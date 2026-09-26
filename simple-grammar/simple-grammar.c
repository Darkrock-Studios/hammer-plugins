// Simple grammar: common slips in prose, underlined in the editor through the `diagnose` export.
// Every rule is a pass over one paragraph's words or bytes, with small word lists, so checking is
// fast and the module stays a few KB. The rules stay where they are sure, since every word involved
// is a real one: a spell checker passes these slips and a writer should not be nagged. Settings turn
// groups of rules on and off.
//
// The lists are resources, in resources/<language>/ for each language it checks, and a project in a
// language without them gets no diagnostics. A, an, a lone "i", and "then" for "than" are English's
// own rules, and the punctuation rules assume English typography.
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
      *const *after_then, *const *abbreviations, *const *tag_verbs, *const *pronouns, *const *repeatable;
} lists;

static void add_entry(const char *entry) {
  if (entries_used == LIST_ENTRIES) hammer_fail("simple-grammar's word lists have too many lines");
  list_entries[entries_used++] = entry;
}

// Languages found to have no lists, so checking one does not drop the lists kept.
static char unlisted[8][sizeof(lists.language)];
static int unlisted_count;

enum { MISTAKES, WORDY, REDUNDANT, POSSESSIVES, COMPARATIVES, AFTER_THEN, ABBREVIATIONS, TAG_VERBS, PRONOUNS, REPEATABLE, LIST_COUNT };

static const char *const LIST_NAMES[LIST_COUNT] = {
  "mistakes", "wordy", "redundant", "possessives", "comparatives", "after-then", "abbreviations", "tag-verbs", "pronouns",
  "repeatable",
};

// Copies a list's lines, from hammer_resource_lines, into the kept lists. With `pairs`, each line is
// `phrase = fix`, listed as the phrase, in lower case, and then the fix.
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
      char *equals = line;
      while (*equals && !(equals[0] == ' ' && equals[1] == '=' && equals[2] == ' ')) {
        *equals = hammer_to_lower(*equals);
        equals++;
      }
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
  lists.found = 1;
  return 1;
}

// ---------------------------------------------------------------------------------------------
// The paragraph

static HammerText text;
static HammerWord *words;
static uint64_t count;
// Whether the paragraph uses curly quotes or apostrophes, which fixes then use too.
static int curly;

static unsigned char byte(uint64_t i) { return hammer_byte(text, i); }

// A fix's text: the first letter a capital when `capital`, apostrophes curly when the paragraph's are.
static const char *styled(const char *fix, int capital, uint64_t *length) {
  HammerBuffer out = {0};
  for (const char *c = fix; *c; c++) {
    if (*c == '\'' && curly) hammer_put_text(&out, "\xE2\x80\x99");
    else hammer_put_char(&out, c == fix && capital ? hammer_to_upper(*c) : *c);
  }
  *length = out.length;
  return hammer_terminated(&out);
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

// `table` alternates a phrase and its fix.
static void phrases(const char *const *table, const char *before, const char *after) {
  for (uint64_t i = 0; i < count; i++) {
    for (const char *const *entry = table; entry[0]; entry += 2) {
      uint64_t matched = match(entry[0], i);
      if (!matched) continue;
      uint64_t length;
      const char *fix = styled(fix_for(entry[0], entry[1], i), hammer_is_upper(text.data[words[i].start]), &length);
      hammer_report_fix(words[i].start, words[i + matched - 1].end, message(before, fix, length, after), fix, length);
      i += matched - 1;
      break;
    }
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

static int sentence_end(char c) { return c == '.' || c == '!' || c == '?'; }

static int space_like(char c) { return c == ' ' || c == '\t'; }

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
      if ((next == ',' || next == ';' || next == ':' || lone_period) && !face) {
        hammer_report_fix(i, i + 2, "Space before punctuation", &text.data[i + 1], 1);
      }
    } else if (c == ',' && i > 0 && hammer_is_ascii_letter(text.data[i - 1]) && hammer_is_ascii_letter((char)byte(i + 1))) {
      hammer_report_fix(i, i + 1, "Missing space after a comma", ", ", 2);
    } else if (sentence_end(c) && i > 1 && hammer_is_lower(text.data[i - 1]) && hammer_is_ascii_letter(text.data[i - 2]) &&
               hammer_is_upper((char)byte(i + 1))) {
      char fix[2] = {c, ' '};
      hammer_report_fix(i, i + 1, "Missing space after a sentence", fix, 2);
    }
  }
}

