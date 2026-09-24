// Style report: readability, adverbs, dialogue, and repetition, per scene and for the whole story,
// saved as a note. Run from a project's menu, through the `action` export.
//
// English only: the syllable, adverb, and common-word rules assume it. Letters outside Latin,
// Greek, and Cyrillic are told apart roughly, and only those scripts are lower-cased.
#define HAMMER_IMPLEMENTATION
#include "../include/hammer.h"
#include "../include/hammer_json.h"

#define MAX_NOTE_LENGTH 10000 // Hammer's note size limit, in UTF-16 code units
#define SCENE_REPEAT_THRESHOLD 3
#define SCENE_REPEATS_SHOWN 5
#define TOTAL_REPEAT_THRESHOLD 5
#define TOTAL_LIST_SIZE 20
#define MIN_REPEAT_LENGTH 4
#define MIN_ADVERB_LENGTH 4
#define PHRASE_LENGTH 3
#define MAX_TITLE_CLOSERS 8

// ---------------------------------------------------------------------------------------------
// Memory: hammer_alloc only grows, so what lives for one scene is reused rather than reallocated.

static void *must_alloc(uint64_t size);

typedef struct {
  char *data;
  uint64_t length;
  uint64_t capacity;
} Buffer;

static void buffer_reserve(Buffer *buffer, uint64_t extra) {
  if (buffer->length + extra <= buffer->capacity) return;
  uint64_t capacity = buffer->capacity ? buffer->capacity * 2 : 4096;
  while (capacity < buffer->length + extra) capacity *= 2;
  char *data = must_alloc(capacity);
  if (buffer->length) memcpy(data, buffer->data, buffer->length);
  buffer->data = data;
  buffer->capacity = capacity;
}

static void put(Buffer *buffer, const char *text, uint64_t length) {
  buffer_reserve(buffer, length);
  memcpy(buffer->data + buffer->length, text, length);
  buffer->length += length;
}

static void put_text(Buffer *buffer, const char *text) { put(buffer, text, strlen(text)); }

static void put_char(Buffer *buffer, char c) { put(buffer, &c, 1); }

static void put_number(Buffer *buffer, int64_t n) {
  char digits[20];
  int length = 0;
  uint64_t magnitude = n < 0 ? (uint64_t)(-n) : (uint64_t)n;
  do {
    digits[length++] = (char)('0' + magnitude % 10);
    magnitude /= 10;
  } while (magnitude);
  if (n < 0) put_char(buffer, '-');
  while (length) put(buffer, &digits[--length], 1);
}

// A number rounded to tenths, written as Kotlin writes a Double: at least one decimal.
static void put_tenths(Buffer *buffer, int64_t tenths) {
  uint64_t magnitude = tenths < 0 ? (uint64_t)(-tenths) : (uint64_t)tenths;
  if (tenths < 0) put_char(buffer, '-');
  put_number(buffer, (int64_t)(magnitude / 10));
  put_char(buffer, '.');
  put_char(buffer, (char)('0' + magnitude % 10));
}

// Key text that must outlive the buffer it came from: chunks, so earlier keys never move.
typedef struct Chunk {
  struct Chunk *next;
  uint64_t used;
  uint64_t capacity;
  char data[];
} Chunk;

typedef struct {
  Chunk *first;
  Chunk *current;
} Pool;

static const char *pool_add(Pool *pool, const char *text, uint64_t length) {
  Chunk *chunk = pool->current;
  while (!chunk || chunk->used + length > chunk->capacity) {
    if (chunk && chunk->next && chunk->next->capacity >= length) {
      chunk = chunk->next;
      chunk->used = 0;
      continue;
    }
    uint64_t capacity = length > 65536 ? length : 65536;
    Chunk *fresh = must_alloc(sizeof(Chunk) + capacity);
    fresh->next = 0;
    fresh->used = 0;
    fresh->capacity = capacity;
    if (chunk) chunk->next = fresh;
    else pool->first = fresh;
    chunk = fresh;
  }
  pool->current = chunk;
  char *at = chunk->data + chunk->used;
  memcpy(at, text, length);
  chunk->used += length;
  return at;
}

// Forgets every key, keeping the chunks to fill again.
static void pool_reset(Pool *pool) {
  pool->current = pool->first;
  if (pool->first) pool->first->used = 0;
}

// ---------------------------------------------------------------------------------------------
// Counting maps: text to count, open addressing. Each keeps the slots it uses, so going over or
// clearing it costs what is in it rather than its capacity.

typedef struct {
  const char *key;
  uint32_t length;
  uint32_t hash;
  uint32_t generation;
  int32_t count;
} Slot;

typedef struct {
  Slot *slots;
  uint32_t *used; // indexes of the slots in use, [size] of them
  uint32_t capacity; // a power of two
  uint32_t size;
  uint32_t generation; // slots of another generation are empty
  Pool *keys;
} Map;

static uint32_t hash_of(const char *text, uint64_t length) {
  uint32_t hash = 2166136261u;
  for (uint64_t i = 0; i < length; i++) hash = (hash ^ (unsigned char)text[i]) * 16777619u;
  return hash;
}

