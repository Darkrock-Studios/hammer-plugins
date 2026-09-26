// Plain text export, for pasting a story into submission forms: their conventions for scene breaks,
// italics, and paragraphs, chosen in the plugin's settings. Reads the host's parse of each scene
// (`input = "prose"`), so it needs no markdown parser.
//
// Output is written straight into one buffer. Anything that turns out blank (a paragraph, a
// chapter) is rolled back by resetting the length to a mark taken before it was written.

#define HAMMER_IMPLEMENTATION
#include "hammer.h"
#include "hammer_json.h"

enum { BREAK_HASH, BREAK_ASTERISKS, BREAK_BLANK };
enum { ITALICS_UNDERSCORES, ITALICS_ASTERISKS, ITALICS_DROPPED };
enum { PARAGRAPHS_BLANK_LINE, PARAGRAPHS_INDENTED };

#define MAX_LIST_DEPTH 32

static struct {
  int scene_break;
  int italics;
  int paragraphs;
  int chapter_headings;
} settings;

static struct {
  char *data;
  uint64_t length;
  uint64_t capacity;
  int overflow;
} out;

// Paragraphs written in the passage being rendered; a passage is a scene, or the part of one between rules.
static int passage_paragraphs;
// Passages written in the chapter being rendered.
static int chapter_passages;
// Where the current paragraph's own text starts, after its separator.
static uint64_t paragraph_start;

static void put(const char *text, uint64_t length) {
  if (out.length + length > out.capacity) {
    out.overflow = 1;
    return;
  }
  memcpy(out.data + out.length, text, length);
  out.length += length;
}

static void put_text(const char *text) { put(text, strlen(text)); }

static void put_number(uint32_t n) {
  char digits[10];
  int length = 0;
  do {
    digits[length++] = (char)('0' + n % 10);
    n /= 10;
  } while (n);
  while (length) put(&digits[--length], 1);
}

// Appends a JSON string's decoded contents.
static void put_string(const char *raw, uint64_t raw_length) {
  if (out.length + raw_length > out.capacity) {
    out.overflow = 1;
    return;
  }
  out.length += hammer_json_unescape(raw, raw_length, out.data + out.length);
}

static void insert(uint64_t at, char c) {
  if (out.length + 1 > out.capacity) {
    out.overflow = 1;
    return;
  }
  for (uint64_t i = out.length; i > at; i--) out.data[i] = out.data[i - 1];
  out.data[at] = c;
  out.length++;
}

// The length of the whitespace character at text[at], or 0: ASCII whitespace plus the Unicode spaces
// Kotlin's isWhitespace counts, which the built-in formats go by.
static uint64_t space_at(const char *text, uint64_t at, uint64_t end) {
  unsigned char c = (unsigned char)text[at];
  if (c == ' ' || (c >= '\t' && c <= '\r') || (c >= 0x1C && c <= 0x1F)) return 1;
  if (c == 0xC2 && at + 1 < end) return (unsigned char)text[at + 1] == 0xA0 ? 2 : 0;
  if (c == 0xE1 && at + 2 < end && (unsigned char)text[at + 1] == 0x9A && (unsigned char)text[at + 2] == 0x80) return 3;
  if (c == 0xE2 && at + 2 < end) {
    unsigned char d = (unsigned char)text[at + 1], e = (unsigned char)text[at + 2];
    if (d == 0x80 && (e <= 0x8A || e == 0xA8 || e == 0xA9 || e == 0xAF)) return 3;
    if (d == 0x81 && e == 0x9F) return 3;
  }
  if (c == 0xE3 && at + 2 < end && (unsigned char)text[at + 1] == 0x80 && (unsigned char)text[at + 2] == 0x80) return 3;
  return 0;
}

static int is_blank(uint64_t start, uint64_t end) {
  uint64_t at = start;
  while (at < end) {
    uint64_t space = space_at(out.data, at, end);
    if (space == 0) return 0;
    at += space;
  }
  return 1;
}