// Whether the text before the full stop at `end` is an abbreviation, an initial, or an initialism
// ("T.V."), after which a lower-case word is fine.
static int abbreviated(uint64_t end) {
  uint64_t start = end;
  int dotted = 0;
  while (start > 0 && (hammer_is_ascii_letter(text.data[start - 1]) || text.data[start - 1] == '.')) {
    dotted |= text.data[--start] == '.';
  }
  if (end - start <= 1 || dotted) return 1;
  return hammer_word_in(text, (HammerWord){start, end}, lists.abbreviations);
}

static void sentence_starts(void) {
  for (uint64_t i = 1; i + 2 < text.length; i++) {
    char c = text.data[i];
    if (!sentence_end(c) || text.data[i + 1] != ' ' || text.data[i - 1] == '.') continue;
    if (c == '.' && abbreviated(i)) continue;
    uint64_t next = i + 1;
    while (byte(next) == ' ') next++;
    // A lone "i" is lone_i's.
    if (!hammer_is_lower((char)byte(next)) || (text.data[next] == 'i' && !hammer_letter_at(text, next + 1))) continue;
    char fix = hammer_to_upper(text.data[next]);
    hammer_report_fix(next, next + 1, "Capitalize the start of a sentence", &fix, 1);
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

// Bytes in the closing quote at i: straight or curly, double or single.
static uint64_t closing_quote_at(uint64_t i) {
  if (byte(i) == '"' || byte(i) == '\'') return 1;
  return hammer_char_is(text, i, 0x201D) || hammer_char_is(text, i, 0x2019) ? 3 : 0;
}

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
    uint64_t quote = closing_quote_at(i);
    int curly_close = hammer_char_is(text, i, 0x201D);
    int curly_open = hammer_char_is(text, i, 0x201C);
    int closes = curly_close || byte(i) == '\'' || (byte(i) == '"' && straight_open);
    int after = quote && closes && hammer_letter_at(text, i + quote) && (curly_close || before == ',' || sentence_end(before));
    int ahead = curly_open && hammer_letter_at(text, i - 1);
    if (after || ahead) {
      uint64_t length = quote ? quote : 3;
      char *spaced = hammer_must_alloc(length + 1);
      memcpy(spaced + ahead, text.data + i, length);
      spaced[ahead ? 0 : length] = ' ';
      HammerFix fix = {spaced, length + 1, "Add a space"};
      hammer_report(i, i + length, after ? "Missing space after a quote" : "Missing space before a quote", &fix, 1);
    }
    if (byte(i) == '"') straight_open = !straight_open;
    if (quote || curly_open) i += (quote ? quote : 3) - 1;
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
  return load_lists(request);
}

void hammer_diagnose_paragraph(HammerText paragraph) {
  text = paragraph;
  words = hammer_words(text, &count);
  curly = 0;
  for (uint64_t i = 0; i < text.length && !curly; i++) {
    curly = hammer_char_is(text, i, 0x2019) || hammer_char_is(text, i, 0x201C) || hammer_char_is(text, i, 0x201D);
  }
  int english = hammer_json_key_is(lists.language, strlen(lists.language), "en");
  if (settings.mistakes) {
    repeated_words();
    if (english) articles();
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
  }
  if (settings.wordiness) phrases(lists.wordy, "Wordy; " OPEN_QUOTE, CLOSE_QUOTE " says the same");
  if (settings.redundancy) phrases(lists.redundant, "Redundant; " OPEN_QUOTE, CLOSE_QUOTE " says the same");
  if (settings.repeated_marks) repeated_marks();
  if (settings.long_sentences) long_sentences();
}
