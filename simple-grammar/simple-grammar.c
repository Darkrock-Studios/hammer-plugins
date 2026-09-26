// Simple grammar: common slips in prose, underlined in the editor through the `diagnose` export.
// Every rule is a pass over one paragraph's words or bytes, with small word lists, so checking is
// fast and the module stays a few KB. The rules stay where they are sure, since every word involved
// is a real one: a spell checker passes these slips and a writer should not be nagged. Settings turn
// groups of rules on and off.
//
// The lists are resources, in resources/<language>/ for each language it checks, and a project in a
// language without them gets no diagnostics. A, an, a lone "i", and "then" for "than" are English's
// own rules; French's spacing and elision are its own. TYPOGRAPHY has each language's quotation marks.
#define HAMMER_IMPLEMENTATION
#define HAMMER_OUT_OF_MEMORY "The text is too long to check"
#include "../include/hammer.h"
#include "../include/hammer_diagnose.h"

#define OPEN_QUOTE "\xE2\x80\x9C"
#define CLOSE_QUOTE "\xE2\x80\x9D"

static struct {
  int mistakes;
  int punctuation;
  int dialogue;
  int wordiness;
  int redundancy;
  int repeated_marks;
  int long_sentences;
  int64_t long_sentence_words;
} settings;

// ---------------------------------------------------------------------------------------------
// Languages

// What a language's rules need beyond its lists: its quotation marks besides straight ones, a 0
// ending each list short of three.
typedef struct {
  const char *code;
  uint32_t open[3];
  uint32_t close[3];
  // "a" and "an", "then" for "than", a lone "i", and a capital after "?" and "!", which other
  // languages may go on from in lower case: « Quoi ? dit-il. »
  int english;
  // A space before ":" and ";", and none after a word.
  int french;
} Typography;

static const Typography TYPOGRAPHY[] = {
  {"en", {0x201C}, {0x201D}, 1, 0},
  {"fr", {0x201C, 0xAB}, {0x201D, 0xBB}, 0, 1},
  {"es", {0x201C, 0xAB}, {0x201D, 0xBB}, 0, 0},
  {"it", {0x201C, 0xAB}, {0x201D, 0xBB}, 0, 0},
  // Germany's » opens and Switzerland's closes, so only „ and “ are told apart.
  {"de", {0x201E}, {0x201C}, 0, 0},
  {"uk", {0xAB, 0x201E}, {0xBB, 0x201C}, 0, 0},
};

// A language with lists but no entry above.
static const Typography OTHER_TYPOGRAPHY = {"", {0x201C}, {0x201D}, 0, 0};

static const Typography *typography = &TYPOGRAPHY[0];

// ---------------------------------------------------------------------------------------------
// Word lists

// The lists of the language last checked, kept between calls; its files together must fit.
#define LIST_BYTES (64 * 1024)
#define LIST_ENTRIES 2048

static char list_bytes[LIST_BYTES];
static const char *list_entries[LIST_ENTRIES];
static uint64_t bytes_used, entries_used;

// Each ends with a 0. The phrase lists alternate a phrase and its fix.
static struct {
  char language[9];
  // Whether `language` has been read, and whether its lists were read whole.
  int loaded;
  int found;
  const char *const *mistakes, *const *wordy, *const *redundant, *const *possessives, *const *comparatives,
      *const *after_then, *const *abbreviations, *const *tag_verbs, *const *pronouns, *const *repeatable,
      *const *elision, *const *no_elision;
} lists;

static void add_entry(const char *entry) {
  if (entries_used == LIST_ENTRIES) hammer_fail("simple-grammar's word lists have too many lines");
  list_entries[entries_used++] = entry;
}

// Languages found to have no lists, so checking one does not drop the lists kept.
static char unlisted[8][sizeof(lists.language)];
static int unlisted_count;

enum {
  MISTAKES, WORDY, REDUNDANT, POSSESSIVES, COMPARATIVES, AFTER_THEN, ABBREVIATIONS, TAG_VERBS, PRONOUNS, REPEATABLE,
  ELISION, NO_ELISION, LIST_COUNT
};

static const char *const LIST_NAMES[LIST_COUNT] = {
  "mistakes", "wordy", "redundant", "possessives", "comparatives", "after-then", "abbreviations", "tag-verbs", "pronouns",
  "repeatable", "elision", "no-elision",
};

