// Name generator: an interactive action that lists names in a chosen style, each with a button that
// adds it to the project's encyclopedia as a person. The list comes from a seed carried in the
// buttons' ids, so pressing one rebuilds the same list, marking the names already added.
//
// Each style is a resource, resources/styles/<style>.json, whose "lists" are named lists of words and
// whose templates build names from them: "feminine", "masculine", and optionally "feminine surname"
// and "masculine surname", such as "{start}{feminine end}" or "{father}sdottir". Adding a style is a
// file and an option in the manifest's style field.
#define HAMMER_IMPLEMENTATION
#define HAMMER_OUT_OF_MEMORY "Too many names to list"
#include "../include/hammer.h"
#include "../include/hammer_action.h"

#define MAX_NAMES 20
#define MAX_STYLES 16

enum { ANY, FEMININE, MASCULINE };

// ---------------------------------------------------------------------------------------------
// Randomness: xorshift64*, seeded once from WASI.

#ifndef HAMMER_NATIVE
__attribute__((import_module("wasi_snapshot_preview1"), import_name("random_get")))
extern int32_t wasi_random_get(uint8_t *buffer, uint32_t length);
#endif

static uint64_t fresh_seed(void) {
  uint64_t seed = 0;
#ifndef HAMMER_NATIVE
  wasi_random_get((uint8_t *)&seed, sizeof(seed));
#endif
  // Zero would stay zero; the ids carry seeds in 53 bits, which a JSON number holds exactly.
  seed &= (1ull << 53) - 1;
  return seed ? seed : 0x9E3779B97F4A7C15ull & ((1ull << 53) - 1);
}

static uint64_t state;

static uint32_t next(uint32_t below) {
  state ^= state >> 12;
  state ^= state << 25;
  state ^= state >> 27;
  return (uint32_t)((state * 0x2545F4914F6CDD1Dull) >> 33) % below;
}

// ---------------------------------------------------------------------------------------------
// Styles

typedef struct {
  char name[32];
  const char *text;
  uint64_t length;
  // The file's "lists" object.
  HammerText lists;
} Style;

// Styles read so far, kept between calls.
static Style styles[MAX_STYLES];
static int style_count;

// Reads a style file's text into style; 0 when it has no lists.
static int parse_style(Style *style, const char *text, uint64_t length) {
  HammerJson json;
  if (!hammer_json_find(text, length, "lists", &json) || hammer_json_peek(&json) != '{') return 0;
  uint64_t start = json.at;
  hammer_json_skip(&json);
  style->text = text;
  style->length = length;
  style->lists = (HammerText){text + start, json.at - start};
  return 1;
}

// The style named name, read from the package the first time it is asked for.
static const Style *load_style(HammerText name) {
  for (int i = 0; i < style_count; i++) {
    if (hammer_json_key_is(name.data, name.length, styles[i].name)) return &styles[i];
  }
  if (name.length == 0 || name.length >= sizeof(styles[0].name)) hammer_fail("There is no such name style");
  // On the stack, so hammer_alloc_keep does not keep it.
  char path[64];
  memcpy(path, "styles/", 7);
  memcpy(path + 7, name.data, name.length);
  memcpy(path + 7 + name.length, ".json", 6);
  static Style unkept;
  Style *style = style_count < MAX_STYLES ? &styles[style_count] : &unkept;
  uint64_t length;
  const char *text = hammer_resource_load(path, &length);
  if (!text) hammer_fail("There is no such name style");
  if (!parse_style(style, text, length)) hammer_fail("A name style has no lists");
  memcpy(style->name, name.data, name.length);
  style->name[name.length] = 0;
  if (style != &unkept) {
    hammer_alloc_keep();
    style_count++;
  }
  return style;
}

// Writes the raw JSON string contents decoded. Names are kept apart by NULs, so a word may not hold one.
static void put_unescaped(HammerBuffer *out, const char *raw, uint64_t raw_length) {
  char *decoded = hammer_must_alloc(raw_length + 1);
  uint64_t length = hammer_json_unescape(raw, raw_length, decoded);
  for (uint64_t i = 0; i < length; i++) {
    if (!decoded[i]) hammer_fail("A name style has a word with a NUL in it");
  }
  hammer_put(out, decoded, length);
}