// The length of the whitespace ending at `end`, stopping at `start`.
static uint64_t trailing_space(uint64_t start, uint64_t end) {
  uint64_t at = end;
  while (at > start) {
    // Step back over one UTF-8 character.
    uint64_t begin = at - 1;
    while (begin > start && ((unsigned char)out.data[begin] & 0xC0) == 0x80) begin--;
    if (space_at(out.data, begin, at) != at - begin) break;
    at = begin;
  }
  return end - at;
}

// Tabs every line of the current paragraph, a list's or a code block's included, in one pass from the end.
static void indent_lines(void) {
  uint64_t lines = 1;
  for (uint64_t at = paragraph_start; at < out.length; at++) lines += out.data[at] == '\n';
  if (out.length + lines > out.capacity) {
    out.overflow = 1;
    return;
  }
  uint64_t from = out.length, to = out.length + lines;
  out.length = to;
  while (from > paragraph_start) {
    char c = out.data[--from];
    if (c == '\n') out.data[--to] = '\t';
    out.data[--to] = c;
  }
  out.data[--to] = '\t';
}

static void separate_paragraph(void) {
  if (passage_paragraphs == 0) {
    if (chapter_passages == 0) return;
    if (settings.scene_break == BREAK_BLANK) put_text("\n\n\n");
    else put_text(settings.scene_break == BREAK_HASH ? "\n\n#\n\n" : "\n\n* * *\n\n");
  } else {
    put_text(settings.paragraphs == PARAGRAPHS_INDENTED ? "\n" : "\n\n");
  }
}

// Starts a paragraph, returning the mark to roll back to if it turns out blank.
static uint64_t begin_paragraph(void) {
  uint64_t mark = out.length;
  separate_paragraph();
  paragraph_start = out.length;
  return mark;
}

static void end_paragraph(uint64_t mark) {
  if (is_blank(paragraph_start, out.length)) {
    out.length = mark;
    return;
  }
  if (settings.paragraphs == PARAGRAPHS_INDENTED) indent_lines();
  if (passage_paragraphs == 0) chapter_passages++;
  passage_paragraphs++;
}

// Wraps the italic run written since `start` in the italics marker, keeping its edge whitespace
// outside, so `_word_` never becomes `_word _`.
static void close_italic_run(uint64_t start) {
  if (settings.italics == ITALICS_DROPPED || is_blank(start, out.length)) return;
  char marker = settings.italics == ITALICS_UNDERSCORES ? '_' : '*';
  uint64_t text_start = start;
  while (text_start < out.length) {
    uint64_t space = space_at(out.data, text_start, out.length);
    if (space == 0) break;
    text_start += space;
  }
  uint64_t text_end = out.length - trailing_space(text_start, out.length);
  insert(text_end, marker);
  insert(text_start, marker);
}

// Consecutive italic spans are wrapped once, so bold or a link inside an italic run does not split it.
static void render_spans(HammerJson *json) {
  if (!hammer_json_enter_array(json)) return;
  int in_run = 0;
  uint64_t run_start = 0;
  while (hammer_json_next_item(json)) {
    const char *text = 0, *key;
    uint64_t text_length = 0, key_length;
    int italic = 0;
    if (!hammer_json_enter_object(json)) return;
    while (hammer_json_next_key(json, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "text")) hammer_json_string(json, &text, &text_length);
      else if (hammer_json_key_is(key, key_length, "italic")) italic = hammer_json_bool(json);
      else hammer_json_skip(json);
    }
    if (italic && !in_run) {
      in_run = 1;
      run_start = out.length;
    } else if (!italic && in_run) {
      close_italic_run(run_start);
      in_run = 0;
    }
    if (text) put_string(text, text_length);
  }
  if (in_run) close_italic_run(run_start);
}