// Copies a list's lines, from hammer_resource_lines, into the kept lists. With `pairs`, each line is
// `phrase = fix`, listed as the phrase and then the fix, which may be several joined by " | ".
static const char *const *keep_list(const char *const *lines, int pairs) {
  const char *const *list = &list_entries[entries_used];
  for (; lines && *lines; lines++) {
    uint64_t length = strlen(*lines) + 1;
    if (length > LIST_BYTES - bytes_used) hammer_fail("simple-grammar's word lists are too long");
    char *line = list_bytes + bytes_used;
    memcpy(line, *lines, length);
    bytes_used += length;
    add_entry(line);
    if (pairs) {
      // The first word matches in either case, and is kept in lower case for match's first look.
      char *equals = line;
      for (; *equals && *equals != ' ' && *equals != '-'; equals++) *equals = hammer_to_lower(*equals);
      while (*equals && !(equals[0] == ' ' && equals[1] == '=' && equals[2] == ' ')) equals++;
      if (!*equals) hammer_fail("A line of simple-grammar's phrase lists has no \" = \"");
      *equals = 0;
      add_entry(equals + 3);
    }
  }
  add_entry(0);
  return list;
}

// Reads the lists for the request's language, English when it has none, unless they are the ones
// kept; 0 when the package has none for it. A language whose lists could not be read gets nothing.
static int load_lists(const HammerDiagnoseRequest *request) {
  char code[sizeof(lists.language)] = "en";
  if (request->language.data) {
    uint64_t n = 0;
    while (n < request->language.length && request->language.data[n] != '-' && request->language.data[n] != '_') n++;
    if (n == 0 || n >= sizeof(code)) return 0;
    for (uint64_t i = 0; i < n; i++) {
      char c = hammer_to_lower(request->language.data[i]);
      if (c < 'a' || c > 'z') return 0;
      code[i] = c;
    }
    code[n] = 0;
  }
  uint64_t code_length = strlen(code);
  if (lists.loaded && hammer_json_key_is(code, code_length, lists.language)) return lists.found;
  for (int i = 0; i < unlisted_count; i++) {
    if (hammer_json_key_is(code, code_length, unlisted[i])) return 0;
  }

  const char **files[LIST_COUNT];
  int any = 0;
  for (int i = 0; i < LIST_COUNT; i++) {
    char path[64];
    uint64_t name_length = strlen(LIST_NAMES[i]);
    memcpy(path, code, code_length);
    path[code_length] = '/';
    memcpy(path + code_length + 1, LIST_NAMES[i], name_length);
    memcpy(path + code_length + 1 + name_length, ".txt", 5);
    uint64_t count;
    files[i] = hammer_resource_lines(path, &count);
    any |= files[i] != 0;
  }
  if (!any) {
    if (unlisted_count < (int)(sizeof(unlisted) / sizeof(unlisted[0]))) memcpy(unlisted[unlisted_count++], code, sizeof(code));
    return 0;
  }

  // Marked loaded and empty first, so lists that fail to read are not read again on every call.
  memcpy(lists.language, code, sizeof(code));
  lists.loaded = 1;
  lists.found = 0;
  bytes_used = entries_used = 0;
  lists.mistakes = keep_list(files[MISTAKES], 1);
  lists.wordy = keep_list(files[WORDY], 1);
  lists.redundant = keep_list(files[REDUNDANT], 1);
  lists.possessives = keep_list(files[POSSESSIVES], 0);
  lists.comparatives = keep_list(files[COMPARATIVES], 0);
  lists.after_then = keep_list(files[AFTER_THEN], 0);
  lists.abbreviations = keep_list(files[ABBREVIATIONS], 0);
  lists.tag_verbs = keep_list(files[TAG_VERBS], 0);
  lists.pronouns = keep_list(files[PRONOUNS], 0);
  lists.repeatable = keep_list(files[REPEATABLE], 0);
  lists.elision = keep_list(files[ELISION], 0);
  lists.no_elision = keep_list(files[NO_ELISION], 0);
  lists.found = 1;
  return 1;
}

// ---------------------------------------------------------------------------------------------
// The paragraph

static HammerText text;
static HammerWord *words;
static uint64_t count;
// The apostrophe fixes use: Ukrainian's U+02BC where the paragraph has one, a curly one where it has
// curly quotes or apostrophes, and otherwise straight.
static const char *apostrophe;

static unsigned char byte(uint64_t i) { return hammer_byte(text, i); }