static int same(const char *a, uint64_t a_length, const char *b, uint64_t b_length) {
  if (a_length != b_length) return 0;
  for (uint64_t i = 0; i < a_length; i++) {
    if (a[i] != b[i]) return 0;
  }
  return 1;
}

static uint32_t find_slot(const Map *map, const char *key, uint64_t length, uint32_t hash) {
  uint32_t at = hash & (map->capacity - 1);
  while (1) {
    const Slot *slot = &map->slots[at];
    if (slot->generation != map->generation) return at;
    if (slot->hash == hash && same(slot->key, slot->length, key, length)) return at;
    at = (at + 1) & (map->capacity - 1);
  }
}

static void map_grow(Map *map) {
  Map grown = *map;
  grown.capacity = map->capacity ? map->capacity * 2 : 1024;
  grown.slots = must_alloc((uint64_t)grown.capacity * sizeof(Slot));
  memset(grown.slots, 0, (uint64_t)grown.capacity * sizeof(Slot));
  grown.used = must_alloc((uint64_t)grown.capacity * sizeof(uint32_t));
  grown.generation = 1;
  for (uint32_t i = 0; i < map->size; i++) {
    Slot *old = &map->slots[map->used[i]];
    uint32_t at = find_slot(&grown, old->key, old->length, old->hash);
    grown.slots[at] = *old;
    grown.slots[at].generation = 1;
    grown.used[i] = at;
  }
  *map = grown;
}

static void map_add(Map *map, const char *key, uint64_t length, int32_t count) {
  if ((map->size + 1) * 4 > map->capacity * 3) map_grow(map);
  uint32_t hash = hash_of(key, length);
  uint32_t at = find_slot(map, key, length, hash);
  Slot *slot = &map->slots[at];
  if (slot->generation == map->generation) {
    slot->count += count;
    return;
  }
  *slot = (Slot){pool_add(map->keys, key, length), (uint32_t)length, hash, map->generation, count};
  map->used[map->size++] = at;
}

static void map_clear(Map *map) {
  map->size = 0;
  map->generation++;
}

static int contains(const Map *map, const char *key, uint64_t length) {
  if (!map->capacity) return 0;
  return map->slots[find_slot(map, key, length, hash_of(key, length))].generation == map->generation;
}

static int32_t map_total(const Map *map) {
  int32_t total = 0;
  for (uint32_t i = 0; i < map->size; i++) total += map->slots[map->used[i]].count;
  return total;
}

// UTF-8 bytes compare in code point order, which is how Kotlin orders these keys.
static int key_before(const Slot *a, const Slot *b) {
  uint32_t length = a->length < b->length ? a->length : b->length;
  for (uint32_t i = 0; i < length; i++) {
    if (a->key[i] != b->key[i]) return (unsigned char)a->key[i] < (unsigned char)b->key[i];
  }
  return a->length < b->length;
}

// The [size] highest counts of at least [minimum], highest first, ties in key order.
static int map_top(const Map *map, int32_t minimum, int size, const Slot **top) {
  int found = 0;
  for (uint32_t i = 0; i < map->size; i++) {
    const Slot *slot = &map->slots[map->used[i]];
    if (slot->count < minimum) continue;
    int at = found;
    while (at > 0 && (top[at - 1]->count < slot->count || (top[at - 1]->count == slot->count && key_before(slot, top[at - 1])))) {
      at--;
    }
    if (at >= size) continue;
    int last = found < size ? found : size - 1;
    for (int j = last; j > at; j--) top[j] = top[j - 1];
    top[at] = slot;
    if (found < size) found++;
  }
  return found;
}

// ---------------------------------------------------------------------------------------------
// Text: UTF-8, read a code point at a time.

static uint32_t decode(const char *text, uint64_t end, uint64_t *at) {
  const unsigned char *s = (const unsigned char *)text;
  uint64_t i = *at;
  uint32_t c = s[i];
  int extra = c >= 0xF0 ? 3 : c >= 0xE0 ? 2 : c >= 0xC0 ? 1 : 0;
  if (extra) c &= 0x3F >> extra;
  i++;
  while (extra-- && i < end && (s[i] & 0xC0) == 0x80) c = (c << 6) | (s[i++] & 0x3F);
  *at = i;
  return c;
}

static uint32_t peek_at(const char *text, uint64_t end, uint64_t at) {
  return at < end ? decode(text, end, &at) : 0;
}

static void encode(Buffer *buffer, uint32_t c) {
  char bytes[4];
  int length;
  if (c < 0x80) {
    bytes[0] = (char)c;
    length = 1;
  } else if (c < 0x800) {
    bytes[0] = (char)(0xC0 | (c >> 6));
    bytes[1] = (char)(0x80 | (c & 0x3F));
    length = 2;
  } else if (c < 0x10000) {
    bytes[0] = (char)(0xE0 | (c >> 12));
    bytes[1] = (char)(0x80 | ((c >> 6) & 0x3F));
    bytes[2] = (char)(0x80 | (c & 0x3F));
    length = 3;
  } else {
    bytes[0] = (char)(0xF0 | (c >> 18));
    bytes[1] = (char)(0x80 | ((c >> 12) & 0x3F));
    bytes[2] = (char)(0x80 | ((c >> 6) & 0x3F));
    bytes[3] = (char)(0x80 | (c & 0x3F));
    length = 4;
  }
  put(buffer, bytes, (uint64_t)length);
}

