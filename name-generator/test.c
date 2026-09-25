#include "name-generator.c"
#include "../include/hammer_test.h"

static char *names(uint64_t seed, Options options) {
  hammer_alloc_reset();
  HammerBuffer out = {0};
  generate(seed, options, &out);
  char *copy = malloc(out.length);
  memcpy(copy, out.data, out.length);
  return copy;
}

static int count_names(const char *list, int count) {
  int n = 0;
  for (int i = 0; i < count; i++, list += strlen(list) + 1) n += strlen(list) > 0;
  return n;
}

int main(void) {
  Options everyday = {EVERYDAY, ANY, 8, 1};
  char *a = names(42, everyday), *b = names(42, everyday), *c = names(43, everyday);
  HAMMER_CHECK(memcmp(a, b, 8) == 0);
  HAMMER_CHECK(strcmp(a, c) != 0 || strcmp(a + strlen(a) + 1, c + strlen(c) + 1) != 0);
  HAMMER_CHECK(count_names(a, 8) == 8);
  HAMMER_CHECK(strchr(a, ' ') != 0);

  Options single = {ELVISH, FEMININE, 3, 0};
  char *elves = names(7, single);
  HAMMER_CHECK(strchr(elves, ' ') == 0);

  Options norse = {NORSE, MASCULINE, 5, 1};
  char *norsemen = names(9, norse);
  const char *name = norsemen;
  for (int i = 0; i < 5; i++, name += strlen(name) + 1) {
    uint64_t n = strlen(name);
    HAMMER_CHECK(n > 4 && strcmp(name + n - 4, "sson") == 0);
  }

  hammer_alloc_reset();
  HammerBuffer reply = {0};
  put_reply(&reply, 42, (Options){EVERYDAY, ANY, 2, 1}, 1u, "Added");
  const char *json = hammer_terminated(&reply);
  HAMMER_CHECK(strstr(json, "(added)") != 0);
  HAMMER_CHECK(strstr(json, "\"id\":\"add:42:1:1\"") != 0);
  HAMMER_CHECK(strstr(json, "\"id\":\"add:42:0:") == 0);
  HAMMER_CHECK(strstr(json, "\"id\":\"more\"") != 0);
  HAMMER_CHECK(strstr(json, "\"message\":\"Added\"") != 0);

  const char *request_json =
      "{\"action\":\"names\",\"project\":\"The \\\"Storm\\\"\",\"settings\":{},"
      "\"input\":{\"style\":\"norse\",\"count\":3,\"surnames\":false},"
      "\"context\":{\"place\":\"entry\",\"id\":12},\"button\":\"more\",\"locale\":\"fr-CA\"}";
  HammerActionRequest request;
  HAMMER_CHECK(hammer_action_read(request_json, strlen(request_json), &request));
  HAMMER_CHECK(hammer_text_is(request.action, "names"));
  HAMMER_CHECK(request.project.length == 13 && memcmp(request.project.data, "The \\\"Storm\\\"", 13) == 0);
  HAMMER_CHECK(hammer_text_is(request.place, "entry") && request.has_id && request.id == 12);
  HAMMER_CHECK(hammer_text_is(request.button, "more") && hammer_text_is(request.locale, "fr-CA"));
  HAMMER_CHECK(hammer_text_is(hammer_input_string(&request, "style"), "norse"));
  HAMMER_CHECK(hammer_input_int(&request, "count", 8) == 3 && hammer_input_int(&request, "missing", 8) == 8);
  HAMMER_CHECK(hammer_input_bool(&request, "surnames", 1) == 0);
  HAMMER_CHECK(!hammer_action_read("{\"project\":\"x\"}", 15, &request));

  free(a), free(b), free(c), free(elves), free(norsemen);
  return hammer_test_summary();
}