// A fix's first `n` bytes: the first letter a capital when `capital`, apostrophes the paragraph's.
static const char *styled(const char *fix, uint64_t n, int capital, uint64_t *length) {
  HammerBuffer out = {0};
  for (uint64_t i = 0; i < n;) {
    uint64_t bytes;
    uint32_t code = hammer_decode(fix + i, n - i, &bytes);
    char encoded[4];
    if (code == '\'') hammer_put_text(&out, apostrophe);
    else hammer_put(&out, encoded, hammer_encode(i == 0 && capital ? hammer_upper(code) : code, encoded));
    i += bytes;
  }
  *length = out.length;
  return hammer_terminated(&out);
}

static int sentence_end(char c) { return c == '.' || c == '!' || c == '?'; }

static int space_like(char c) { return c == ' ' || c == '\t'; }

// Bytes in the mark at i when it is one of `marks`, none of which is ASCII, or 0.
static uint64_t mark_at(uint64_t i, const uint32_t *marks) {
  if (byte(i) < 0x80) return 0;
  uint64_t length;
  uint32_t code = hammer_char_at(text, i, &length);
  for (int k = 0; k < 3 && marks[k]; k++) {
    if (marks[k] == code) return length;
  }
  return 0;
}

// Bytes in the closing quote at i: straight, single, or the language's.
static uint64_t closing_quote_at(uint64_t i) {
  if (byte(i) == '"' || byte(i) == '\'') return 1;
  if (hammer_char_is(text, i, 0x2019)) return 3;
  return mark_at(i, typography->close);
}

// Whether the text before the full stop at `end` is an abbreviation, an initial, or an initialism
// ("T.V."), after which a lower-case word is fine.
static int abbreviated(uint64_t end) {
  uint64_t start = end, letters = 0;
  int dotted = 0;
  while (start > 0) {
    uint64_t before = hammer_char_before(text, start);
    if (text.data[before] == '.') dotted = 1;
    else if (hammer_letter_at(text, before)) letters++;
    else break;
    start = before;
  }
  if (letters <= 1 || dotted) return 1;
  return hammer_word_in(text, (HammerWord){start, end}, lists.abbreviations);
}

static const char *message(const char *before, const char *quoted, uint64_t length, const char *after) {
  HammerBuffer out = {0};
  hammer_put_text(&out, before);
  hammer_put(&out, quoted, length);
  hammer_put_text(&out, after);
  return hammer_terminated(&out);
}

// ---------------------------------------------------------------------------------------------
// Word mistakes

static void repeated_words(void) {
  for (uint64_t i = 0; i + 1 < count; i++) {
    HammerWord a = words[i], b = words[i + 1];
    // "had had" and "that that" are good English.
    if (!hammer_spaced(text, a, b) || !hammer_same_word(text, a, b) || hammer_word_in(text, a, lists.repeatable)) {
      continue;
    }
    hammer_report_fix(a.start, b.end, "Repeated word", text.data + a.start, hammer_word_length(a));
  }
}

// Only where the sound is plain from the spelling: words starting with "h" or "u", acronyms, and
// single letters are skipped either way.
static int vowel_sound(HammerWord word) {
  char c = hammer_to_lower(text.data[word.start]);
  if (c == 'a' || c == 'i') return 1;
  if (c == 'e') return !hammer_word_starts_with(text, word, "eu") && !hammer_word_starts_with(text, word, "ewe");
  if (c == 'o') return !hammer_word_starts_with(text, word, "one") && !hammer_word_starts_with(text, word, "once");
  return 0;
}

static int consonant_sound(HammerWord word) {
  char c = hammer_to_lower(text.data[word.start]);
  return hammer_is_ascii_letter(c) && c != 'a' && c != 'e' && c != 'i' && c != 'o' && c != 'u' && c != 'h';
}

static void articles(void) {
  for (uint64_t i = 0; i + 1 < count; i++) {
    HammerWord article = words[i], next = words[i + 1];
    if (!hammer_spaced(text, article, next) || hammer_word_length(next) < 2 || hammer_all_upper(text, next) ||
        !hammer_is_ascii_letter(text.data[next.start])) {
      continue;
    }
    int capital = hammer_is_upper(text.data[article.start]);
    if (hammer_word_is(text, article, "a") && vowel_sound(next)) {
      hammer_report_fix(article.start, article.end, "Use " OPEN_QUOTE "an" CLOSE_QUOTE " before a vowel sound",
                        capital ? "An" : "an", 2);
    } else if (hammer_word_is(text, article, "an") && consonant_sound(next)) {
      hammer_report_fix(article.start, article.end, "Use " OPEN_QUOTE "a" CLOSE_QUOTE " before a consonant sound",
                        capital ? "A" : "a", 1);
    }
  }
}

