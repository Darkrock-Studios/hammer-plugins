// Style report: readability, adverbs, filter words, dialogue, and repetition, per scene and for the
// whole story, shown as a document. Run from a project's menu, through the `action` export.
//
// It reads the project's language, and LANGUAGES has what each needs beyond its word lists, which
// are resources in resources/<language>/: its vowels, adverb suffix, readability formula, and
// dialogue marks. A language without lists gets words, sentences, and dialogue alone. The report is
// written in the UI's language, from messages.h. Letters outside Latin, Greek, and Cyrillic are told
// apart roughly, and only those scripts are lower-cased.
#define HAMMER_IMPLEMENTATION
#define HAMMER_OUT_OF_MEMORY "The story is too long for a style report"
#include "hammer.h"
#include "hammer_action.h"
#include "messages.h"

#define SCENE_REPEATS_SHOWN 5
#define MAX_LIST_SIZE 100
#define MIN_REPEAT_LENGTH 4
#define MIN_ADVERB_LENGTH 4
#define PHRASE_LENGTH 3
#define MAX_TITLE_CLOSERS 8

// The UI language's words, from messages.h.
static const Messages *ui = &MESSAGES[0];

// The report's settings, from settings.toml.
static struct {
  int filter_words;
  int scenes;
  int64_t scene_repeats;
  int64_t story_repeats;
  int64_t list_size;
} settings;

// A number rounded to tenths, with one decimal.
static void put_tenths(HammerBuffer *buffer, int64_t tenths) {
  uint64_t magnitude = tenths < 0 ? (uint64_t)(-tenths) : (uint64_t)tenths;
  if (tenths < 0) hammer_put_char(buffer, '-');
  hammer_put_int(buffer, (int64_t)(magnitude / 10));
  hammer_put_char(buffer, ui->decimal);
  hammer_put_char(buffer, (char)('0' + magnitude % 10));
}

// Writes a message's template with each %s given the next of `args`.
static void say(HammerBuffer *out, const char *template, const char *const *args) {
  for (const char *c = template; *c; c++) {
    if (c[0] == '%' && c[1] == 's') {
      hammer_put_text(out, *args++);
      c++;
    } else {
      hammer_put_char(out, *c);
    }
  }
}

// "3 words": a count and its noun in the form the UI language gives that count.
static const char *counted(int64_t count, const char *const forms[3]) {
  int64_t n = count < 0 ? -count : count;
  int form = 2;
  if (ui->code[0] == 'u' && ui->code[1] == 'k') {
    form = n % 10 == 1 && n % 100 != 11 ? 0 : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? 1 : 2;
  } else if (n == 1 || (n == 0 && ui->code[0] == 'f' && ui->code[1] == 'r')) {
    form = 0;
  }
  HammerBuffer out = {0};
  hammer_put_int(&out, count);
  hammer_put_char(&out, ' ');
  hammer_put_text(&out, forms[form]);
  return hammer_terminated(&out);
}

static const char *tenths_text(int64_t tenths) {
  HammerBuffer out = {0};
  put_tenths(&out, tenths);
  return hammer_terminated(&out);
}

static const char *int_text(int64_t value) {
  HammerBuffer out = {0};
  hammer_put_int(&out, value);
  return hammer_terminated(&out);
}

// ---------------------------------------------------------------------------------------------
// Languages

enum { READ_NONE, READ_FLESCH, READ_KANDEL_MOLES, READ_AMSTAD, READ_SZIGRISZT, READ_GULPEASE };

// What a project language needs beyond its word lists.
typedef struct {
  const char *code;
  // Its vowels, lower case, for counting syllables as vowel groups.
  const char *vowels;
  // Whether a final "e" after a consonant is silent, as in English "made" and French "table".
  int silent_e;
  // The ending that makes an adverb, as "-ly" does, or 0 where adverbs have none.
  const char *adverb_suffix;
  int readability;
  // The readability formula's name, for the whole story's figure.
  const char *formula;
  // Whether an apostrophe ends a word, as French and Italian elide: "l'homme" counts "homme".
  int split_apostrophes;
  // Whether a line starting with a dash is dialogue, each later dash moving between speech and
  // narration: "—Ven —dijo ella—. Ya."
  int dash_dialogue;
} Language;

