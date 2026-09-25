#include "simple-grammar.c"
#include "../include/hammer_test.h"

static void repeated_words_test(void) {
  HAMMER_EXPECT(0, "Over the The hill.", "[the The]>the");
  HAMMER_EXPECT(0, "She had had enough, and said that that was that.", "");
  HAMMER_EXPECT_MESSAGES(0, "Café the the end.", "[the the] Repeated word");
}

static void articles_test(void) {
  HAMMER_EXPECT(0, "It was a apple. An dog barked.", "[a]>an; [An]>A");
  HAMMER_EXPECT(0, "A one-off, a unicorn, an hour, a European, an FBI agent, an x-ray.", "");
}

static void phrases_test(void) {
  HAMMER_EXPECT(0, "Could of been worse, alot worse.", "[Could of]>Could have; [alot]>a lot");
  HAMMER_EXPECT(0, "It could-of worked, a could\tof aside.", "[could-of]>could have");
  HAMMER_EXPECT_MESSAGES(0, "It was per say fine.", "[per say] Did you mean \xE2\x80\x9Cper se\xE2\x80\x9D?");
}

static void lone_i_test(void) {
  HAMMER_EXPECT(0, "Then i left, and i\xE2\x80\x99m glad. That is, i.e. gone.", "[i]>I; [i]>I");
}

static void spacing_test(void) {
  HAMMER_EXPECT(0, "Too  far , and red,green.Then more.  Two spaces after a sentence are fine.",
                "[  ]> ; [ ,]>,; [,]>, ; [.]>. ");
}

static void sentence_starts_test(void) {
  HAMMER_EXPECT(0, "She left. a dog barked! then quiet\xE2\x80\xA6 e.g. this, etc. and... that.", "[a]>A; [t]>T");
}

static void languages_test(void) {
  char *reply = hammer_test_run("{\"language\":\"fr\",\"paragraphs\":[\"Il a a dit.\"]}");
  HAMMER_CHECK(strcmp(reply, "{\"diagnostics\":[]}") == 0);
  free(reply);
  reply = hammer_test_run("{\"paragraphs\":[\"Fine.\",\"It a a dog.\"],\"language\":\"en-GB\"}");
  HAMMER_CHECK(strcmp(reply, "{\"diagnostics\":[{\"paragraph\":1,\"start\":3,\"end\":6,\"message\":\"Repeated word\",\"fixes\":[\"a\"]}]}") == 0);
  free(reply);
  reply = hammer_test_run("{\"language\":null,\"paragraphs\":[\"It a a dog.\"]}");
  HAMMER_CHECK(strstr(reply, "Repeated word") != 0);
  free(reply);
}

int main(void) {
  repeated_words_test();
  articles_test();
  phrases_test();
  lone_i_test();
  spacing_test();
  sentence_starts_test();
  languages_test();
  return hammer_test_summary();
}