static void render_spans_at(HammerJson *json, uint64_t at) {
  HammerJson spans = *json;
  spans.at = at;
  render_spans(&spans);
  if (spans.error) json->error = 1;
}

// A list is one paragraph, an item per line, numbered per nesting level.
static void render_list(HammerJson *json) {
  uint32_t counters[MAX_LIST_DEPTH];
  int depth = 0;
  int first = 1;
  if (!hammer_json_enter_array(json)) return;
  while (hammer_json_next_item(json)) {
    const char *key;
    uint64_t key_length, spans_at = 0;
    int64_t level = 0;
    int ordered = 0;
    if (!hammer_json_enter_object(json)) return;
    while (hammer_json_next_key(json, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "level")) level = hammer_json_int(json);
      else if (hammer_json_key_is(key, key_length, "ordered")) ordered = hammer_json_bool(json);
      else if (hammer_json_key_is(key, key_length, "spans")) {
        hammer_json_peek(json);
        spans_at = json->at;
        hammer_json_skip(json);
      } else hammer_json_skip(json);
    }
    if (level < 0) level = 0;
    if (level >= MAX_LIST_DEPTH) level = MAX_LIST_DEPTH - 1;
    // A shallower item ends the deeper lists under it.
    while (depth < level + 1) counters[depth++] = 0;
    depth = (int)level + 1;
    counters[level]++;

    if (!first) put_text("\n");
    first = 0;
    for (int64_t i = 0; i < level; i++) put_text("  ");
    if (ordered) {
      put_number(counters[level]);
      put_text(". ");
    } else {
      put_text("- ");
    }
    if (spans_at) render_spans_at(json, spans_at);
  }
}

// A table is one paragraph, a row per line and cells split by " | ".
static void render_row(HammerJson *json) {
  int first = 1;
  if (!hammer_json_enter_array(json)) return;
  while (hammer_json_next_item(json)) {
    if (!first) put_text(" | ");
    first = 0;
    render_spans(json);
  }
}

static void render_rows(HammerJson *json, int *first_row) {
  if (!hammer_json_enter_array(json)) return;
  while (hammer_json_next_item(json)) {
    if (!*first_row) put_text("\n");
    *first_row = 0;
    render_row(json);
  }
}

enum { BLOCK_UNKNOWN, BLOCK_PARAGRAPH, BLOCK_HEADING, BLOCK_LIST, BLOCK_QUOTE, BLOCK_CODE, BLOCK_RULE, BLOCK_TABLE };

// The block's type, read ahead without moving `json`.
static int block_type(HammerJson *json) {
  HammerJson ahead = *json;
  const char *key, *type;
  uint64_t key_length, type_length;
  if (!hammer_json_enter_object(&ahead)) return BLOCK_UNKNOWN;
  while (hammer_json_next_key(&ahead, &key, &key_length)) {
    if (!hammer_json_key_is(key, key_length, "type")) {
      hammer_json_skip(&ahead);
      continue;
    }
    if (!hammer_json_string(&ahead, &type, &type_length)) return BLOCK_UNKNOWN;
    if (hammer_json_key_is(type, type_length, "paragraph")) return BLOCK_PARAGRAPH;
    if (hammer_json_key_is(type, type_length, "heading")) return BLOCK_HEADING;
    if (hammer_json_key_is(type, type_length, "list")) return BLOCK_LIST;
    if (hammer_json_key_is(type, type_length, "quote")) return BLOCK_QUOTE;
    if (hammer_json_key_is(type, type_length, "code")) return BLOCK_CODE;
    if (hammer_json_key_is(type, type_length, "rule")) return BLOCK_RULE;
    if (hammer_json_key_is(type, type_length, "table")) return BLOCK_TABLE;
    return BLOCK_UNKNOWN;
  }
  return BLOCK_UNKNOWN;
}