static const Language LANGUAGES[] = {
  {"en", "aeiouy", 1, "ly", READ_FLESCH, "Flesch", 0, 0},
  {"fr", "aeiouy\xC3\xA0\xC3\xA2\xC3\xA4\xC3\xA9\xC3\xA8\xC3\xAA\xC3\xAB\xC3\xAE\xC3\xAF\xC3\xB4\xC3\xB6\xC3\xB9\xC3\xBB\xC3\xBC\xC3\xBF\xC5\x93\xC3\xA6", 1, "ment",
   READ_KANDEL_MOLES, "Kandel\xE2\x80\x93Moles", 1, 1},
  {"de", "aeiouy\xC3\xA4\xC3\xB6\xC3\xBC", 0, 0, READ_AMSTAD, "Amstad", 0, 0},
  {"es", "aeiou\xC3\xA1\xC3\xA9\xC3\xAD\xC3\xB3\xC3\xBA\xC3\xBC", 0, "mente", READ_SZIGRISZT, "Szigriszt-Pazos", 0, 1},
  {"it", "aeiou\xC3\xA0\xC3\xA8\xC3\xA9\xC3\xAC\xC3\xAD\xC3\xB2\xC3\xB3\xC3\xB9", 0, "mente", READ_GULPEASE, "Gulpease", 1, 1},
  {"uk", "\xD0\xB0\xD0\xB5\xD1\x94\xD0\xB8\xD1\x96\xD1\x97\xD0\xBE\xD1\x83\xD1\x8E\xD1\x8F", 0, 0, READ_NONE, 0, 0, 1},
};

// A language with no entry above: counted as words, sentences, and quoted dialogue.
static const Language OTHER_LANGUAGE = {"", "aeiouy", 0, 0, READ_NONE, 0, 0, 0};

static const Language *language = &LANGUAGES[0];
// The project language's primary subtag, which names its folder of lists.
static char language_code[9];
// Whether that folder had lists, without which there is nothing to compare words with.
static int has_lists;

// ---------------------------------------------------------------------------------------------
// Memory: hammer_alloc only grows, so what lives for one scene is reused rather than reallocated.

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
    Chunk *fresh = hammer_must_alloc(sizeof(Chunk) + capacity);
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
  grown.slots = hammer_must_alloc((uint64_t)grown.capacity * sizeof(Slot));
  memset(grown.slots, 0, (uint64_t)grown.capacity * sizeof(Slot));
  grown.used = hammer_must_alloc((uint64_t)grown.capacity * sizeof(uint32_t));
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

static void encode(HammerBuffer *buffer, uint32_t c) {
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
  hammer_put(buffer, bytes, (uint64_t)length);
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

// Marks that may close a sentence after its stop, German's “ and guillemets either way round among them.
static int is_closer(uint32_t c) {
  return c == '"' || c == 0x201D || c == 0x2019 || c == ')' || c == 0x201C || c == 0xBB || c == 0xAB;
}

static int is_sentence_end(uint32_t c) { return c == '.' || c == '!' || c == '?' || c == 0x2026; }

// ---------------------------------------------------------------------------------------------
// Word lists

// Titles, after which a full stop does not end a sentence; read with the other lists.
static const char *const *titles;
static int lists_found;

// A hash of the lists read, which cached counts are kept under with their scene's text.
static uint64_t lists_hash;

// Reads resources/<language>/<name>.txt for this call, in lower case; a missing or empty file lists
// nothing.
static const char *const *read_list(const char *name) {
  static const char *const none[] = {0};
  char path[64];
  uint64_t code_length = strlen(language_code), name_length = strlen(name), count;
  memcpy(path, language_code, code_length);
  path[code_length] = '/';
  memcpy(path + code_length + 1, name, name_length);
  memcpy(path + code_length + 1 + name_length, ".txt", 5);
  const char **list = hammer_resource_lines(path, &count);
  if (!list) return none;
  lists_found = 1;
  for (uint64_t i = 0; i < count; i++) {
    // The lines are this call's, in hammer_alloc's memory.
    for (char *c = (char *)list[i]; *c; c++) {
      *c = (char)((unsigned char)*c >= 'A' && (unsigned char)*c <= 'Z' ? *c | 0x20 : *c);
      lists_hash = (lists_hash ^ (unsigned char)*c) * 1099511628211ull;
    }
    lists_hash = (lists_hash ^ '\n') * 1099511628211ull;
  }
  return list;
}

static Pool list_keys;
static Map common_words, not_adverbs, filter_words;

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
  int64_t letters;
} Counts;

static Pool scene_keys, total_keys;
static Map scene_adverbs, scene_words, scene_phrases, scene_filters;
static Map total_adverbs, total_words, total_phrases, total_filters;

static HammerBuffer line;          // one line, markdown stripped
static HammerBuffer sentence_text; // the lower-cased words of one sentence, one after another
static HammerBuffer phrase;
static uint64_t *word_starts;
static uint64_t word_capacity;

static int is_vowel(uint32_t c) {
  if (c < 0x80) {
    for (const char *v = language->vowels; *v && (unsigned char)*v < 0x80; v++) {
      if ((uint32_t)*v == c) return 1;
    }
    return 0;
  }
  uint64_t length = strlen(language->vowels);
  for (uint64_t at = 0; at < length;) {
    if (decode(language->vowels, length, &at) == c) return 1;
  }
  return 0;
}