// The words a matched phrase covers: a "*" must be a possessive, and the last word must not run on
// into a compound or abbreviation ("your A-list", "your a.m."). Returns how many, or 0.
static uint64_t match(const char *phrase, uint64_t i) {
  // Most words start no phrase, and the first letter says so without the full match.
  if (hammer_is_ascii_letter(phrase[0]) && hammer_to_lower(text.data[words[i].start]) != phrase[0]) return 0;
  uint64_t matched = hammer_match_phrase(text, words, count, i, phrase);
  if (!matched) return 0;
  uint64_t end = words[i + matched - 1].end;
  if ((byte(end) == '-' || byte(end) == '.') && hammer_letter_at(text, end + 1)) return 0;
  uint64_t index = 0;
  for (const char *c = phrase; *c; c++) {
    if (*c == '*' && !hammer_word_in(text, words[i + index], lists.possessives)) return 0;
    if (*c == ' ' || *c == '-') index++;
  }
  return matched;
}

// A phrase's fix, with its "*" given the possessive the text has there.
static const char *fix_for(const char *phrase, const char *fix, uint64_t i) {
  const char *star = fix;
  while (*star && *star != '*') star++;
  if (!*star) return fix;
  uint64_t index = 0;
  for (const char *c = phrase; *c && *c != '*'; c++) index += *c == ' ' || *c == '-';
  HammerWord possessive = words[i + index];
  HammerBuffer out = {0};
  hammer_put(&out, fix, (uint64_t)(star - fix));
  for (uint64_t at = possessive.start; at < possessive.end; at++) hammer_put_char(&out, hammer_to_lower(text.data[at]));
  hammer_put_text(&out, star + 1);
  return hammer_terminated(&out);
}

// `table` alternates a phrase and its fixes.
static void phrases(const char *const *table, const char *before, const char *after) {
  for (uint64_t i = 0; i < count; i++) {
    for (const char *const *entry = table; entry[0]; entry += 2) {
      uint64_t matched = match(entry[0], i);
      if (!matched) continue;
      int capital = hammer_upper_at(text, words[i].start);
      HammerFix fixes[4];
      uint32_t n = 0;
      for (const char *at = entry[1]; n < 4;) {
        const char *end = at;
        while (*end && !(end[0] == ' ' && end[1] == '|' && end[2] == ' ')) end++;
        char *alternative = hammer_must_alloc((uint64_t)(end - at) + 1);
        memcpy(alternative, at, (uint64_t)(end - at));
        alternative[end - at] = 0;
        const char *fix = fix_for(entry[0], alternative, i);
        fixes[n].replacement = styled(fix, strlen(fix), capital, &fixes[n].length);
        fixes[n++].label = 0;
        if (!*end) break;
        at = end + 3;
      }
      hammer_report(words[i].start, words[i + matched - 1].end, message(before, fixes[0].replacement, fixes[0].length, after),
                    fixes, n);
      i += matched - 1;
      break;
    }
  }
}

// Whether a word starts with a vowel, as French elides before: "y" alone, but not "yeux" or an "h",
// which may be aspirated.
static int starts_with_vowel(HammerWord word) {
  uint64_t length;
  uint32_t c = hammer_lower(hammer_char_at(text, word.start, &length));
  if (c == 'y') return hammer_word_length(word) == 1;
  switch (c) {
  case 'a': case 'e': case 'i': case 'o': case 'u':
  case 0xE0: case 0xE2: case 0xE4: case 0xE6: case 0xE8: case 0xE9: case 0xEA: case 0xEB:
  case 0xEE: case 0xEF: case 0xF4: case 0xF6: case 0xF9: case 0xFB: case 0xFC: case 0x153:
    return 1;
  default:
    return 0;
  }
}

// Whether no-elision.txt keeps words[i] whole before words[i + 1]: the next word, or the two.
static int kept_whole(uint64_t i) {
  for (const char *const *entry = lists.no_elision; *entry; entry++) {
    if (hammer_word_is(text, words[i + 1], *entry) || hammer_match_phrase(text, words, count, i, *entry)) return 1;
  }
  return 0;
}