static void render_block(HammerJson *json) {
  int type = block_type(json);
  if (type == BLOCK_RULE) {
    // The author's own break: it separates passages like a scene boundary.
    passage_paragraphs = 0;
  }
  // A table is one paragraph; the rest are one per block, except a quote's, one per paragraph.
  int first_row = 1;
  uint64_t table_mark = 0;
  if (type == BLOCK_TABLE) table_mark = begin_paragraph();

  const char *key;
  uint64_t key_length;
  if (!hammer_json_enter_object(json)) return;
  while (hammer_json_next_key(json, &key, &key_length)) {
    if ((type == BLOCK_PARAGRAPH || type == BLOCK_HEADING) && hammer_json_key_is(key, key_length, "spans")) {
      uint64_t mark = begin_paragraph();
      render_spans(json);
      end_paragraph(mark);
    } else if (type == BLOCK_LIST && hammer_json_key_is(key, key_length, "items")) {
      uint64_t mark = begin_paragraph();
      render_list(json);
      end_paragraph(mark);
    } else if (type == BLOCK_QUOTE && hammer_json_key_is(key, key_length, "paragraphs")) {
      if (!hammer_json_enter_array(json)) return;
      while (hammer_json_next_item(json)) {
        uint64_t mark = begin_paragraph();
        put_text("    ");
        render_spans(json);
        end_paragraph(mark);
      }
    } else if (type == BLOCK_CODE && hammer_json_key_is(key, key_length, "code")) {
      const char *code;
      uint64_t code_length;
      uint64_t mark = begin_paragraph();
      if (hammer_json_string(json, &code, &code_length)) put_string(code, code_length);
      out.length -= trailing_space(paragraph_start, out.length);
      end_paragraph(mark);
    } else if (type == BLOCK_TABLE && (hammer_json_key_is(key, key_length, "header"))) {
      // The header is one row: a list of cells.
      if (!first_row) put_text("\n");
      first_row = 0;
      render_row(json);
    } else if (type == BLOCK_TABLE && hammer_json_key_is(key, key_length, "rows")) {
      render_rows(json, &first_row);
    } else {
      hammer_json_skip(json);
    }
  }
  if (type == BLOCK_TABLE) end_paragraph(table_mark);
}

// Set when a chapter carries `prose`; a host from before prose input sends only `scenes`.
static int saw_prose;

static void render_chapter(HammerJson *json, int headings, int *chapters_written) {
  const char *key, *name = 0;
  uint64_t key_length, name_length = 0, prose_at = 0;
  if (!hammer_json_enter_object(json)) return;
  while (hammer_json_next_key(json, &key, &key_length)) {
    if (hammer_json_key_is(key, key_length, "name")) {
      hammer_json_string(json, &name, &name_length);
    } else if (hammer_json_key_is(key, key_length, "prose")) {
      saw_prose = 1;
      hammer_json_peek(json);
      prose_at = json->at;
      hammer_json_skip(json);
    } else {
      hammer_json_skip(json);
    }
  }

  uint64_t mark = out.length;
  if (*chapters_written) put_text("\n\n\n\n");
  uint64_t chapter_start = out.length;
  if (headings && name) {
    put_string(name, name_length);
    put_text("\n\n");
  }
  chapter_passages = 0;
  if (prose_at) {
    HammerJson scenes = *json;
    scenes.at = prose_at;
    if (hammer_json_enter_array(&scenes)) {
      while (hammer_json_next_item(&scenes)) {
        passage_paragraphs = 0;
        if (!hammer_json_enter_array(&scenes)) break;
        while (hammer_json_next_item(&scenes)) render_block(&scenes);
      }
    }
    if (scenes.error) json->error = 1;
  }
  if (is_blank(chapter_start, out.length)) {
    out.length = mark;
  } else {
    (*chapters_written)++;
  }
}