// Vowel groups, less a silent final e; every word with a letter has at least one.
static int syllables(const char *word, uint64_t length, int64_t *letter_count) {
  uint32_t letters[4] = {0};         // the last three letters, newest in [0]
  uint64_t count_of_letters = 0;
  int count = 0, in_vowels = 0, last_vowel = 0, second_last_vowel = 0;
  for (uint64_t at = 0; at < length;) {
    uint32_t c = decode(word, length, &at);
    // Kotlin looks at UTF-16 units, where a letter outside the basic plane is two non-letters.
    if (c >= 0x10000 || !is_letter(c)) continue;
    count_of_letters++;
    letters[2] = letters[1];
    letters[1] = letters[0];
    letters[0] = c;
    int vowel = is_vowel(c);
    second_last_vowel = last_vowel;
    last_vowel = vowel;
    if (vowel && !in_vowels) count++;
    in_vowels = vowel;
  }
  *letter_count += (int64_t)count_of_letters;
  if (!count_of_letters) return 0;
  // English says the e of "able" and "little".
  int sounded = language == &LANGUAGES[0] && letters[1] == 'l';
  if (language->silent_e && count_of_letters > 2 && letters[0] == 'e' && !sounded && !second_last_vowel) count--;
  return count < 1 ? 1 : count;
}

static int ends_with(const char *word, uint64_t length, const char *suffix) {
  uint64_t n = strlen(suffix);
  if (length < n) return 0;
  for (uint64_t i = 0; i < n; i++) {
    if (word[length - n + i] != suffix[i]) return 0;
  }
  return 1;
}

static void count_word(Counts *counts, const char *word, uint64_t length, int in_dialogue) {
  uint64_t units = utf16_length(word, length);
  counts->words++;
  counts->syllables += syllables(word, length, &counts->letters);
  if (in_dialogue) counts->dialogue_words++;
  if (!has_lists) return;
  const char *suffix = language->adverb_suffix;
  if (suffix && units > MIN_ADVERB_LENGTH && ends_with(word, length, suffix) && !contains(&not_adverbs, word, length)) {
    map_add(&scene_adverbs, word, length, 1);
  }
  if (contains(&filter_words, word, length)) map_add(&scene_filters, word, length, 1);
  if (units >= MIN_REPEAT_LENGTH && !contains(&common_words, word, length)) map_add(&scene_words, word, length, 1);
}

static void count_phrases(uint64_t words) {
  if (!has_lists) return;
  for (uint64_t first = 0; first + PHRASE_LENGTH <= words; first++) {
    int uncommon = 0;
    phrase.length = 0;
    for (uint64_t i = first; i < first + PHRASE_LENGTH; i++) {
      const char *word = sentence_text.data + word_starts[i];
      uint64_t word_length = word_starts[i + 1] - word_starts[i];
      if (!contains(&common_words, word, word_length)) uncommon = 1;
      if (i > first) hammer_put_char(&phrase, ' ');
      hammer_put(&phrase, word, word_length);
    }
    if (uncommon) map_add(&scene_phrases, phrase.data, phrase.length, 1);
  }
}