// "le arbre" for "l'arbre", with elision.txt's words, but not after a hyphen ("prends-le avec toi",
// "puis-je entrer"), or before a capital's letter or an acronym ("de A à Z").
static void elision(void) {
  for (uint64_t i = 0; i + 1 < count; i++) {
    HammerWord word = words[i], next = words[i + 1];
    if (!hammer_spaced(text, word, next) || !hammer_word_in(text, word, lists.elision) ||
        (word.start > 0 && text.data[word.start - 1] == '-') || !starts_with_vowel(next) || hammer_all_upper(text, next) ||
        kept_whole(i)) {
      continue;
    }
    HammerBuffer out = {0};
    hammer_put(&out, text.data + word.start, hammer_char_before(text, word.end) - word.start);
    hammer_put_text(&out, apostrophe);
    hammer_put(&out, text.data + next.start, hammer_word_length(next));
    uint64_t length = out.length;
    const char *fix = hammer_terminated(&out);
    hammer_report_fix(word.start, next.end, message("Did you mean " OPEN_QUOTE, fix, length, CLOSE_QUOTE "?"), fix, length);
    i++;
  }
}

// "more then", "better then": only mid-sentence, since "it was better then." is the time.
static void than(void) {
  for (uint64_t i = 0; i + 2 < count; i++) {
    HammerWord then = words[i + 1];
    if (!hammer_word_is(text, then, "then") || !hammer_word_in(text, words[i], lists.comparatives) ||
        !hammer_spaced(text, words[i], then) || !hammer_spaced(text, then, words[i + 2]) ||
        hammer_word_in(text, words[i + 2], lists.after_then)) {
      continue;
    }
    const char *fix = hammer_is_upper(text.data[then.start]) ? "Than" : "than";
    hammer_report_fix(then.start, then.end, "Did you mean " OPEN_QUOTE "than" CLOSE_QUOTE "?", fix, 4);
  }
}

// ---------------------------------------------------------------------------------------------
// Punctuation and capitalization

// "i", "i'm", "i've", but not the "i" of "i.e.".
static void lone_i(void) {
  for (uint64_t i = 0; i < count; i++) {
    HammerWord word = words[i];
    if (text.data[word.start] != 'i') continue;
    int alone = hammer_word_length(word) == 1 && byte(word.end) != '.' && (word.start == 0 || byte(word.start - 1) != '.');
    int contracted = hammer_apostrophe_at(text, word.start + 1) != 0;
    if (alone || contracted) hammer_report_fix(word.start, word.start + 1, "Capitalize " OPEN_QUOTE "I" CLOSE_QUOTE, "I", 1);
  }
}

static void spacing(void) {
  for (uint64_t i = 0; i < text.length; i++) {
    char c = text.data[i];
    if (c == ' ' && i > 0 && byte(i + 1) == ' ') {
      uint64_t end = i;
      while (byte(end) == ' ') end++;
      // Two spaces after a sentence is a typing habit, not a slip.
      if (end < text.length && !sentence_end(text.data[i - 1]) && !space_like(text.data[i - 1])) {
        hammer_report_fix(i, end, "Extra spaces", " ", 1);
      }
      i = end - 1;
    } else if (c == ' ' && i > 0 && !space_like(text.data[i - 1])) {
      char next = (char)byte(i + 1);
      int lone_period = next == '.' && byte(i + 2) != '.';
      int face = (next == ':' || next == ';') && (byte(i + 2) == ')' || byte(i + 2) == '(');
      int spaced = typography->french && (next == ':' || next == ';');
      if ((next == ',' || next == ';' || next == ':' || lone_period) && !face && !spaced) {
        hammer_report_fix(i, i + 2, "Space before punctuation", &text.data[i + 1], 1);
      }
    } else if (c == ',' && hammer_letter_before(text, i) && hammer_letter_at(text, i + 1)) {
      hammer_report_fix(i, i + 1, "Missing space after a comma", ", ", 2);
    } else if (sentence_end(c) && i > 0 && hammer_lower_at(text, hammer_char_before(text, i)) &&
               hammer_letter_before(text, hammer_char_before(text, i)) && hammer_upper_at(text, i + 1)) {
      char fix[2] = {c, ' '};
      hammer_report_fix(i, i + 1, "Missing space after a sentence", fix, 2);
    } else if (c == ':' && typography->french && hammer_letter_before(text, i) && (i + 1 == text.length || byte(i + 1) == ' ')) {
      // A no-break space, so the colon keeps to its word.
      HammerFix fix = {"\xC2\xA0:", 3, "Add a space"};
      hammer_report(i, i + 1, "Missing space before a colon", &fix, 1);
    }
  }
}