static void read_settings(HammerJson *json) {
  const char *key, *value;
  uint64_t key_length, value_length;
  if (!hammer_json_enter_object(json)) return;
  while (hammer_json_next_key(json, &key, &key_length)) {
    if (hammer_json_key_is(key, key_length, "chapterHeadings")) {
      settings.chapter_headings = hammer_json_bool(json);
    } else if (hammer_json_peek(json) != '"') {
      hammer_json_skip(json);
    } else {
      hammer_json_string(json, &value, &value_length);
      if (hammer_json_key_is(key, key_length, "sceneBreak")) {
        settings.scene_break = hammer_json_key_is(value, value_length, "Asterisks") ? BREAK_ASTERISKS
          : hammer_json_key_is(value, value_length, "Blank") ? BREAK_BLANK
          : BREAK_HASH;
      } else if (hammer_json_key_is(key, key_length, "italics")) {
        settings.italics = hammer_json_key_is(value, value_length, "Asterisks") ? ITALICS_ASTERISKS
          : hammer_json_key_is(value, value_length, "Dropped") ? ITALICS_DROPPED
          : ITALICS_UNDERSCORES;
      } else if (hammer_json_key_is(key, key_length, "paragraphs")) {
        settings.paragraphs = hammer_json_key_is(value, value_length, "Indented") ? PARAGRAPHS_INDENTED : PARAGRAPHS_BLANK_LINE;
      }
    }
  }
}

int32_t EXTISM_EXPORTED_FUNCTION(export) {
  hammer_alloc_reset();
  uint64_t size = extism_input_length();
  char *input = hammer_alloc(size);
  // Markers, indents, and breaks add less than the JSON around the text takes away, but leave room.
  out.capacity = size * 2 + 65536;
  out.data = hammer_alloc(out.capacity);
  out.length = 0;
  out.overflow = 0;
  if (!input || !out.data) {
    extism_error_set_buf_from_sz("The story is too long to export as plain text");
    return 1;
  }
  extism_load_input(0, input, size);

  settings.scene_break = BREAK_HASH;
  settings.italics = ITALICS_UNDERSCORES;
  settings.paragraphs = PARAGRAPHS_BLANK_LINE;
  settings.chapter_headings = 1;

  HammerJson json;
  hammer_json_init(&json, input, size);
  const char *key;
  uint64_t key_length, chapters_at = 0;
  int top_level_as_chapters = 0;
  if (hammer_json_enter_object(&json)) {
    while (hammer_json_next_key(&json, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "settings")) {
        read_settings(&json);
      } else if (hammer_json_key_is(key, key_length, "topLevelAsChapters")) {
        top_level_as_chapters = hammer_json_bool(&json);
      } else if (hammer_json_key_is(key, key_length, "chapters")) {
        hammer_json_peek(&json);
        chapters_at = json.at;
        hammer_json_skip(&json);
      } else {
        hammer_json_skip(&json);
      }
    }
  }

  int chapters_written = 0;
  saw_prose = 0;
  if (chapters_at) {
    json.at = chapters_at;
    if (hammer_json_enter_array(&json)) {
      while (hammer_json_next_item(&json)) {
        render_chapter(&json, top_level_as_chapters && settings.chapter_headings, &chapters_written);
      }
    }
  }
  if (json.error) {
    extism_error_set_buf_from_sz("Could not read the story from Hammer");
    return 1;
  }
  if (chapters_written == 0 && !saw_prose && size > 0) {
    // Chapters without `prose`: a Hammer too old for `input = "prose"` sent markdown only.
    HammerJson check;
    hammer_json_init(&check, input, size);
    check.at = chapters_at;
    if (chapters_at && hammer_json_enter_array(&check) && hammer_json_next_item(&check)) {
      extism_error_set_buf_from_sz("This plugin needs a newer version of Hammer");
      return 1;
    }
  }
  if (out.overflow) {
    extism_error_set_buf_from_sz("The story is too long to export as plain text");
    return 1;
  }

  out.length -= trailing_space(0, out.length);
  put_text("\n");
  extism_output_buf(out.data, out.length);
  return 0;
}