// How long Kotlin measures the text: UTF-16 code units.
static uint64_t utf16_length(const char *text, uint64_t length) {
  uint64_t units = 0;
  for (uint64_t at = 0; at < length;) units += decode(text, length, &at) >= 0x10000 ? 2 : 1;
  return units;
}

static int is_letter(uint32_t c) {
  if (c < 0x80) return (c | 0x20) >= 'a' && (c | 0x20) <= 'z';
  if (c == 0xAA || c == 0xB5 || c == 0xBA) return 1;
  if (c >= 0xC0 && c <= 0x2C1) return c != 0xD7 && c != 0xF7;
  if (c >= 0x386 && c <= 0x3FF) return c != 0x387 && c != 0x3F6;
  if (c >= 0x400 && c <= 0x52F) return c < 0x482 || c > 0x489;
  if (c >= 0x5D0 && c <= 0x5EA) return 1;
  if (c >= 0x620 && c <= 0x64A) return 1;
  if (c >= 0x1E00 && c <= 0x1FFF) return 1;
  if (c >= 0x3041 && c <= 0x30FF) return c != 0x30A0 && c != 0x30FB;
  if (c >= 0x3400 && c <= 0x9FFF) return 1;
  if (c >= 0xAC00 && c <= 0xD7A3) return 1;
  return (c >= 0x1D400 && c <= 0x1D7CB) || (c >= 0x20000 && c <= 0x3134A);
}

static int is_number(uint32_t c) {
  return (c >= '0' && c <= '9') || c == 0xB2 || c == 0xB3 || c == 0xB9 || (c >= 0xBC && c <= 0xBE) ||
    (c >= 0x660 && c <= 0x669) || (c >= 0x2150 && c <= 0x2189) || (c >= 0x1D7CE && c <= 0x1D7FF);
}

static int is_word_char(uint32_t c) { return is_letter(c) || is_number(c); }