static void sentence_starts(void) {
  for (uint64_t i = 1; i + 2 < text.length; i++) {
    char c = text.data[i];
    if (!(c == '.' || (typography->english && sentence_end(c))) || text.data[i + 1] != ' ' || text.data[i - 1] == '.') continue;
    if (c == '.' && abbreviated(i)) continue;
    uint64_t next = i + 1, length;
    while (byte(next) == ' ') next++;
    // Spanish's opening marks: ". ¿qué".
    uint32_t mark;
    while ((mark = hammer_char_at(text, next, &length)) == 0xBF || mark == 0xA1) next += length;
    // A lone "i" is lone_i's.
    if (!hammer_lower_at(text, next) || (typography->english && text.data[next] == 'i' && !hammer_letter_at(text, next + 1))) {
      continue;
    }
    char fix[4];
    uint32_t letter = hammer_char_at(text, next, &length);
    hammer_report_fix(next, next + length, "Capitalize the start of a sentence", fix, hammer_encode(hammer_upper(letter), fix));
  }
}

// ",,", ";;", "..", ".,", ",.", "!.", and "?.", but not an ellipsis or "etc.,".
static void doubled_punctuation(void) {
  for (uint64_t i = 0; i + 1 < text.length; i++) {
    char c = text.data[i], next = text.data[i + 1];
    if (byte(i + 2) == '.' || (i > 0 && text.data[i - 1] == '.')) continue;
    HammerFix fixes[2] = {{&text.data[i], 1, 0}, {&text.data[i + 1], 1, 0}};
    HammerFix swapped[2] = {fixes[1], fixes[0]};
    if ((c == ',' || c == ';') && next == c) {
      hammer_report(i, i + 2, "Doubled punctuation", fixes, 1);
    } else if (c == '.' && next == '.') {
      HammerFix ellipsis[2] = {{".", 1, 0}, {"...", 3, 0}};
      hammer_report(i, i + 2, "Doubled punctuation", ellipsis, 2);
    } else if (c == '.' && next == ',' && !abbreviated(i)) {
      hammer_report(i, i + 2, "Doubled punctuation", &fixes[1], 1);
    } else if (c == ',' && next == '.') {
      hammer_report(i, i + 2, "Doubled punctuation", swapped, 2);
    } else if ((c == '!' || c == '?') && next == '.') {
      hammer_report(i, i + 2, "Doubled punctuation", fixes, 1);
    }
  }
}

static int smiley(uint64_t i) { return i > 0 && (text.data[i - 1] == ':' || text.data[i - 1] == ';'); }

// Whether the ")" at i ends a list marker: "a)", "2)", "iv)".
static int list_marker(uint64_t i) {
  uint64_t start = i;
  while (start > 0 && i - start < 3 && (hammer_is_ascii_letter(text.data[start - 1]) || hammer_is_digit(text.data[start - 1]))) {
    start--;
  }
  return start < i && (start == 0 || text.data[start - 1] == ' ');
}

static void parentheses(void) {
  uint64_t open[64];
  uint64_t depth = 0, unrecorded = 0;
  for (uint64_t i = 0; i < text.length; i++) {
    char c = text.data[i];
    if ((c != '(' && c != ')') || smiley(i)) continue;
    if (c == '(') {
      if (depth < sizeof(open) / sizeof(open[0])) open[depth++] = i;
      else unrecorded++;
    } else if (unrecorded) {
      unrecorded--;
    } else if (depth) {
      depth--;
    } else if (!list_marker(i)) {
      hammer_report(i, i + 1, "Unmatched parenthesis", 0, 0);
    }
  }
  for (uint64_t k = 0; k < depth; k++) hammer_report(open[k], open[k] + 1, "Unclosed parenthesis", 0, 0);
}

// ---------------------------------------------------------------------------------------------
// Dialogue

// Whether the text between two words is `mark`, a closing quote, and spaces.
static int after_quote(HammerWord before, HammerWord word, char mark) {
  uint64_t i = before.end;
  if (byte(i) != (unsigned char)mark) return 0;
  uint64_t quote = closing_quote_at(i + 1);
  if (!quote) return 0;
  i += 1 + quote;
  if (i >= word.start) return 0;
  while (i < word.start && text.data[i] == ' ') i++;
  return i == word.start;
}

