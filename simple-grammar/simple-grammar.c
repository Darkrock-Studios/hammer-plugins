// Simple grammar: common slips in English prose, underlined in the editor through the `diagnose` export.
// Repeated words, "a" and "an", "could of" and its kin, a lower-case "i", spacing around
// punctuation, and a sentence starting in lower case. The rules stay where they are sure, so a
// writer is not nagged. Projects in another language get no diagnostics.
#define HAMMER_IMPLEMENTATION
#define HAMMER_OUT_OF_MEMORY "The text is too long to check"
#include "../include/hammer.h"
#include "../include/hammer_diagnose.h"

static HammerText text;

static unsigned char byte(uint64_t i) { return hammer_byte(text, i); }

// ---------------------------------------------------------------------------------------------
// Rules

static void repeated_words(const HammerWord *words, uint64_t count) {
  for (uint64_t i = 0; i + 1 < count; i++) {
    HammerWord a = words[i], b = words[i + 1];
    // "had had" and "that that" are good English.
    if (!hammer_spaced(text, a, b) || !hammer_same_word(text, a, b) || hammer_word_is(text, a, "had") ||
        hammer_word_is(text, a, "that")) {
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

static void articles(const HammerWord *words, uint64_t count) {
  for (uint64_t i = 0; i + 1 < count; i++) {
    HammerWord article = words[i], next = words[i + 1];
    if (!hammer_spaced(text, article, next) || hammer_word_length(next) < 2 || hammer_all_upper(text, next) ||
        !hammer_is_ascii_letter(text.data[next.start])) {
      continue;
    }
    int capital = hammer_is_upper(text.data[article.start]);
    if (hammer_word_is(text, article, "a") && vowel_sound(next)) {
      hammer_report_fix(article.start, article.end, "Use \xE2\x80\x9C" "an\xE2\x80\x9D before a vowel sound",
                        capital ? "An" : "an", 2);
    } else if (hammer_word_is(text, article, "an") && consonant_sound(next)) {
      hammer_report_fix(article.start, article.end, "Use \xE2\x80\x9C" "a\xE2\x80\x9D before a consonant sound",
                        capital ? "A" : "a", 1);
    }
  }
}

static const char *const PHRASES[][2] = {
  {"could of", "could have"}, {"should of", "should have"}, {"would of", "would have"},
  {"must of", "must have"},   {"might of", "might have"},   {"alot", "a lot"},
  {"irregardless", "regardless"}, {"per say", "per se"},
};

static void phrases(const HammerWord *words, uint64_t count) {
  HammerBuffer message = {0};
  for (uint64_t i = 0; i < count; i++) {
    for (uint64_t p = 0; p < sizeof(PHRASES) / sizeof(PHRASES[0]); p++) {
      uint64_t matched = hammer_match_phrase(text, words, count, i, PHRASES[p][0]);
      if (!matched) continue;
      const char *fix = PHRASES[p][1];
      uint64_t fix_length = strlen(fix);
      char *cased = hammer_must_alloc(fix_length);
      memcpy(cased, fix, fix_length);
      if (hammer_is_upper(text.data[words[i].start])) cased[0] = hammer_to_upper(cased[0]);
      message.length = 0;
      hammer_put_text(&message, "Did you mean \xE2\x80\x9C");
      hammer_put(&message, cased, fix_length);
      hammer_put_text(&message, "\xE2\x80\x9D?");
      hammer_report_fix(words[i].start, words[i + matched - 1].end, hammer_terminated(&message), cased, fix_length);
      break;
    }
  }
}

// "i", "i'm", "i've", but not the "i" of "i.e.".
static void lone_i(const HammerWord *words, uint64_t count) {
  for (uint64_t i = 0; i < count; i++) {
    HammerWord word = words[i];
    if (text.data[word.start] != 'i') continue;
    int alone = hammer_word_length(word) == 1 && byte(word.end) != '.' && (word.start == 0 || byte(word.start - 1) != '.');
    int contracted = hammer_apostrophe_at(text, word.start + 1) != 0;
    if (alone || contracted) hammer_report_fix(word.start, word.start + 1, "Capitalize \xE2\x80\x9CI\xE2\x80\x9D", "I", 1);
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
      if (next == ',' || next == ';' || next == ':' || lone_period) {
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

static const char *const ABBREVIATIONS[] = {"e.g", "i.e", "etc", "vs", "cf", "approx", "a.m", "p.m", "viz", "ca", 0};

// Whether the text before the full stop at `end` is an abbreviation or an initial, after which a
// lower-case word is fine.
static int abbreviated(uint64_t end) {
  uint64_t start = end;
  while (start > 0 && (hammer_is_ascii_letter(text.data[start - 1]) || text.data[start - 1] == '.')) start--;
  if (end - start <= 1) return 1;
  return hammer_word_in(text, (HammerWord){start, end}, ABBREVIATIONS);
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

// ---------------------------------------------------------------------------------------------
// Export

// English, or no language given.
int hammer_diagnose_begin(const HammerDiagnoseRequest *request) {
  return !request->language.data || hammer_language_is(request, "en");
}

void hammer_diagnose_paragraph(HammerText paragraph) {
  text = paragraph;
  uint64_t count;
  HammerWord *words = hammer_words(text, &count);
  repeated_words(words, count);
  articles(words, count);
  phrases(words, count);
  lone_i(words, count);
  spacing();
  sentence_starts();
}