// Writes a random word from the style's list named name.
static void put_pick(HammerBuffer *out, const Style *style, const char *name, uint64_t length) {
  char key[64];
  if (length >= sizeof(key)) hammer_fail("A name style names a list it does not have");
  memcpy(key, name, length);
  key[length] = 0;
  HammerJson json;
  uint32_t count = 0;
  if (!hammer_json_find(style->lists.data, style->lists.length, key, &json) || !hammer_json_enter_array(&json)) {
    hammer_fail("A name style names a list it does not have");
  }
  while (hammer_json_next_item(&json)) {
    if (hammer_json_peek(&json) != '"') hammer_fail("A name style has a word that is not a string");
    hammer_json_skip(&json);
    count++;
  }
  if (count == 0) hammer_fail("A name style has an empty list");
  uint32_t chosen = next(count);
  hammer_json_find(style->lists.data, style->lists.length, key, &json);
  hammer_json_enter_array(&json);
  for (uint32_t i = 0; hammer_json_next_item(&json); i++) {
    const char *raw;
    uint64_t raw_length;
    if (i == chosen && hammer_json_string(&json, &raw, &raw_length)) {
      put_unescaped(out, raw, raw_length);
      return;
    }
    hammer_json_skip(&json);
  }
}

// Writes the style's template named key with a word picked for each {list}; 0 when it has no such template.
static int put_template(HammerBuffer *out, const Style *style, const char *key) {
  HammerJson json;
  const char *raw;
  uint64_t raw_length;
  if (!hammer_json_find(style->text, style->length, key, &json) || !hammer_json_string(&json, &raw, &raw_length)) return 0;
  char *template = hammer_must_alloc(raw_length + 1);
  uint64_t length = hammer_json_unescape(raw, raw_length, template);
  for (uint64_t i = 0; i < length; i++) {
    if (template[i] != '{') {
      hammer_put_char(out, template[i]);
      continue;
    }
    uint64_t end = i + 1;
    while (end < length && template[end] != '}') end++;
    if (end == length) hammer_fail("A name style has a template with an unclosed {");
    put_pick(out, style, template + i + 1, end - i - 1);
    i = end;
  }
  return 1;
}

typedef struct {
  const Style *style;
  int gender;
  int count;
  int surnames;
} Options;

// The count names seeded by seed, each ending with a 0 in out, one after another.
static void generate(uint64_t seed, Options options, HammerBuffer *out) {
  state = seed;
  for (int i = 0; i < options.count; i++) {
    int female = options.gender == ANY ? (int)next(2) : options.gender == FEMININE;
    if (!put_template(out, options.style, female ? "feminine" : "masculine")) hammer_fail("A name style is missing a template");
    if (options.surnames) {
      uint64_t given = out->length;
      hammer_put_char(out, ' ');
      if (!put_template(out, options.style, female ? "feminine surname" : "masculine surname")) out->length = given;
    }
    hammer_put_char(out, 0);
  }
}

// ---------------------------------------------------------------------------------------------
// Export

// Reads a decimal number from text, advancing it past the digits.
static uint64_t read_number(const char **text) {
  uint64_t value = 0;
  while (**text >= '0' && **text <= '9') value = value * 10 + (uint64_t)(*(*text)++ - '0');
  return value;
}

// The reply for the names from seed, those in added marked, with a button to add each of the rest.
static void put_reply(HammerBuffer *out, uint64_t seed, Options options, uint32_t added, const char *message) {
  HammerBuffer names = {0};
  generate(seed, options, &names);

  HammerBuffer markdown = {0};
  hammer_put_text(&markdown, "Press a name to add it to the encyclopedia as a person.\n\n");
  HammerButton buttons[MAX_NAMES + 1];
  uint32_t count = 0;
  const char *name = names.data;
  for (int i = 0; i < options.count; i++) {
    hammer_put_text(&markdown, "- ");
    hammer_put_text(&markdown, name);
    if (added & (1u << i)) {
      hammer_put_text(&markdown, " (added)");
    } else {
      HammerBuffer id = {0};
      hammer_put_text(&id, "add:");
      hammer_put_int(&id, (int64_t)seed);
      hammer_put_char(&id, ':');
      hammer_put_int(&id, i);
      hammer_put_char(&id, ':');
      hammer_put_int(&id, added);
      buttons[count++] = (HammerButton){hammer_terminated(&id), name, 0};
    }
    hammer_put_char(&markdown, '\n');
    name += strlen(name) + 1;
  }
  buttons[count++] = (HammerButton){"more", "More names", 1};
  hammer_put_reply(out, markdown.data, markdown.length, buttons, count, message);
}

