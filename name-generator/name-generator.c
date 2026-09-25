// Name generator: an interactive action that lists names in a chosen style, each with a button that
// adds it to the project's encyclopedia as a person. The list comes from a seed carried in the
// buttons' ids, so pressing one rebuilds the same list, marking the names already added.
#define HAMMER_IMPLEMENTATION
#define HAMMER_OUT_OF_MEMORY "Too many names to list"
#include "../include/hammer.h"
#include "../include/hammer_json.h"
#include "../include/hammer_out.h"

#define MAX_NAMES 20

enum { EVERYDAY, NORSE, ELVISH };
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

static const char *pick(const char *const *list) {
  uint32_t n = 0;
  while (list[n]) n++;
  return list[next(n)];
}

// ---------------------------------------------------------------------------------------------
// Styles

static const char *const EVERYDAY_FEMININE[] = {
  "Alice", "Beatrice", "Clara", "Daisy", "Eleanor", "Florence", "Grace", "Harriet", "Iris", "Josephine",
  "Katherine", "Lucy", "Margaret", "Nora", "Olive", "Philippa", "Rose", "Sarah", "Theodora", "Violet", 0,
};
static const char *const EVERYDAY_MASCULINE[] = {
  "Albert", "Benjamin", "Charles", "Daniel", "Edward", "Frederick", "George", "Henry", "Isaac", "James",
  "Leonard", "Matthew", "Nathaniel", "Oliver", "Peter", "Robert", "Samuel", "Thomas", "Walter", "William", 0,
};
static const char *const EVERYDAY_SURNAMES[] = {
  "Ashford", "Barlow", "Carter", "Dalton", "Ellison", "Fairfax", "Garner", "Hale", "Ingram", "Jarvis",
  "Kendall", "Lowell", "Mercer", "Norwood", "Oakley", "Prescott", "Radcliffe", "Sutton", "Thorne", "Whitlock", 0,
};

static const char *const NORSE_STARTS[] = {"Ar", "Bjor", "Ei", "Frey", "Gun", "Hal", "Ing", "Rag", "Sig", "Sol", "Thor", "Ulf", "Vig", 0};
static const char *const NORSE_FEMININE_ENDS[] = {"a", "dis", "hild", "run", "frid", "gerd", "ny", 0};
static const char *const NORSE_MASCULINE_ENDS[] = {"n", "mund", "rik", "vald", "stein", "geir", "leif", 0};
static const char *const NORSE_FATHERS[] = {"Arn", "Bjorn", "Erik", "Gunnar", "Halvard", "Ivar", "Olaf", "Sigurd", "Thorvald", "Ulf", 0};

static const char *const ELVISH_STARTS[] = {"Ae", "Cae", "El", "Fin", "Gal", "Ith", "Lae", "Mel", "Nim", "Syl", "Tha", "Ver", 0};
static const char *const ELVISH_MIDDLES[] = {"a", "e", "i", "ri", "la", "ne", "the", "wy", "", 0};
static const char *const ELVISH_FEMININE_ENDS[] = {"wen", "riel", "lith", "nor", "iel", "anna", 0};
static const char *const ELVISH_MASCULINE_ENDS[] = {"dor", "las", "thir", "ion", "mir", "ros", 0};
static const char *const ELVISH_SURNAMES[] = {"Moonbrook", "Silverleaf", "Starwhisper", "Dawnmere", "Willowmere", "Nightbloom", 0};

static void given_name(HammerBuffer *out, int style, int female) {
  if (style == EVERYDAY) {
    hammer_put_text(out, pick(female ? EVERYDAY_FEMININE : EVERYDAY_MASCULINE));
  } else if (style == NORSE) {
    hammer_put_text(out, pick(NORSE_STARTS));
    hammer_put_text(out, pick(female ? NORSE_FEMININE_ENDS : NORSE_MASCULINE_ENDS));
  } else {
    hammer_put_text(out, pick(ELVISH_STARTS));
    hammer_put_text(out, pick(ELVISH_MIDDLES));
    hammer_put_text(out, pick(female ? ELVISH_FEMININE_ENDS : ELVISH_MASCULINE_ENDS));
  }
}