// The run of word characters ending at [end] is a title such as "Mr", so the full stop after it
// does not end the sentence.
static int ends_with_title(const char *text, uint64_t start, uint64_t end) {
  uint64_t at = end;
  while (at > start) {
    uint64_t before = at - 1;
    while (before > start && ((unsigned char)text[before] & 0xC0) == 0x80) before--;
    uint64_t probe = before;
    uint32_t c = decode(text, at, &probe);
    if (!is_word_char(c) && c != '_') break;
    at = before;
  }
  for (int i = 0; titles[i]; i++) {
    uint64_t length = strlen(titles[i]), t = 0, w = at;
    int match = 1;
    while (match && t < length && w < end) match = lowercase(decode(text, end, &w)) == decode(titles[i], length, &t);
    if (match && t == length && w == end) return 1;
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

enum { QUOTE_NONE, QUOTE_DOUBLE, QUOTE_CURLY_DOUBLE, QUOTE_CURLY_SINGLE, QUOTE_LOW, QUOTE_LEFT_ANGLE, QUOTE_RIGHT_ANGLE,
       QUOTE_DASH };

// One line: tokens, quotation marks, and sentence ends.
static void count_line(Counts *counts, const char *text, uint64_t length) {
  int opened = QUOTE_NONE;
  // Whether the line has had anything but spaces, and whether it opened with a dialogue dash.
  int started = 0, dash_line = 0;
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
        uint64_t *starts = hammer_must_alloc(capacity * sizeof(uint64_t));
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
        } else if ((d == '\'' || d == 0x2019) && !language->split_apostrophes && is_word_char(peek_at(text, length, next))) {
          encode(&sentence_text, d);
          at = next;
        } else {
          break;
        }
      }
      count_word(counts, sentence_text.data + start, sentence_text.length - start, opened != QUOTE_NONE);
      words++;
      started = 1;
      continue;
    }

    if ((c == 0x2014 || c == 0x2013) && language->dash_dialogue && (dash_line || !started)) {
      dash_line = 1;
      opened = opened == QUOTE_DASH ? QUOTE_NONE : opened == QUOTE_NONE ? QUOTE_DASH : opened;
    }
    if (!is_space(c)) started = 1;
    // „ opens a German quotation that “ closes; guillemets face either way, « » and » «.
    if (c == 0x201E) opened = opened == QUOTE_NONE ? QUOTE_LOW : opened;
    else if (c == 0x201C && opened == QUOTE_LOW) opened = QUOTE_NONE;
    else if (c == 0xAB) opened = opened == QUOTE_RIGHT_ANGLE ? QUOTE_NONE : opened == QUOTE_NONE ? QUOTE_LEFT_ANGLE : opened;
    else if (c == 0xBB) opened = opened == QUOTE_LEFT_ANGLE ? QUOTE_NONE : opened == QUOTE_NONE ? QUOTE_RIGHT_ANGLE : opened;
    else if (c == 0x201C) opened = QUOTE_CURLY_DOUBLE;
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
            if (text[i] != '*' && text[i] != '_' && text[i] != '~' && text[i] != '`') hammer_put_char(&line, text[i]);
          }
          at = end + 1;
          continue;
        }
      }
    }
    if (c != '*' && c != '_' && c != '~' && c != '`') hammer_put_char(&line, c);
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
// Cached counts: a scene's counts are kept under a hash of its text, its language, and the word
// lists, so unchanged scenes are not counted again. The value is the Counts, then the scene's adverbs,
// words, phrases seen at least twice, and filter words, each as an entry count followed by entries of
// a count, a length, and the text.

#define CACHE_KEY_PREFIX "counts/2/"

static char cache_key[64];
static uint64_t cache_key_length;
static HammerBuffer cached;

static void put_u32(HammerBuffer *buffer, uint32_t value) { hammer_put(buffer, (const char *)&value, 4); }

static void set_cache_key(const HammerBuffer *text) {
  static const char hex[] = "0123456789abcdef";
  uint64_t hash = lists_hash;
  for (uint64_t i = 0; i < text->length; i++) hash = (hash ^ (unsigned char)text->data[i]) * 1099511628211ull;
  HammerBuffer key = {cache_key, 0, sizeof(cache_key)};
  hammer_put_text(&key, CACHE_KEY_PREFIX);
  for (int shift = 60; shift >= 0; shift -= 4) hammer_put_char(&key, hex[(hash >> shift) & 15]);
  hammer_put_char(&key, '/');
  hammer_put_int(&key, (int64_t)text->length);
  cache_key_length = key.length;
}

static void put_map(HammerBuffer *out, const Map *map, int32_t minimum) {
  uint64_t size_at = out->length;
  uint32_t size = 0;
  put_u32(out, 0);
  for (uint32_t i = 0; i < map->size; i++) {
    const Slot *slot = &map->slots[map->used[i]];
    if (slot->count < minimum) continue;
    put_u32(out, (uint32_t)slot->count);
    put_u32(out, slot->length);
    hammer_put(out, slot->key, slot->length);
    size++;
  }
  memcpy(out->data + size_at, &size, 4);
}

static void save_counts(const Counts *counts) {
  cached.length = 0;
  hammer_put(&cached, (const char *)counts, sizeof(Counts));
  put_map(&cached, &scene_adverbs, 1);
  put_map(&cached, &scene_words, 1);
  put_map(&cached, &scene_phrases, 2);
  put_map(&cached, &scene_filters, 1);
  hammer_cache_write(cache_key, cache_key_length, cached.data, cached.length);
}

static int read_u32(const char **at, const char *end, uint32_t *value) {
  if (end - *at < 4) return 0;
  memcpy(value, *at, 4);
  *at += 4;
  return 1;
}

static int read_map(const char **at, const char *end, Map *map) {
  uint32_t size, count, length;
  if (!read_u32(at, end, &size)) return 0;
  for (uint32_t i = 0; i < size; i++) {
    if (!read_u32(at, end, &count) || !read_u32(at, end, &length) || (uint64_t)(end - *at) < length) return 0;
    map_add(map, *at, length, (int32_t)count);
    *at += length;
  }
  return 1;
}