// Whether words[k] starts a dialogue tag: a speaking verb, or a pronoun and then one.
static int dialogue_tag(uint64_t k) {
  if (hammer_word_in(text, words[k], lists.tag_verbs)) return 1;
  return k + 1 < count && hammer_word_in(text, words[k], lists.pronouns) && hammer_spaced(text, words[k], words[k + 1]) &&
         hammer_word_in(text, words[k + 1], lists.tag_verbs);
}

// "Wait." she said: a comma. "Wait," She said: lower case.
static void dialogue_tags(void) {
  for (uint64_t k = 1; k < count; k++) {
    HammerWord word = words[k];
    char first = text.data[word.start];
    if (!dialogue_tag(k)) continue;
    if (hammer_is_lower(first) && after_quote(words[k - 1], word, '.') && !abbreviated(words[k - 1].end)) {
      hammer_report_fix(words[k - 1].end, words[k - 1].end + 1, "Use a comma before a dialogue tag", ",", 1);
    } else if (hammer_is_upper(first) && after_quote(words[k - 1], word, ',')) {
      char fix = hammer_to_lower(first);
      hammer_report_fix(word.start, word.start + 1, "A dialogue tag after a comma continues the sentence", &fix, 1);
    }
  }
}

// A quote run into the word beside it: "Wait,"she said, “Wait”she said, and said“Wait”. A straight
// double quote closes when an odd number come before it.
static void quote_spacing(void) {
  int straight_open = byte(0) == '"';
  for (uint64_t i = 1; i < text.length; i++) {
    char before = text.data[i - 1];
    uint64_t quote = closing_quote_at(i), open = mark_at(i, typography->open);
    int marked_close = mark_at(i, typography->close) != 0;
    int closes = marked_close || byte(i) == '\'' || (byte(i) == '"' && straight_open);
    int after = quote && closes && hammer_letter_at(text, i + quote) && (marked_close || before == ',' || sentence_end(before));
    int ahead = !after && open && hammer_letter_before(text, i);
    if (after || ahead) {
      uint64_t length = after ? quote : open;
      char *spaced = hammer_must_alloc(length + 1);
      memcpy(spaced + ahead, text.data + i, length);
      spaced[ahead ? 0 : length] = ' ';
      HammerFix fix = {spaced, length + 1, "Add a space"};
      hammer_report(i, i + length, after ? "Missing space after a quote" : "Missing space before a quote", &fix, 1);
    }
    if (byte(i) == '"') straight_open = !straight_open;
    uint64_t skip = quote ? quote : open;
    if (skip) i += skip - 1;
  }
}