// A Norse surname follows the given name's gender: a son or a daughter of the father named.
static void surname(HammerBuffer *out, int style, int female) {
  if (style == EVERYDAY) {
    hammer_put_text(out, pick(EVERYDAY_SURNAMES));
  } else if (style == NORSE) {
    hammer_put_text(out, pick(NORSE_FATHERS));
    hammer_put_text(out, female ? "sdottir" : "sson");
  } else {
    hammer_put_text(out, pick(ELVISH_SURNAMES));
  }
}

typedef struct {
  int style;
  int gender;
  int count;
  int surnames;
} Options;

// The count names seeded by seed, each ending with a 0 in out, one after another.
static void generate(uint64_t seed, Options options, HammerBuffer *out) {
  state = seed;
  for (int i = 0; i < options.count; i++) {
    int female = options.gender == ANY ? (int)next(2) : options.gender == FEMININE;
    given_name(out, options.style, female);
    if (options.surnames) {
      hammer_put_char(out, ' ');
      surname(out, options.style, female);
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

static void put_button(HammerBuffer *out, int *first, const char *id, const char *label) {
  hammer_put_text(out, *first ? "{\"id\":" : ",{\"id\":");
  *first = 0;
  hammer_put_json_string(out, id, strlen(id));
  hammer_put_text(out, ",\"label\":");
  hammer_put_json_string(out, label, strlen(label));
  hammer_put_char(out, '}');
}

// The reply for the names from seed, those in added marked, with a button to add each of the rest.
static void put_reply(HammerBuffer *out, uint64_t seed, Options options, uint32_t added, const char *message) {
  HammerBuffer names = {0};
  generate(seed, options, &names);

  HammerBuffer markdown = {0};
  hammer_put_text(&markdown, "Press a name to add it to the encyclopedia as a person.\n\n");
  HammerBuffer buttons = {0};
  int first = 1;
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
      put_button(&buttons, &first, hammer_terminated(&id), name);
    }
    hammer_put_char(&markdown, '\n');
    name += strlen(name) + 1;
  }
  put_button(&buttons, &first, "more", "More names");

  hammer_put_text(out, "{\"markdown\":");
  hammer_put_json_string(out, markdown.data, markdown.length);
  hammer_put_text(out, ",\"buttons\":[");
  hammer_put(out, buttons.data, buttons.length);
  hammer_put_char(out, ']');
  if (message) {
    hammer_put_text(out, ",\"message\":");
    hammer_put_json_string(out, message, strlen(message));
  }
  hammer_put_char(out, '}');
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

  Options options = {EVERYDAY, ANY, 8, 1};
  const char *key, *project = 0, *button = 0;
  uint64_t key_length, project_length = 0, button_length = 0;
  HammerJson json;
  hammer_json_init(&json, input, size);
  if (hammer_json_enter_object(&json)) {
    while (hammer_json_next_key(&json, &key, &key_length)) {
      if (hammer_json_key_is(key, key_length, "project")) {
        hammer_json_string(&json, &project, &project_length);
      } else if (hammer_json_key_is(key, key_length, "button") && hammer_json_peek(&json) == '"') {
        hammer_json_string(&json, &button, &button_length);
      } else if (hammer_json_key_is(key, key_length, "input") && hammer_json_enter_object(&json)) {
        while (hammer_json_next_key(&json, &key, &key_length)) {
          const char *value;
          uint64_t value_length;
          if (hammer_json_key_is(key, key_length, "style") && hammer_json_string(&json, &value, &value_length)) {
            options.style = hammer_json_key_is(value, value_length, "norse") ? NORSE
                            : hammer_json_key_is(value, value_length, "elvish") ? ELVISH : EVERYDAY;
          } else if (hammer_json_key_is(key, key_length, "gender") && hammer_json_string(&json, &value, &value_length)) {
            options.gender = hammer_json_key_is(value, value_length, "feminine") ? FEMININE
                             : hammer_json_key_is(value, value_length, "masculine") ? MASCULINE : ANY;
          } else if (hammer_json_key_is(key, key_length, "count")) {
            int64_t count = hammer_json_int(&json);
            options.count = count < 1 ? 1 : count > MAX_NAMES ? MAX_NAMES : (int)count;
          } else if (hammer_json_key_is(key, key_length, "surnames")) {
            options.surnames = hammer_json_bool(&json);
          } else {
            hammer_json_skip(&json);
          }
        }
      } else {
        hammer_json_skip(&json);
      }
    }
  }
  if (json.error || !project) hammer_fail("Hammer sent a request the name generator could not read");

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
    if (add_entry(project, project_length, name, &failure)) {
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