// Fills [counts] and the scene maps from the cache; 0, leaving them empty, on a miss.
static int load_counts(Counts *counts) {
  ExtismHandle value = hammer_cache_read(cache_key, cache_key_length);
  if (!value) return 0;
  uint64_t length = extism_length(value);
  cached.length = 0;
  hammer_reserve(&cached, length);
  extism_load_from_handle(value, 0, cached.data, length);
  extism_free(value);
  cached.length = length;

  const char *at = cached.data, *end = cached.data + length;
  if (length >= sizeof(Counts)) {
    memcpy(counts, at, sizeof(Counts));
    at += sizeof(Counts);
    if (read_map(&at, end, &scene_adverbs) && read_map(&at, end, &scene_words) &&
        read_map(&at, end, &scene_phrases) && read_map(&at, end, &scene_filters) && at == end) {
      return 1;
    }
  }
  *counts = (Counts){0};
  pool_reset(&scene_keys);
  map_clear(&scene_adverbs);
  map_clear(&scene_words);
  map_clear(&scene_phrases);
  map_clear(&scene_filters);
  return 0;
}

// ---------------------------------------------------------------------------------------------
// Figures

// Java's Math.round: the nearest integer, halves up.
static int64_t round_half_up(double value) {
  double floor = __builtin_floor(value);
  return (int64_t)floor + (value - floor >= 0.5 ? 1 : 0);
}

static void put_list(HammerBuffer *out, const Slot **top, int count) {
  for (int i = 0; i < count; i++) {
    if (i) hammer_put_text(out, ", ");
    hammer_put(out, top[i]->key, top[i]->length);
    hammer_put_text(out, " (");
    hammer_put_int(out, top[i]->count);
    hammer_put_char(out, ')');
  }
}

typedef struct {
  int has_ease, has_grade;
  int64_t ease, grade, adverbs_per_thousand, filters_per_thousand; // in tenths
  int64_t dialogue_percent;
} Figures;

static Figures figures_of(const Counts *counts, int32_t adverbs, int32_t filters) {
  Figures figures = {0};
  if (counts->words > 0 && counts->sentences > 0 && has_lists) {
    double words = (double)counts->words, sentences = (double)counts->sentences;
    double words_per_sentence = words / sentences;
    double syllables_per_word = (double)counts->syllables / words;
    double ease = 0;
    figures.has_ease = 1;
    switch (language->readability) {
    case READ_FLESCH:
      ease = 206.835 - 1.015 * words_per_sentence - 84.6 * syllables_per_word;
      figures.has_grade = 1;
      figures.grade = round_half_up((0.39 * words_per_sentence + 11.8 * syllables_per_word - 15.59) * 10);
      break;
    case READ_KANDEL_MOLES: ease = 207 - 1.015 * words_per_sentence - 73.6 * syllables_per_word; break;
    case READ_AMSTAD: ease = 180 - words_per_sentence - 58.5 * syllables_per_word; break;
    case READ_SZIGRISZT: ease = 206.835 - 62.3 * syllables_per_word - words_per_sentence; break;
    case READ_GULPEASE: ease = 89 + (300 * sentences - 10 * (double)counts->letters) / words; break;
    default: figures.has_ease = 0; break;
    }
    figures.ease = round_half_up(ease * 10);
  }
  if (counts->words > 0) {
    figures.adverbs_per_thousand = round_half_up((double)adverbs * 1000.0 / (double)counts->words * 10);
    figures.filters_per_thousand = round_half_up((double)filters * 1000.0 / (double)counts->words * 10);
    figures.dialogue_percent = round_half_up((double)counts->dialogue_words / (double)counts->words * 100);
  }
  return figures;
}

static const char *percent_text(int64_t percent) {
  HammerBuffer out = {0};
  const char *args[] = {int_text(percent)};
  say(&out, ui->percent, args);
  return hammer_terminated(&out);
}

// One line: "9 words in 2 sentences. Reading ease 89.5, grade 1.9. ..."
static void put_scene_summary(HammerBuffer *out, const Counts *counts, int32_t adverbs, int32_t filters) {
  Figures figures = figures_of(counts, adverbs, filters);
  const char *words[] = {counted(counts->words, ui->words), counted(counts->sentences, ui->sentences_in)};
  say(out, ui->scene_words, words);
  if (figures.has_ease) {
    const char *ease[] = {tenths_text(figures.ease), tenths_text(figures.grade)};
    say(out, figures.has_grade ? ui->scene_ease_grade : ui->scene_ease, ease);
  }
  if (has_lists && language->adverb_suffix) {
    const char *adverb_figure[] = {tenths_text(figures.adverbs_per_thousand)};
    say(out, ui->scene_adverbs, adverb_figure);
  }
  if (has_lists && settings.filter_words) {
    const char *filter_figure[] = {tenths_text(figures.filters_per_thousand)};
    say(out, ui->scene_filters, filter_figure);
  }
  const char *dialogue[] = {percent_text(figures.dialogue_percent)};
  say(out, ui->scene_dialogue, dialogue);
}

// [text] as it should read, with the characters that would format it escaped.
static void put_markdown_text(HammerBuffer *out, const char *text, uint64_t length) {
  for (uint64_t i = 0; i < length; i++) {
    char c = text[i];
    if (c == '\\' || c == '*' || c == '_' || c == '`' || c == '[' || c == ']' || c == '<' || c == '>') hammer_put_char(out, '\\');
    hammer_put_char(out, c);
  }
}