// "Wait.", she said: a quote a comma follows ends without a full stop. English moves the comma in,
// "Wait," she said, and other languages drop the stop: „Warte“, sagte sie.
static void quote_comma(void) {
  for (uint64_t i = 1; i < text.length; i++) {
    if (text.data[i] != '.' || text.data[i - 1] == '.' || abbreviated(i)) continue;
    // French spaces its guillemets: « Viens. », dit-il.
    uint64_t at = i + 1;
    while (byte(at) == ' ' || (byte(at) == 0xC2 && byte(at + 1) == 0xA0) || hammer_char_is(text, at, 0x202F)) {
      at += hammer_char_length(text, at);
    }
    uint64_t quote = closing_quote_at(at);
    if (!quote || byte(at + quote) != ',') continue;
    if (typography->english) {
      uint64_t length = at + quote - i;
      char *fix = hammer_must_alloc(length);
      fix[0] = ',';
      memcpy(fix + 1, text.data + i + 1, length - 1);
      hammer_report_fix(i, at + quote + 1, "Use a comma before a dialogue tag", fix, length);
    } else {
      HammerFix fix = {"", 0, "Remove the full stop"};
      hammer_report(i, i + 1, "A quote followed by a comma ends without a full stop", &fix, 1);
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Style

// "!!", "??", and "?!?", but not "?!" or "!?".
static void repeated_marks(void) {
  for (uint64_t i = 0; i < text.length; i++) {
    if (text.data[i] != '!' && text.data[i] != '?') continue;
    uint64_t end = i;
    int bangs = 0, questions = 0;
    while (byte(end) == '!' || byte(end) == '?') {
      if (text.data[end] == '!') bangs++;
      else questions++;
      end++;
    }
    if (end - i >= 2 && !(bangs == 1 && questions == 1)) {
      const char *fix = bangs && questions ? "?!" : bangs ? "!" : "?";
      hammer_report_fix(i, end, "Repeated punctuation", fix, strlen(fix));
    }
    i = end - 1;
  }
}

// Whether a sentence ends between two words: a stop, not after an abbreviation, then any closing
// quotes or brackets and a space.
static int sentence_break(HammerWord a, HammerWord b) {
  for (uint64_t i = a.end; i < b.start; i++) {
    char c = text.data[i];
    if (!sentence_end(c) || (c == '.' && (byte(i + 1) == '.' || abbreviated(i)))) continue;
    uint64_t next = i + 1;
    while (next < b.start && (text.data[next] == ')' || closing_quote_at(next))) {
      next += text.data[next] == ')' ? 1 : closing_quote_at(next);
    }
    if (next < b.start && space_like(text.data[next])) return 1;
  }
  return 0;
}

static void long_sentences(void) {
  uint64_t first = 0;
  for (uint64_t k = 0; k < count; k++) {
    if (k + 1 < count && !sentence_break(words[k], words[k + 1])) continue;
    uint64_t length = k + 1 - first;
    if ((int64_t)length > settings.long_sentence_words) {
      HammerBuffer note = {0};
      hammer_put_text(&note, "Long sentence: ");
      hammer_put_int(&note, (int64_t)length);
      hammer_put_text(&note, " words");
      hammer_report(words[first].start, words[k].end, hammer_terminated(&note), 0, 0);
    }
    first = k + 1;
  }
}

// ---------------------------------------------------------------------------------------------
// Export

// A language the package has lists for, English when none is given.
int hammer_diagnose_begin(const HammerDiagnoseRequest *request) {
  settings.mistakes = hammer_setting_bool(request, "mistakes", 1);
  settings.punctuation = hammer_setting_bool(request, "punctuation", 1);
  settings.dialogue = hammer_setting_bool(request, "dialogue", 1);
  settings.wordiness = hammer_setting_bool(request, "wordiness", 0);
  settings.redundancy = hammer_setting_bool(request, "redundancy", 0);
  settings.repeated_marks = hammer_setting_bool(request, "repeatedMarks", 0);
  settings.long_sentences = hammer_setting_bool(request, "longSentences", 0);
  settings.long_sentence_words = hammer_setting_int(request, "longSentenceWords", 40);
  if (settings.long_sentence_words < 1) settings.long_sentence_words = 1;
  if (!load_lists(request)) return 0;
  typography = &OTHER_TYPOGRAPHY;
  for (uint64_t i = 0; i < sizeof(TYPOGRAPHY) / sizeof(TYPOGRAPHY[0]); i++) {
    if (hammer_json_key_is(lists.language, strlen(lists.language), TYPOGRAPHY[i].code)) typography = &TYPOGRAPHY[i];
  }
  return 1;
}

void hammer_diagnose_paragraph(HammerText paragraph) {
  text = paragraph;
  words = hammer_words(text, &count);
  apostrophe = "'";
  for (uint64_t i = 0; i < text.length; i++) {
    if (hammer_byte(text, i) == 0xCA && hammer_byte(text, i + 1) == 0xBC) {
      apostrophe = "\xCA\xBC";
      break;
    }
    if (hammer_char_is(text, i, 0x2019) || hammer_char_is(text, i, 0x201C) || hammer_char_is(text, i, 0x201D)) {
      apostrophe = "\xE2\x80\x99";
    }
  }
  int english = typography->english;
  if (settings.mistakes) {
    repeated_words();
    if (english) articles();
    elision();
    phrases(lists.mistakes, "Did you mean " OPEN_QUOTE, CLOSE_QUOTE "?");
    if (english) than();
  }
  if (settings.punctuation) {
    if (english) lone_i();
    spacing();
    sentence_starts();
    doubled_punctuation();
    parentheses();
  }
  if (settings.dialogue) {
    dialogue_tags();
    quote_spacing();
    quote_comma();
  }
  if (settings.wordiness) phrases(lists.wordy, "Wordy; " OPEN_QUOTE, CLOSE_QUOTE " says the same");
  if (settings.redundancy) phrases(lists.redundant, "Redundant; " OPEN_QUOTE, CLOSE_QUOTE " says the same");
  if (settings.repeated_marks) repeated_marks();
  if (settings.long_sentences) long_sentences();
}