#ifndef HAMMER_NATIVE
// Adds name to the project as a person; returns 0, with failure set, when Hammer refuses.
static int add_entry(const char *project, uint64_t project_length, const char *name, const char **failure) {
  HammerBuffer request = {0};
  hammer_put_text(&request, "{\"project\":\"");
  hammer_put(&request, project, project_length);
  hammer_put_text(&request, "\",\"type\":\"person\",\"text\":\"\",\"name\":");
  hammer_put_json_string(&request, name, strlen(name));
  hammer_put_char(&request, '}');
  ExtismHandle handle = hammer_call("entry.create", hammer_terminated(&request));
  uint64_t size = extism_length(handle);
  char *reply = hammer_must_alloc(size + 1);
  extism_load_from_handle(handle, 0, reply, size);
  extism_free(handle);

  HammerJson json;
  hammer_json_init(&json, reply, size);
  const char *key;
  uint64_t key_length;
  if (hammer_json_enter_object(&json)) {
    while (hammer_json_next_key(&json, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "output")) return 1;
      if (hammer_json_key_is(key, key_length, "error") && hammer_json_enter_object(&json)) {
        while (hammer_json_next_key(&json, &key, &key_length)) {
          const char *raw;
          uint64_t raw_length;
          if (hammer_json_key_is(key, key_length, "message") && hammer_json_string(&json, &raw, &raw_length)) {
            char *message = hammer_must_alloc(raw_length + 1);
            message[hammer_json_unescape(raw, raw_length, message)] = 0;
            *failure = message;
          } else {
            hammer_json_skip(&json);
          }
        }
        return 0;
      }
      hammer_json_skip(&json);
    }
  }
  *failure = "Hammer sent a reply the name generator could not read";
  return 0;
}

int32_t EXTISM_EXPORTED_FUNCTION(action) {
  hammer_alloc_reset();
  uint64_t size = extism_input_length();
  char *input = hammer_must_alloc(size);
  extism_load_input(0, input, size);

  HammerActionRequest request;
  if (!hammer_action_read(input, size, &request)) hammer_fail("Hammer sent a request the name generator could not read");
  HammerText style = hammer_input_string(&request, "style");
  HammerText gender = hammer_input_string(&request, "gender");
  int64_t count = hammer_input_int(&request, "count", 8);
  Options options = {
    load_style(style.data ? style : (HammerText){"everyday", 8}),
    hammer_text_is(gender, "feminine") ? FEMININE : hammer_text_is(gender, "masculine") ? MASCULINE : ANY,
    count < 1 ? 1 : count > MAX_NAMES ? MAX_NAMES : (int)count,
    hammer_input_bool(&request, "surnames", 1),
  };
  const char *button = request.button.data;
  uint64_t button_length = request.button.length;

  HammerBuffer reply = {0};
  // A button's id is "add:<seed>:<index>:<names added so far, as bits>", or "more".
  if (button && button_length > 4 && hammer_json_key_is(button, 4, "add:")) {
    const char *at = button + 4;
    uint64_t seed = read_number(&at);
    at++;
    int index = (int)read_number(&at);
    at++;
    uint32_t added = (uint32_t)read_number(&at);
    if (index >= options.count) hammer_fail("That name is no longer in the list");

    HammerBuffer names = {0};
    generate(seed, options, &names);
    const char *name = names.data;
    for (int i = 0; i < index; i++) name += strlen(name) + 1;

    const char *failure = 0;
    HammerBuffer message = {0};
    if (add_entry(request.project.data, request.project.length, name, &failure)) {
      added |= 1u << index;
      hammer_put_text(&message, "Added ");
      hammer_put_text(&message, name);
      hammer_put_text(&message, " to the encyclopedia");
    } else {
      hammer_put_text(&message, failure);
    }
    put_reply(&reply, seed, options, added, hammer_terminated(&message));
  } else {
    put_reply(&reply, fresh_seed(), options, 0, 0);
  }
  extism_output_buf(reply.data, reply.length);
  return 0;
}
#endif // HAMMER_NATIVE