// The whole story's figures, a markdown list item each.
static void put_story_summary(HammerBuffer *out, const Counts *counts, int32_t adverbs, int32_t filters) {
  Figures figures = figures_of(counts, adverbs, filters);
  const char *words[] = {int_text(counts->words), counted(counts->sentences, ui->sentences_in)};
  say(out, ui->story_words, words);
  hammer_put_char(out, '\n');
  if (figures.has_ease) {
    const char *ease[] = {tenths_text(figures.ease), figures.has_grade ? tenths_text(figures.grade) : language->formula};
    say(out, figures.has_grade ? ui->story_ease_grade : ui->story_ease, ease);
    hammer_put_char(out, '\n');
  }
  if (has_lists && language->adverb_suffix) {
    const char *adverb_figure[] = {tenths_text(figures.adverbs_per_thousand)};
    say(out, ui->story_adverbs, adverb_figure);
    hammer_put_char(out, '\n');
  }
  if (has_lists && settings.filter_words) {
    const char *filter_figure[] = {tenths_text(figures.filters_per_thousand)};
    say(out, ui->story_filters, filter_figure);
    hammer_put_char(out, '\n');
  }
  const char *dialogue[] = {percent_text(figures.dialogue_percent)};
  say(out, ui->story_dialogue, dialogue);
  hammer_put_char(out, '\n');
}

// ---------------------------------------------------------------------------------------------
// Talking to Hammer

static const char *failure;