// Java's \s: ASCII whitespace only.
static int is_space(uint32_t c) { return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r'; }

static uint32_t lowercase(uint32_t c) {
  if (c < 0x80) return c >= 'A' && c <= 'Z' ? c + 32 : c;
  if (c >= 0xC0 && c <= 0xDE && c != 0xD7) return c + 32;
  if (c == 0x178) return 0xFF;
  if ((c >= 0x100 && c <= 0x12F) || (c >= 0x132 && c <= 0x137) || (c >= 0x14A && c <= 0x177)) return c | 1;
  if ((c >= 0x139 && c <= 0x148) || (c >= 0x179 && c <= 0x17E)) return c & 1 ? c + 1 : c;
  if (c >= 0x391 && c <= 0x3AB && c != 0x3A2) return c + 32;
  if (c >= 0x410 && c <= 0x42F) return c + 32;
  if (c >= 0x400 && c <= 0x40F) return c + 80;
  return c;
}

static int is_closer(uint32_t c) { return c == '"' || c == 0x201D || c == 0x2019 || c == ')'; }

static int is_sentence_end(uint32_t c) { return c == '.' || c == '!' || c == '?' || c == 0x2026; }

// ---------------------------------------------------------------------------------------------
// Word lists

static const char *const NOT_ADVERBS[] = {
  "family", "only", "early", "reply", "apply", "supply", "rely", "ally", "belly", "bully", "jelly",
  "holy", "ugly", "july", "italy", "lily", "rally", "fly", "sly", "assembly", "anomaly", "butterfly",
  "costly", "curly", "daily", "deadly", "elderly", "friendly", "ghostly", "hilly", "holly", "homely",
  "jolly", "likely", "lively", "lonely", "lovely", "manly", "monthly", "oily", "orderly", "silly",
  "smelly", "surly", "timely", "unlikely", "weekly", "wily", "woolly", "worldly", "yearly", "chilly",
  "comely", "cowardly", "unruly", "burly", "dilly", "gully", "melancholy", "monopoly", "multiply",
  "poly", "sully", "tally", "wobbly", 0,
};

static const char *const COMMON_WORDS[] = {
  "a", "about", "after", "again", "all", "also", "am", "an", "and", "any", "are", "as", "at", "back",
  "be", "because", "been", "before", "being", "but", "by", "can", "could", "did", "do", "does", "down",
  "even", "every", "for", "from", "get", "got", "had", "has", "have", "he", "her", "here", "hers",
  "him", "his", "how", "i", "if", "in", "into", "is", "it", "its", "just", "know", "like", "me", "more",
  "much", "my", "no", "not", "now", "of", "off", "on", "one", "only", "or", "our", "out", "over",
  "said", "she", "so", "some", "than", "that", "the", "their", "them", "then", "there", "these",
  "they", "this", "those", "through", "to", "too", "up", "very", "was", "we", "were", "what", "when",
  "where", "which", "while", "who", "why", "will", "with", "would", "you", "your", "i'm", "it's",
  "don't", "didn't", "that's", "he's", "she's", "i'd", "i'll", "you're", "they're", "we're", "can't",
  "won't", "wasn't", "there's", "let", "going", "would've", "could've", "though", "still",
  "around", "away", "other", "another", "each", "went", "come", "came", "make", "made", "want",
  "look", "looked", "think", "thought", "well", "yes", "yeah", "okay", "good", "right", "never",
  "always", "something", "nothing", "anything", "someone", "anyone", "time", "once", 0,
};

static const char *const TITLES[] = {"mr", "mrs", "ms", "dr", "st", "prof", "jr", "sr", "mt", "vs", "etc", 0};

static Pool list_keys;
static Map common_words, not_adverbs;

static void load_list(Map *map, const char *const *words) {
  for (int i = 0; words[i]; i++) map_add(map, words[i], strlen(words[i]), 1);
}

// ---------------------------------------------------------------------------------------------
// Counting one scene

typedef struct {
  int64_t words;
  int64_t sentences;
  int64_t syllables;
  int64_t dialogue_words;
} Counts;

static Pool scene_keys, total_keys;
static Map scene_adverbs, scene_words, scene_phrases;
static Map total_adverbs, total_words, total_phrases;

static Buffer line;          // one line, markdown stripped
static Buffer sentence_text; // the lower-cased words of one sentence, one after another
static Buffer phrase;
static uint64_t *word_starts;
static uint64_t word_capacity;

// Vowel groups, less a silent final e; every word with a letter has at least one.
static int syllables(const char *word, uint64_t length) {
  uint32_t letters[4] = {0};         // the last three letters, newest in [0]
  uint64_t letter_count = 0, letter_units = 0;
  int count = 0, in_vowels = 0;
  for (uint64_t at = 0; at < length;) {
    uint32_t c = decode(word, length, &at);
    // Kotlin looks at UTF-16 units, where a letter outside the basic plane is two non-letters.
    if (c >= 0x10000 || !is_letter(c)) continue;
    letter_count++;
    letter_units++;
    letters[2] = letters[1];
    letters[1] = letters[0];
    letters[0] = c;
    int vowel = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y';
    if (vowel && !in_vowels) count++;
    in_vowels = vowel;
  }
  if (!letter_count) return 0;
  int second_last_vowel = letters[1] == 'a' || letters[1] == 'e' || letters[1] == 'i' || letters[1] == 'o' ||
    letters[1] == 'u' || letters[1] == 'y';
  if (letter_units > 2 && letters[0] == 'e' && letters[1] != 'l' && !second_last_vowel) count--;
  return count < 1 ? 1 : count;
}

static int ends_with_ly(const char *word, uint64_t length) {
  return length >= 2 && word[length - 2] == 'l' && word[length - 1] == 'y';
}

static void count_word(Counts *counts, const char *word, uint64_t length, int in_dialogue) {
  uint64_t units = utf16_length(word, length);
  counts->words++;
  counts->syllables += syllables(word, length);
  if (in_dialogue) counts->dialogue_words++;
  if (units > MIN_ADVERB_LENGTH && ends_with_ly(word, length) && !contains(&not_adverbs, word, length)) {
    map_add(&scene_adverbs, word, length, 1);
  }
  if (units >= MIN_REPEAT_LENGTH && !contains(&common_words, word, length)) map_add(&scene_words, word, length, 1);
}

static void count_phrases(uint64_t words) {
  for (uint64_t first = 0; first + PHRASE_LENGTH <= words; first++) {
    int uncommon = 0;
    phrase.length = 0;
    for (uint64_t i = first; i < first + PHRASE_LENGTH; i++) {
      const char *word = sentence_text.data + word_starts[i];
      uint64_t word_length = word_starts[i + 1] - word_starts[i];
      if (!contains(&common_words, word, word_length)) uncommon = 1;
      if (i > first) put_char(&phrase, ' ');
      put(&phrase, word, word_length);
    }
    if (uncommon) map_add(&scene_phrases, phrase.data, phrase.length, 1);
  }
}

// The run of word characters ending at [end] is a title such as "Mr", so the full stop after it
// does not end the sentence.
static int ends_with_title(const char *text, uint64_t start, uint64_t end) {
  uint64_t at = end;
  while (at > start) {
    unsigned char c = (unsigned char)text[at - 1];
    if (!((c | 0x20) >= 'a' && (c | 0x20) <= 'z') && !(c >= '0' && c <= '9') && c != '_') break;
    at--;
  }
  for (int i = 0; TITLES[i]; i++) {
    uint64_t length = strlen(TITLES[i]);
    if (end - at != length) continue;
    int match = 1;
    for (uint64_t j = 0; j < length && match; j++) match = ((unsigned char)text[at + j] | 0x20) == TITLES[i][j];
    if (match) return 1;
  }
  return 0;
}

// Whether the whitespace at [space] ends a sentence: it follows a sentence end and up to eight
// closing marks, and the sentence does not end in a title.
static int ends_sentence(const char *text, uint64_t sentence_start, uint64_t space) {
  uint64_t at = space;
  int closers = 0;
  while (at > sentence_start) {
    uint64_t before = at - 1;
    while (before > sentence_start && ((unsigned char)text[before] & 0xC0) == 0x80) before--;
    uint64_t probe = before;
    uint32_t c = decode(text, at, &probe);
    if (is_sentence_end(c)) {
      return !(c == '.' && ends_with_title(text, sentence_start, before));
    }
    if (!is_closer(c) || ++closers > MAX_TITLE_CLOSERS) return 0;
    at = before;
  }
  return 0;
}

enum { QUOTE_NONE, QUOTE_DOUBLE, QUOTE_CURLY_DOUBLE, QUOTE_CURLY_SINGLE };

// One line: tokens, quotation marks, and sentence ends.
static void count_line(Counts *counts, const char *text, uint64_t length) {
  int opened = QUOTE_NONE;
  uint64_t sentence_start = 0;
  uint64_t words = 0;
  sentence_text.length = 0;

  uint64_t at = 0;
  while (1) {
    uint64_t here = at;
    uint32_t c = at < length ? decode(text, length, &at) : 0;

    if (here >= length || (is_space(c) && ends_sentence(text, sentence_start, here))) {
      if (words) counts->sentences++;
      if (words >= PHRASE_LENGTH) {
        word_starts[words] = sentence_text.length;
        count_phrases(words);
      }
      words = 0;
      sentence_text.length = 0;
      if (here >= length) return;
      while (at < length && is_space(peek_at(text, length, at))) at++;
      sentence_start = at;
      continue;
    }

    if (is_word_char(c)) {
      if (words + 2 > word_capacity) {
        uint64_t capacity = word_capacity ? word_capacity * 2 : 256;
        uint64_t *starts = must_alloc(capacity * sizeof(uint64_t));
        if (words) memcpy(starts, word_starts, words * sizeof(uint64_t));
        word_starts = starts;
        word_capacity = capacity;
      }
      uint64_t start = sentence_text.length;
      word_starts[words] = start;
      encode(&sentence_text, lowercase(c));
      while (at < length) {
        uint64_t next = at;
        uint32_t d = decode(text, length, &next);
        if (is_word_char(d)) {
          encode(&sentence_text, lowercase(d));
          at = next;
        } else if ((d == '\'' || d == 0x2019) && is_word_char(peek_at(text, length, next))) {
          encode(&sentence_text, d);
          at = next;
        } else {
          break;
        }
      }
      count_word(counts, sentence_text.data + start, sentence_text.length - start, opened != QUOTE_NONE);
      words++;
      continue;
    }

    if (c == 0x201C) opened = QUOTE_CURLY_DOUBLE;
    else if (c == 0x2018) opened = QUOTE_CURLY_SINGLE;
    else if (c == 0x201D) opened = opened == QUOTE_CURLY_DOUBLE ? QUOTE_NONE : opened;
    // A lone ’ is also a plural possessive, so it closes only a ‘ quotation.
    else if (c == 0x2019 && !is_letter(peek_at(text, length, at))) opened = opened == QUOTE_CURLY_SINGLE ? QUOTE_NONE : opened;
    else if (c == '"') opened = opened == QUOTE_DOUBLE ? QUOTE_NONE : opened == QUOTE_NONE ? QUOTE_DOUBLE : opened;
  }
}

static int is_heading(const char *text, uint64_t length) {
  uint64_t at = 0;
  while (at < length && is_space((unsigned char)text[at])) at++;
  uint64_t hashes = 0;
  while (at < length && text[at] == '#') {
    hashes++;
    at++;
  }
  return hashes >= 1 && hashes <= 6 && at < length && is_space((unsigned char)text[at]);
}

// The prose of a line: without a leading list or quote marker, with links as their text, and
// without emphasis marks.
static void strip_markdown(const char *text, uint64_t length) {
  line.length = 0;
  uint64_t at = 0;
  while (at < length && is_space((unsigned char)text[at])) at++;
  if (at < length) {
    char c = text[at];
    uint64_t after = at + 1;
    if ((c == '-' || c == '*' || c == '+') && after < length && is_space((unsigned char)text[after])) {
      while (after < length && is_space((unsigned char)text[after])) after++;
      at = after;
    } else if (c == '>') {
      while (after < length && is_space((unsigned char)text[after])) after++;
      at = after;
    } else if (c >= '0' && c <= '9') {
      while (after < length && text[after] >= '0' && text[after] <= '9') after++;
      if (after + 1 < length && text[after] == '.' && is_space((unsigned char)text[after + 1])) {
        after++;
        while (after < length && is_space((unsigned char)text[after])) after++;
        at = after;
      } else {
        at = 0;
      }
    } else {
      at = 0;
    }
  } else {
    at = 0;
  }

  while (at < length) {
    char c = text[at];
    if (c == '[') {
      uint64_t close = at + 1;
      while (close < length && text[close] != ']') close++;
      if (close + 1 < length && text[close + 1] == '(') {
        uint64_t end = close + 2;
        while (end < length && text[end] != ')') end++;
        if (end < length) {
          for (uint64_t i = at + 1; i < close; i++) {
            if (text[i] != '*' && text[i] != '_' && text[i] != '~' && text[i] != '`') put_char(&line, text[i]);
          }
          at = end + 1;
          continue;
        }
      }
    }
    if (c != '*' && c != '_' && c != '~' && c != '`') put_char(&line, c);
    at++;
  }
}

static void count_scene(Counts *counts, const char *markdown, uint64_t length) {
  uint64_t start = 0;
  while (start <= length) {
    uint64_t end = start;
    while (end < length && markdown[end] != '\n' && markdown[end] != '\r') end++;
    if (!is_heading(markdown + start, end - start)) {
      strip_markdown(markdown + start, end - start);
      count_line(counts, line.data, line.length);
    }
    if (end >= length) break;
    start = end + (markdown[end] == '\r' && end + 1 < length && markdown[end + 1] == '\n' ? 2 : 1);
  }
}

// ---------------------------------------------------------------------------------------------
// Figures

// Java's Math.round: the nearest integer, halves up.
static int64_t round_half_up(double value) {
  double floor = __builtin_floor(value);
  return (int64_t)floor + (value - floor >= 0.5 ? 1 : 0);
}

static void put_list(Buffer *out, const Slot **top, int count) {
  for (int i = 0; i < count; i++) {
    if (i) put_text(out, ", ");
    put(out, top[i]->key, top[i]->length);
    put_text(out, " (");
    put_number(out, top[i]->count);
    put_char(out, ')');
  }
}

static void put_summary(Buffer *out, const Counts *counts, int32_t adverbs) {
  put_number(out, counts->words);
  put_text(out, " words in ");
  put_number(out, counts->sentences);
  put_text(out, " sentences.");
  if (counts->words > 0 && counts->sentences > 0) {
    double words_per_sentence = (double)counts->words / (double)counts->sentences;
    double syllables_per_word = (double)counts->syllables / (double)counts->words;
    put_text(out, " Reading ease ");
    put_tenths(out, round_half_up((206.835 - 1.015 * words_per_sentence - 84.6 * syllables_per_word) * 10));
    put_text(out, ", grade ");
    put_tenths(out, round_half_up((0.39 * words_per_sentence + 11.8 * syllables_per_word - 15.59) * 10));
    put_char(out, '.');
  }
  put_char(out, ' ');
  put_tenths(out, counts->words > 0 ? round_half_up((double)adverbs * 1000.0 / (double)counts->words * 10) : 0);
  put_text(out, " adverbs per 1,000 words. ");
  put_number(out, counts->words > 0 ? round_half_up((double)counts->dialogue_words / (double)counts->words * 100) : 0);
  put_text(out, "% dialogue.");
}

// ---------------------------------------------------------------------------------------------
// Talking to Hammer

static const char *failure;

static void *must_alloc(uint64_t size) {
  void *memory = hammer_alloc(size);
  if (!memory) {
    extism_error_set_buf_from_sz("The story is too long for a style report");
    __builtin_trap();
  }
  return memory;
}

// Runs an operation and loads its reply; null, with [failure] set, when it failed.
static char *call(const char *operation, const char *input, uint64_t *length, HammerJson *output) {
  ExtismHandle handle = hammer_call(operation, input);
  uint64_t size = extism_length(handle);
  char *reply = must_alloc(size);
  extism_load_from_handle(handle, 0, reply, size);
  extism_free(handle);
  *length = size;

  hammer_json_init(output, reply, size);
  const char *key, *raw;
  uint64_t key_length, raw_length;
  if (!hammer_json_enter_object(output)) return 0;
  while (hammer_json_next_key(output, &key, &key_length)) {
    if (hammer_json_key_is(key, key_length, "output")) return reply;
    if (hammer_json_key_is(key, key_length, "error") && hammer_json_enter_object(output)) {
      while (hammer_json_next_key(output, &key, &key_length)) {
        if (hammer_json_key_is(key, key_length, "message") && hammer_json_string(output, &raw, &raw_length)) {
          char *message = must_alloc(raw_length + 1);
          message[hammer_json_unescape(raw, raw_length, message)] = 0;
          failure = message;
        } else {
          hammer_json_skip(output);
        }
      }
      return 0;
    }
    hammer_json_skip(output);
  }
  return 0;
}

// Appends [raw], already escaped, as a JSON string.
static void put_raw_string(Buffer *out, const char *raw, uint64_t length) {
  put_char(out, '"');
  put(out, raw, length);
  put_char(out, '"');
}

static void put_json_string(Buffer *out, const char *text, uint64_t length) {
  static const char hex[] = "0123456789abcdef";
  put_char(out, '"');
  for (uint64_t i = 0; i < length; i++) {
    unsigned char c = (unsigned char)text[i];
    if (c == '"' || c == '\\') {
      put_char(out, '\\');
      put_char(out, (char)c);
    } else if (c == '\n') {
      put_text(out, "\\n");
    } else if (c < 0x20) {
      put_text(out, "\\u00");
      put_char(out, hex[c >> 4]);
      put_char(out, hex[c & 15]);
    } else {
      put_char(out, (char)c);
    }
  }
  put_char(out, '"');
}

typedef struct {
  const char *name; // raw JSON string contents
  uint64_t name_length;
  int64_t id;
} Scene;

static Scene *scenes;
static uint64_t scene_count, scene_capacity;

static void add_scene(const char *name, uint64_t name_length, int64_t id) {
  if (scene_count == scene_capacity) {
    uint64_t capacity = scene_capacity ? scene_capacity * 2 : 64;
    Scene *grown = must_alloc(capacity * sizeof(Scene));
    if (scene_count) memcpy(grown, scenes, scene_count * sizeof(Scene));
    scenes = grown;
    scene_capacity = capacity;
  }
  scenes[scene_count++] = (Scene){name, name_length, id};
}

// The story's scenes in order, groups opened up.
static void read_nodes(HammerJson *json) {
  if (!hammer_json_enter_array(json)) return;
  while (hammer_json_next_item(json)) {
    const char *key, *name = 0;
    uint64_t key_length, name_length = 0, children_at = 0;
    int64_t id = 0;
    int is_scene = 0;
    if (!hammer_json_enter_object(json)) return;
    while (hammer_json_next_key(json, &key, &key_length)) {
      const char *raw;
      uint64_t raw_length;
      if (hammer_json_key_is(key, key_length, "id")) {
        id = hammer_json_int(json);
      } else if (hammer_json_key_is(key, key_length, "name")) {
        hammer_json_string(json, &name, &name_length);
      } else if (hammer_json_key_is(key, key_length, "kind") && hammer_json_string(json, &raw, &raw_length)) {
        is_scene = hammer_json_key_is(raw, raw_length, "scene");
      } else if (hammer_json_key_is(key, key_length, "children")) {
        hammer_json_peek(json);
        children_at = json->at;
        hammer_json_skip(json);
      } else {
        hammer_json_skip(json);
      }
    }
    if (is_scene) {
      add_scene(name, name_length, id);
    } else if (children_at) {
      uint64_t resume = json->at;
      json->at = children_at;
      read_nodes(json);
      json->at = resume;
    }
  }
}

// The markdown from scene.read's reply, decoded into [text].
static int read_markdown(HammerJson *json, Buffer *text) {
  const char *key, *raw;
  uint64_t key_length, raw_length;
  if (!hammer_json_enter_object(json)) return 0;
  while (hammer_json_next_key(json, &key, &key_length)) {
    if (hammer_json_key_is(key, key_length, "markdown") && hammer_json_string(json, &raw, &raw_length)) {
      text->length = 0;
      buffer_reserve(text, raw_length);
      text->length = hammer_json_unescape(raw, raw_length, text->data);
      return 1;
    }
    hammer_json_skip(json);
  }
  return 0;
}

static void merge(Map *into, const Map *from, int32_t minimum) {
  for (uint32_t i = 0; i < from->size; i++) {
    const Slot *slot = &from->slots[from->used[i]];
    if (slot->count >= minimum) map_add(into, slot->key, slot->length, slot->count);
  }
}

// Appends [text] to [note] with a line break, or, when that would not leave room for the notice of
// how many scenes are left out, appends the notice instead and returns 0.
static Buffer omission;

static int append_scene_line(Buffer *note, uint64_t *note_units, const Buffer *text, uint64_t remaining) {
  omission.length = 0;
  put_text(&omission, "\xE2\x80\xA6" "and ");
  put_number(&omission, (int64_t)remaining);
  put_text(&omission, remaining == 1 ? " more scene." : " more scenes.");
  uint64_t text_units = utf16_length(text->data, text->length);
  uint64_t omission_units = utf16_length(omission.data, omission.length);
  if (*note_units + text_units + 1 + omission_units > MAX_NOTE_LENGTH) {
    put(note, omission.data, omission.length);
    *note_units += omission_units;
    return 0;
  }
  put(note, text->data, text->length);
  put_char(note, '\n');
  *note_units += text_units + 1;
  return 1;
}

// Cuts [note] to at most [units] UTF-16 code units, at a character boundary.
static void cut(Buffer *note, uint64_t units) {
  uint64_t at = 0, counted = 0;
  while (at < note->length) {
    uint64_t next = at;
    uint32_t c = decode(note->data, note->length, &next);
    uint64_t size = c >= 0x10000 ? 2 : 1;
    if (counted + size > units) break;
    counted += size;
    at = next;
  }
  note->length = at;
}

static void trim_end(Buffer *note) {
  while (note->length && is_space((unsigned char)note->data[note->length - 1])) note->length--;
}

static Map empty_map(Pool *keys) { return (Map){0, 0, 0, 0, 1, keys}; }

// Everything that pointed into memory from an earlier call, which hammer_alloc hands out again.
static void reset(void) {
  hammer_alloc_reset();
  list_keys = scene_keys = total_keys = (Pool){0};
  common_words = not_adverbs = empty_map(&list_keys);
  scene_adverbs = scene_words = scene_phrases = empty_map(&scene_keys);
  total_adverbs = total_words = total_phrases = empty_map(&total_keys);
  line = sentence_text = phrase = omission = (Buffer){0};
  word_starts = 0;
  word_capacity = 0;
  scenes = 0;
  scene_count = scene_capacity = 0;
  failure = 0;
  load_list(&common_words, COMMON_WORDS);
  load_list(&not_adverbs, NOT_ADVERBS);
}

int32_t EXTISM_EXPORTED_FUNCTION(action) {
  reset();

  uint64_t size = extism_input_length();
  char *input = must_alloc(size);
  extism_load_input(0, input, size);

  HammerJson json;
  hammer_json_init(&json, input, size);
  const char *key, *project = 0;
  uint64_t key_length, project_length = 0;
  if (hammer_json_enter_object(&json)) {
    while (hammer_json_next_key(&json, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "project")) hammer_json_string(&json, &project, &project_length);
      else hammer_json_skip(&json);
    }
  }
  if (!project) {
    extism_error_set_buf_from_sz("No project to report on");
    return 1;
  }

  Buffer request = {0};
  put_text(&request, "{\"project\":");
  put_raw_string(&request, project, project_length);
  put_char(&request, '}');
  put_char(&request, 0);
  uint64_t reply_length;
  HammerJson reply;
  if (!call("scene.tree", request.data, &reply_length, &reply)) goto failed;
  if (hammer_json_enter_object(&reply)) {
    while (hammer_json_next_key(&reply, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "nodes")) read_nodes(&reply);
      else hammer_json_skip(&reply);
    }
  }

  Counts total = {0};
  Buffer markdown = {0};
  Buffer *lines = must_alloc((scene_count ? scene_count : 1) * sizeof(Buffer));
  for (uint64_t i = 0; i < scene_count; i++) {
    request.length = 0;
    put_text(&request, "{\"project\":");
    put_raw_string(&request, project, project_length);
    put_text(&request, ",\"id\":");
    put_number(&request, scenes[i].id);
    put_char(&request, '}');
    put_char(&request, 0);
    if (!call("scene.read", request.data, &reply_length, &reply) || !read_markdown(&reply, &markdown)) goto failed;

    pool_reset(&scene_keys);
    map_clear(&scene_adverbs);
    map_clear(&scene_words);
    map_clear(&scene_phrases);
    Counts counts = {0};
    count_scene(&counts, markdown.data, markdown.length);
    total.words += counts.words;
    total.sentences += counts.sentences;
    total.syllables += counts.syllables;
    total.dialogue_words += counts.dialogue_words;
    merge(&total_adverbs, &scene_adverbs, 1);
    merge(&total_words, &scene_words, 1);
    merge(&total_phrases, &scene_phrases, 2);

    Buffer *text = &lines[i];
    *text = (Buffer){0};
    char *name = must_alloc(scenes[i].name_length);
    put(text, name, hammer_json_unescape(scenes[i].name, scenes[i].name_length, name));
    put_text(text, ": ");
    put_summary(text, &counts, map_total(&scene_adverbs));
    const Slot *top[SCENE_REPEATS_SHOWN];
    int repeats = map_top(&scene_words, SCENE_REPEAT_THRESHOLD, SCENE_REPEATS_SHOWN, top);
    if (repeats) {
      put_text(text, " Repeats: ");
      put_list(text, top, repeats);
      put_char(text, '.');
    }
  }

  Buffer note = {0};
  put_text(&note, "Style report\n\nWhole story: ");
  put_summary(&note, &total, map_total(&total_adverbs));
  put_char(&note, '\n');
  const Slot *top[TOTAL_LIST_SIZE];
  int found = map_top(&total_adverbs, 1, TOTAL_LIST_SIZE, top);
  if (found) {
    put_text(&note, "Adverbs: ");
    put_list(&note, top, found);
    put_char(&note, '\n');
  }
  found = map_top(&total_words, TOTAL_REPEAT_THRESHOLD, TOTAL_LIST_SIZE, top);
  if (found) {
    put_text(&note, "Repeated words: ");
    put_list(&note, top, found);
    put_char(&note, '\n');
  }
  found = map_top(&total_phrases, 2, TOTAL_LIST_SIZE, top);
  if (found) {
    put_text(&note, "Repeated phrases: ");
    put_list(&note, top, found);
    put_char(&note, '\n');
  }
  if (scene_count) put_text(&note, "\nScenes\n");

  uint64_t note_units = utf16_length(note.data, note.length);
  if (note_units > MAX_NOTE_LENGTH) {
    // Only the lists can make the head this long; cut rather than have note.create refuse it.
    cut(&note, MAX_NOTE_LENGTH);
  } else {
    for (uint64_t i = 0; i < scene_count; i++) {
      if (!append_scene_line(&note, &note_units, &lines[i], scene_count - i)) break;
    }
  }
  trim_end(&note);

  request.length = 0;
  put_text(&request, "{\"project\":");
  put_raw_string(&request, project, project_length);
  put_text(&request, ",\"content\":");
  put_json_string(&request, note.data, note.length);
  put_text(&request, ",\"tags\":[\"style-report\"]}");
  put_char(&request, 0);
  if (!call("note.create", request.data, &reply_length, &reply)) goto failed;

  extism_output_buf_from_sz("Style report saved to Notes");
  return 0;

failed:
  extism_error_set_buf_from_sz(failure ? failure : "Hammer sent a reply the style report could not read");
  return 1;
}