// Runs an operation and loads its reply; null, with [failure] set, when it failed.
static char *call(const char *operation, const char *input, uint64_t *length, HammerJson *output) {
  ExtismHandle handle = hammer_call(operation, input);
  uint64_t size = extism_length(handle);
  char *reply = hammer_must_alloc(size);
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
          char *message = hammer_must_alloc(raw_length + 1);
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
static void put_raw_string(HammerBuffer *out, const char *raw, uint64_t length) {
  hammer_put_char(out, '"');
  hammer_put(out, raw, length);
  hammer_put_char(out, '"');
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
    Scene *grown = hammer_must_alloc(capacity * sizeof(Scene));
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
static int read_markdown(HammerJson *json, HammerBuffer *text) {
  const char *key, *raw;
  uint64_t key_length, raw_length;
  if (!hammer_json_enter_object(json)) return 0;
  while (hammer_json_next_key(json, &key, &key_length)) {
    if (hammer_json_key_is(key, key_length, "markdown") && hammer_json_string(json, &raw, &raw_length)) {
      text->length = 0;
      hammer_reserve(text, raw_length);
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

static void trim_end(HammerBuffer *report) {
  while (report->length && is_space((unsigned char)report->data[report->length - 1])) report->length--;
}

static Map empty_map(Pool *keys) { return (Map){0, 0, 0, 0, 1, keys}; }

// Drops the scenes whose ids are not among the count in chosen, keeping story order.
static void keep_chosen(const int64_t *chosen, uint64_t count) {
  uint64_t kept = 0;
  for (uint64_t i = 0; i < scene_count; i++) {
    for (uint64_t c = 0; c < count; c++) {
      if (chosen[c] == scenes[i].id) {
        scenes[kept++] = scenes[i];
        break;
      }
    }
  }
  scene_count = kept;
}

// Everything that pointed into memory from an earlier call, which hammer_alloc hands out again.
static void reset(void) {
  hammer_alloc_reset();
  list_keys = scene_keys = total_keys = (Pool){0};
  common_words = not_adverbs = filter_words = empty_map(&list_keys);
  scene_adverbs = scene_words = scene_phrases = scene_filters = empty_map(&scene_keys);
  total_adverbs = total_words = total_phrases = total_filters = empty_map(&total_keys);
  line = sentence_text = phrase = cached = (HammerBuffer){0};
  word_starts = 0;
  word_capacity = 0;
  scenes = 0;
  scene_count = scene_capacity = 0;
  failure = 0;
}

// The primary subtag of a BCP 47 tag, as JSON sent it, in lower case; English's when it is missing
// or not a plain one.
static void set_language(const char *tag, uint64_t length) {
  uint64_t n = 0;
  while (tag && n < length && tag[n] != '-' && tag[n] != '_' && n + 1 < sizeof(language_code)) {
    char c = hammer_to_lower(tag[n]);
    if (c < 'a' || c > 'z') break;
    language_code[n++] = c;
  }
  if (n < 2 || (tag && n < length && tag[n] != '-' && tag[n] != '_')) {
    memcpy(language_code, "en", 3);
  } else {
    language_code[n] = 0;
  }
  language = &OTHER_LANGUAGE;
  for (uint64_t i = 0; i < sizeof(LANGUAGES) / sizeof(LANGUAGES[0]); i++) {
    if (hammer_json_key_is(language_code, strlen(language_code), LANGUAGES[i].code)) language = &LANGUAGES[i];
  }
}

// The UI's words for a BCP 47 tag, English's for any language messages.h lacks.
static void set_ui(HammerText locale) {
  ui = &MESSAGES[0];
  for (uint64_t i = 0; i < sizeof(MESSAGES) / sizeof(MESSAGES[0]) && locale.data; i++) {
    const char *code = MESSAGES[i].code;
    if (locale.length >= 2 && hammer_to_lower(locale.data[0]) == code[0] && hammer_to_lower(locale.data[1]) == code[1] &&
        (locale.length == 2 || locale.data[2] == '-' || locale.data[2] == '_')) {
      ui = &MESSAGES[i];
    }
  }
}

static int64_t setting_int(const HammerActionRequest *request, const char *key, int64_t fallback, int64_t min, int64_t max) {
  HammerJson json;
  if (!hammer_json_find(request->settings.data, request->settings.length, key, &json)) return fallback;
  char c = hammer_json_peek(&json);
  if (c != '-' && !hammer_is_digit(c)) return fallback;
  int64_t value = hammer_json_int(&json);
  return value < min ? min : value > max ? max : value;
}

static int setting_bool(const HammerActionRequest *request, const char *key, int fallback) {
  HammerJson json;
  if (!hammer_json_find(request->settings.data, request->settings.length, key, &json)) return fallback;
  char c = hammer_json_peek(&json);
  return c == 't' || c == 'f' ? hammer_json_bool(&json) : fallback;
}

// The project's language, from project.info; English when Hammer does not say, or the plugin may not ask.
static void read_language(const char *project, uint64_t project_length) {
  HammerBuffer request = {0};
  hammer_put_text(&request, "{\"project\":");
  put_raw_string(&request, project, project_length);
  hammer_put_char(&request, '}');
  hammer_put_char(&request, 0);
  uint64_t length;
  HammerJson reply;
  const char *key, *tag = 0;
  uint64_t key_length, tag_length = 0;
  if (call("project.info", request.data, &length, &reply) && hammer_json_enter_object(&reply)) {
    while (hammer_json_next_key(&reply, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "language") && hammer_json_peek(&reply) == '"') {
        hammer_json_string(&reply, &tag, &tag_length);
      } else {
        hammer_json_skip(&reply);
      }
    }
  }
  failure = 0;
  set_language(tag, tag_length);
}

// The project language's lists, which cached counts are kept under with the language.
static void load_lists(void) {
  lists_hash = 14695981039346656037ull;
  for (const char *c = language_code; *c; c++) lists_hash = (lists_hash ^ (unsigned char)*c) * 1099511628211ull;
  lists_hash = (lists_hash ^ '/') * 1099511628211ull;
  lists_found = 0;
  load_list(&common_words, read_list("common-words"));
  load_list(&not_adverbs, read_list("not-adverbs"));
  load_list(&filter_words, read_list("filter-words"));
  titles = read_list("titles");
  has_lists = lists_found;
}

static void put_top(HammerBuffer *report, const char *label, const Map *map, int32_t minimum) {
  const Slot *top[MAX_LIST_SIZE];
  int found = map_top(map, minimum, (int)settings.list_size, top);
  if (!found) return;
  hammer_put_text(report, label);
  put_list(report, top, found);
  hammer_put_char(report, '\n');
}

int32_t EXTISM_EXPORTED_FUNCTION(action) {
  reset();

  uint64_t size = extism_input_length();
  char *input = hammer_must_alloc(size);
  extism_load_input(0, input, size);

  HammerActionRequest action;
  if (!hammer_action_read(input, size, &action)) {
    extism_error_set_buf_from_sz("No project to report on");
    return 1;
  }
  const char *project = action.project.data;
  uint64_t project_length = action.project.length;
  set_ui(action.locale);
  settings.filter_words = setting_bool(&action, "filterWords", 1);
  settings.scenes = setting_bool(&action, "scenes", 1);
  settings.scene_repeats = setting_int(&action, "sceneRepeats", 3, 2, 1000);
  settings.story_repeats = setting_int(&action, "storyRepeats", 5, 2, 100000);
  settings.list_size = setting_int(&action, "listSize", 20, 1, MAX_LIST_SIZE);
  read_language(project, project_length);
  load_lists();

  // The ids of the scenes chosen, as int64s; none means the whole story.
  HammerBuffer chosen = {0};
  HammerJson json;
  if (hammer_input(&action, "scenes", &json) && hammer_json_enter_array(&json)) {
    while (hammer_json_next_item(&json)) {
      int64_t id = hammer_json_int(&json);
      hammer_put(&chosen, (const char *)&id, sizeof(id));
    }
  }

  HammerBuffer request = {0};
  hammer_put_text(&request, "{\"project\":");
  put_raw_string(&request, project, project_length);
  hammer_put_char(&request, '}');
  hammer_put_char(&request, 0);
  const char *key;
  uint64_t key_length, reply_length;
  HammerJson reply;
  if (!call("scene.tree", request.data, &reply_length, &reply)) goto failed;
  if (hammer_json_enter_object(&reply)) {
    while (hammer_json_next_key(&reply, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "nodes")) read_nodes(&reply);
      else hammer_json_skip(&reply);
    }
  }

  if (chosen.length) keep_chosen((const int64_t *)chosen.data, chosen.length / sizeof(int64_t));

  Counts total = {0};
  HammerBuffer markdown = {0};
  HammerBuffer *lines = hammer_must_alloc((scene_count ? scene_count : 1) * sizeof(HammerBuffer));
  HammerBuffer status = {0};
  for (uint64_t i = 0; i < scene_count; i++) {
    status.length = 0;
    const char *place[] = {int_text((int64_t)i + 1), int_text((int64_t)scene_count)};
    say(&status, ui->progress, place);
    hammer_progress((double)i / (double)scene_count, hammer_terminated(&status));

    request.length = 0;
    hammer_put_text(&request, "{\"project\":");
    put_raw_string(&request, project, project_length);
    hammer_put_text(&request, ",\"id\":");
    hammer_put_int(&request, scenes[i].id);
    hammer_put_char(&request, '}');
    hammer_put_char(&request, 0);
    if (!call("scene.read", request.data, &reply_length, &reply) || !read_markdown(&reply, &markdown)) goto failed;

    pool_reset(&scene_keys);
    map_clear(&scene_adverbs);
    map_clear(&scene_words);
    map_clear(&scene_phrases);
    map_clear(&scene_filters);
    Counts counts = {0};
    set_cache_key(&markdown);
    if (!load_counts(&counts)) {
      count_scene(&counts, markdown.data, markdown.length);
      save_counts(&counts);
    }
    total.words += counts.words;
    total.sentences += counts.sentences;
    total.syllables += counts.syllables;
    total.dialogue_words += counts.dialogue_words;
    total.letters += counts.letters;
    merge(&total_adverbs, &scene_adverbs, 1);
    merge(&total_words, &scene_words, 1);
    merge(&total_phrases, &scene_phrases, 2);
    merge(&total_filters, &scene_filters, 1);

    HammerBuffer *text = &lines[i];
    *text = (HammerBuffer){0};
    if (!settings.scenes) continue;
    char *name = hammer_must_alloc(scenes[i].name_length);
    hammer_put_text(text, "### ");
    put_markdown_text(text, name, hammer_json_unescape(scenes[i].name, scenes[i].name_length, name));
    hammer_put_char(text, '\n');
    put_scene_summary(text, &counts, map_total(&scene_adverbs), map_total(&scene_filters));
    const Slot *top[SCENE_REPEATS_SHOWN];
    int repeats = map_top(&scene_words, (int32_t)settings.scene_repeats, SCENE_REPEATS_SHOWN, top);
    if (repeats) {
      hammer_put_text(text, "\n\n");
      hammer_put_text(text, ui->repeats);
      put_list(text, top, repeats);
    }
  }

  HammerBuffer report = {0};
  hammer_put_text(&report, ui->title);
  hammer_put_text(&report, "\n\n");
  hammer_put_text(&report, chosen.length ? ui->chosen_scenes : ui->whole_story);
  hammer_put_text(&report, "\n\n");
  put_story_summary(&report, &total, map_total(&total_adverbs), map_total(&total_filters));
  if (has_lists) {
    put_top(&report, ui->top_adverbs, &total_adverbs, 1);
    if (settings.filter_words) put_top(&report, ui->top_filters, &total_filters, 1);
    put_top(&report, ui->repeated_words, &total_words, (int32_t)settings.story_repeats);
    put_top(&report, ui->repeated_phrases, &total_phrases, 2);
  } else {
    hammer_put_text(&report, "\n");
    hammer_put_text(&report, ui->no_lists);
    hammer_put_char(&report, '\n');
  }
  if (scene_count && settings.scenes) {
    hammer_put_text(&report, "\n");
    hammer_put_text(&report, ui->scenes);
    hammer_put_text(&report, "\n\n");
  }

  for (uint64_t i = 0; i < scene_count && settings.scenes; i++) {
    hammer_put(&report, lines[i].data, lines[i].length);
    hammer_put_text(&report, "\n\n");
  }
  trim_end(&report);

  extism_output_buf(report.data, report.length);
  return 0;

failed:
  extism_error_set_buf_from_sz(failure ? failure : "Hammer sent a reply the style report could not read");
  return 1;
}
