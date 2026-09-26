// For mkdtemp and setenv, which languages_test uses to make a language of its own.
#define _POSIX_C_SOURCE 200809L
#include "simple-grammar.c"
#include "../include/hammer_test.h"

#define LQ "\xE2\x80\x9C"
#define RQ "\xE2\x80\x9D"
#define APOSTROPHE "\xE2\x80\x99"

static const char *const OPTIONAL_ON =
    "{\"wordiness\":true,\"redundancy\":true,\"repeatedMarks\":true,\"longSentences\":true,\"longSentenceWords\":10}";

static void repeated_words_test(void) {
  HAMMER_EXPECT(0, "Over the The hill.", "[the The]>the");
  HAMMER_EXPECT(0, "She had had enough, and said that that was that.", "");
  HAMMER_EXPECT_MESSAGES(0, "Café the the end.", "[the the] Repeated word");
}

static void articles_test(void) {
  HAMMER_EXPECT(0, "It was a apple. An dog barked.", "[a]>an; [An]>A");
  HAMMER_EXPECT(0, "A one-off, a unicorn, an hour, a European, an FBI agent, an x-ray.", "");
}

static void mistakes_test(void) {
  HAMMER_EXPECT(0, "Could of been worse, alot worse.", "[Could of]>Could have; [alot]>a lot");
  HAMMER_EXPECT(0, "It could-of worked, a could\tof aside.", "[could-of]>could have");
  HAMMER_EXPECT_MESSAGES(0, "It was per say fine.", "[per say] Did you mean " LQ "per se" RQ "?");
  HAMMER_EXPECT(0, "It's own fault. Their is time, and your welcome.",
                "[It's own]>Its own; [Their is]>There is; [your welcome]>you're welcome");
  HAMMER_EXPECT(0, "Your the best, and it" APOSTROPHE "s own reward.",
                "[Your the]>You" APOSTROPHE "re the; [it" APOSTROPHE "s own]>its own");
  HAMMER_EXPECT(0, "Is that your A-list pass, or your a.m. slot? Your a star.", "[Your a]>You're a");
  HAMMER_EXPECT(0, "These kind of things. I was suppose to go. We got use to it.",
                "[These kind of]>These kinds of; [was suppose to]>was supposed to; [got use to]>got used to");
  HAMMER_EXPECT(0, "A sneak peak, with baited breath, gave free reign: a mute point and a shoe-in.",
                "[sneak peak]>sneak peek; [baited breath]>bated breath; [free reign]>free rein; "
                "[mute point]>moot point; [shoe-in]>shoo-in");
  HAMMER_EXPECT(0, "Deep-seeded fears. For all intensive purposes, an old wise tale.",
                "[Deep-seeded]>Deep-seated; [For all intensive purposes]>For all intents and purposes; "
                "[old wise tale]>old wives' tale");
  HAMMER_EXPECT(0, "It peaked her interest, wet my appetite, and peaked in interest rates.",
                "[peaked her interest]>piqued her interest; [wet my appetite]>whet my appetite");
  HAMMER_EXPECT(0, "Put the shoe in the box, make due allowance, and make due with less.",
                "[make due with]>make do with");
}

static void than_test(void) {
  HAMMER_EXPECT(0, "She was taller and more then ready, better then me, more then that.",
                "[then]>than; [then]>than; [then]>than");
  HAMMER_EXPECT(0, "Things were better then. We were happier then and freer. More then than now.", "");
  HAMMER_EXPECT(0, "They hugged each other then left.", "");
}

static void lone_i_test(void) {
  HAMMER_EXPECT(0, "Then i left, and i" APOSTROPHE "m glad. That is, i.e. gone.", "[i]>I; [i]>I");
}

static void spacing_test(void) {
  HAMMER_EXPECT(0, "Too  far , and red,green.Then more.  Two spaces after a sentence are fine.",
                "[  ]> ; [ ,]>,; [,]>, ; [.]>. ");
}

static void sentence_starts_test(void) {
  HAMMER_EXPECT(0, "She left. a dog barked! then quiet\xE2\x80\xA6 e.g. this, etc. and... that.", "[a]>A; [t]>T");
  HAMMER_EXPECT(0, "She met Mr. smith and Dr. jones.", "");
}

static void doubled_punctuation_test(void) {
  HAMMER_EXPECT(0, "Yes,, no;; maybe.. So,. Stop!. Fine.", "[,,]>,; [;;]>;; [..]>.|...; [,.]>.|,; [!.]>!");
  HAMMER_EXPECT(0, "Wait... and then etc., and so on.", "");
  HAMMER_EXPECT(0, "It ended., then more.", "[.,]>,");
}

static void parentheses_test(void) {
  HAMMER_EXPECT_MESSAGES(0, "It was (mostly fine. Then it wasn't.", "[(] Unclosed parenthesis");
  HAMMER_EXPECT_MESSAGES(0, "It was fine) and done.", "[)] Unmatched parenthesis");
  HAMMER_EXPECT(0, "a) First, 2) second (see page 2), and a smile :) or a frown :(.", "");
}

static void dialogue_test(void) {
  HAMMER_EXPECT(0, "\"Wait.\" she said. \"Go.\" said Tom. \"Fine.\" he whispered.", "[.]>,; [.]>,; [.]>,");
  HAMMER_EXPECT(0, LQ "Wait." RQ " she said. " LQ "Stop!" RQ " she said.", "[.]>,");
  HAMMER_EXPECT(0, "\"Wait,\" She said. \"Go,\" Said Tom.", "[S]>s; [S]>s");
  HAMMER_EXPECT(0, "\"Wait.\" She ran. \"Fine.\" I said nothing. \"Wait,\" Tom said.", "");
  HAMMER_EXPECT(0, "\"Wait,\"she said, and said" LQ "Go" RQ "then left.",
                "[\"]>\" {Add a space}; [" LQ "]> " LQ "{Add a space}; [" RQ "]>" RQ " {Add a space}");
  HAMMER_EXPECT(0, "Don't say it" APOSTROPHE "s the dog" APOSTROPHE "s.", "");
  HAMMER_EXPECT(0, "He said,\"Wait here.\" \"I saw it on T.V.\" she said.", "");
}

static void optional_rules_test(void) {
  HAMMER_EXPECT(0, "In order to win, we got a free gift!!", "");
  HAMMER_EXPECT(OPTIONAL_ON, "In order to win, we got a free gift!!", "[In order to]>To; [free gift]>gift; [!!]>!");
  HAMMER_EXPECT_MESSAGES(OPTIONAL_ON, "Each and every one.", "[Each and every] Wordy; " LQ "Each" RQ " says the same");
  HAMMER_EXPECT(OPTIONAL_ON, "What?! Really?!? No!? Why??", "[?!?]>?!; [??]>?");
  HAMMER_EXPECT_MESSAGES(OPTIONAL_ON,
                         "One two three four five six seven eight nine ten eleven. Short one. Mr. J. R. Smith went "
                         "to the shop and bought one or two things.",
                         "[One two three four five six seven eight nine ten eleven] Long sentence: 11 words; "
                         "[Mr. J. R. Smith went to the shop and bought one or two things] Long sentence: 14 words");
}

static void settings_test(void) {
  const char *off = "{\"mistakes\":false,\"punctuation\":false,\"dialogue\":false}";
  HAMMER_EXPECT(off, "It was a apple.. could of, \"Wait.\" she said. then i left", "");
  HAMMER_EXPECT("{\"punctuation\":false}", "It was a apple.. then", "[a]>an");
}

static void languages_test(void) {
  char *reply = hammer_test_run("{\"language\":\"pt\",\"paragraphs\":[\"Ele a a disse.\"]}");
  HAMMER_CHECK(strcmp(reply, "{\"diagnostics\":[]}") == 0);
  free(reply);
  reply = hammer_test_run("{\"paragraphs\":[\"Fine.\",\"It a a dog.\"],\"language\":\"en-GB\"}");
  HAMMER_CHECK(strcmp(reply, "{\"diagnostics\":[{\"paragraph\":1,\"start\":3,\"end\":6,\"message\":\"Repeated word\",\"fixes\":[\"a\"]}]}") == 0);
  free(reply);
  reply = hammer_test_run("{\"language\":null,\"paragraphs\":[\"It a a dog.\"]}");
  HAMMER_CHECK(strstr(reply, "Repeated word") != 0);
  free(reply);

  // Another language's lists, saved with Windows line endings and written loosely: its phrases and
  // repeatable words apply, and English's own rules do not. A phrase's first word matches in any case.
  char directory[] = "/tmp/simple-grammar-XXXXXX";
  HAMMER_CHECK(mkdtemp(directory) != 0);
  char path[256];
  snprintf(path, sizeof(path), "%s/xx", directory);
  mkdir(path, 0700);
  snprintf(path, sizeof(path), "%s/xx/mistakes.txt", directory);
  FILE *file = fopen(path, "w");
  fputs("# A comment\r\n  FOO bar = baz\r\n\r\n", file);
  fclose(file);
  snprintf(path, sizeof(path), "%s/xx/repeatable.txt", directory);
  file = fopen(path, "w");
  fputs("a\n", file);
  fclose(file);
  char *english = strdup(getenv("HAMMER_RESOURCES"));
  setenv("HAMMER_RESOURCES", directory, 1);
  reply = hammer_test_run("{\"language\":\"xx-YY\",\"paragraphs\":[\"Foo bar, a a apple.\"]}");
  HAMMER_CHECK(strstr(reply, "\"start\":0,\"end\":7,") != 0 && strstr(reply, "[\"Baz\"]") != 0);
  HAMMER_CHECK(strstr(reply, "Repeated") == 0 && strstr(reply, "\"an\"") == 0);
  free(reply);
  setenv("HAMMER_RESOURCES", english, 1);
  free(english);
  remove(path);
  snprintf(path, sizeof(path), "%s/xx/mistakes.txt", directory);
  remove(path);
  snprintf(path, sizeof(path), "%s/xx", directory);
  remove(path);
  remove(directory);
  reply = hammer_test_run("{\"language\":\"en\",\"paragraphs\":[\"It a a apple.\"]}");
  HAMMER_CHECK(strstr(reply, "Repeated word") != 0 && strstr(reply, "\"an\"") != 0);
  free(reply);

  // A language with no lists leaves English's kept: with the resources gone, English still checks.
  english = strdup(getenv("HAMMER_RESOURCES"));
  setenv("HAMMER_RESOURCES", "/nonexistent", 1);
  reply = hammer_test_run("{\"language\":\"sv\",\"paragraphs\":[\"Det var var bra.\"]}");
  HAMMER_CHECK(strcmp(reply, "{\"diagnostics\":[]}") == 0);
  free(reply);
  reply = hammer_test_run("{\"language\":\"en\",\"paragraphs\":[\"It a a apple.\"]}");
  HAMMER_CHECK(strstr(reply, "Repeated word") != 0);
  free(reply);
  setenv("HAMMER_RESOURCES", english, 1);
  free(english);
}

#define GUILLEMET_OPEN "\xC2\xAB"
#define GUILLEMET_CLOSE "\xC2\xBB"
#define LOW_QUOTE "\xE2\x80\x9E"
#define NO_BREAK_SPACE "\xC2\xA0"
#define MODIFIER_APOSTROPHE "\xCA\xBC"

static void french_test(void) {
  HAMMER_EXPECT_IN("fr", 0, "Le arbre de école, je ai vu que il part jusque ici.",
                   "[Le arbre]>L'arbre; [de école]>d'école; [je ai]>j'ai; [que il]>qu'il; [jusque ici]>jusqu'ici");
  HAMMER_EXPECT_IN("fr", 0, "Je ai l" APOSTROPHE "air, et de Anne.", "[Je ai]>J" APOSTROPHE "ai; [de Anne]>d" APOSTROPHE "Anne");
  HAMMER_EXPECT_IN("fr", 0, "Prends-le avec toi. Puis-je entrer ? Le héros, le onze, à la une, de A à Z, le ONU.", "");
  HAMMER_EXPECT_IN("fr", 0, "Si il vient, ce est bien, quelque soit le temps, la où il faut.",
                   "[Si il]>S'il; [ce est]>c'est; [quelque soit]>quel que soit|quelle que soit; [la où]>là où");
  HAMMER_EXPECT_IN("fr", 0, "Nous nous sommes vus, vous vous êtes tus, et le le chat.", "[le le]>le");
  HAMMER_EXPECT_IN("fr", 0, "Il dit: viens ; puis , non : fin.",
                   "[:]>" NO_BREAK_SPACE ":{Add a space}; [ ,]>,");
  HAMMER_EXPECT_IN("fr", 0, GUILLEMET_OPEN " Viens. " GUILLEMET_CLOSE ", dit-il, puis" GUILLEMET_OPEN "non" GUILLEMET_CLOSE "fin.",
                   "[" GUILLEMET_OPEN "]> " GUILLEMET_OPEN "{Add a space}; [" GUILLEMET_CLOSE "]>" GUILLEMET_CLOSE
                   " {Add a space}; [.]>{Remove the full stop}");
  HAMMER_EXPECT_IN("fr", 0, "Il part. été chaud. Quoi ? rien, etc. et voilà.", "[é]>É");
}

static void german_test(void) {
  HAMMER_EXPECT_IN("de", 0, "Seid wann ist das selbe Haus leer? Er ist tod.",
                   "[Seid wann]>Seit wann; [das selbe]>dasselbe; [ist tod]>ist tot");
  HAMMER_EXPECT_IN("de", 0, "Die Frau, die die Tür öffnet, weil sie sie liebt. Das ist Tod.", "");
  HAMMER_EXPECT_IN("de", 0, "Er ist älter wie ich, im Laufe des weiteren Gesprächs. Ihr seid gestern gekommen.",
                   "[älter wie]>älter als");
  HAMMER_EXPECT_IN("de", 0, LOW_QUOTE "Warte." LQ ", sagte sie. " LOW_QUOTE "Gut" LQ ", sagte er.", "[.]>{Remove the full stop}");
  HAMMER_EXPECT_IN("de", 0, LOW_QUOTE "Warte" LQ "sagte sie" LOW_QUOTE "Gut" LQ ".",
                   "[" LQ "]>" LQ " {Add a space}; [" LOW_QUOTE "]> " LOW_QUOTE "{Add a space}");
  HAMMER_EXPECT_IN("de", 0, "Das kostet ca. zehn Euro, z. B. heute. äpfel fallen.", "[ä]>Ä");
  HAMMER_EXPECT_IN("de", OPTIONAL_ON, "Das ist bereits schon eine Zukunftsprognose.",
                   "[bereits schon]>bereits|schon; [Zukunftsprognose]>Prognose");
}

static void spanish_test(void) {
  HAMMER_EXPECT_IN("es", 0, "Me voy de el pueblo a el mar, pero no de El Salvador.", "[de el]>del|de él; [a el]>al|a él");
  HAMMER_EXPECT_IN("es", 0, "Esto a sido duro; e visto cosas. Haber si vienes. Ha echo mucho, de echo.",
                   "[a sido]>ha sido; [e visto]>he visto; [Haber si]>A ver si; [Ha echo]>Ha hecho; [de echo]>de hecho");
  HAMMER_EXPECT_IN("es", 0, "Vino. \xC2\xBFqu\xC3\xA9 haces? nada.", "[q]>Q");
  HAMMER_EXPECT_IN("es", 0, GUILLEMET_OPEN "Ven." GUILLEMET_CLOSE ", dijo.", "[.]>{Remove the full stop}");
}

static void italian_test(void) {
  HAMMER_EXPECT_IN("it", 0, "Ho un idea e un amica; c'è la faccio, tutt'ora, più migliore.",
                   "[un idea]>un'idea; [un amica]>un'amica; [c'è la faccio]>ce la faccio; [tutt'ora]>tuttora; "
                   "[più migliore]>migliore");
  HAMMER_EXPECT_IN("it", 0, "C" APOSTROPHE "è la faccio.", "[C" APOSTROPHE "è la faccio]>Ce la faccio");
  HAMMER_EXPECT_IN("it", 0, "Piano piano, quasi quasi, via via che passa, un'idea.", "");
}

static void ukrainian_test(void) {
  HAMMER_EXPECT_IN("uk", 0, "Він приймав участь у грі на протязі року.",
                   "[приймав участь]>брав участь; [на протязі року]>протягом року");
  HAMMER_EXPECT_IN("uk", 0, "Приймати участь важливо. По крайній мірі так.",
                   "[Приймати участь]>Брати участь; [По крайній мірі]>Принаймні");
  HAMMER_EXPECT_IN("uk", 0, "Так так. Добре. тепер іди.", "[Так так]>Так; [т]>Т");
  HAMMER_EXPECT_IN("uk", 0, GUILLEMET_OPEN "Іду." GUILLEMET_CLOSE ", \xE2\x80\x94 сказав він" GUILLEMET_OPEN "так" GUILLEMET_CLOSE "і пішов.",
                   "[" GUILLEMET_OPEN "]> " GUILLEMET_OPEN "{Add a space}; [" GUILLEMET_CLOSE "]>" GUILLEMET_CLOSE
                   " {Add a space}; [.]>{Remove the full stop}");
  HAMMER_EXPECT_IN("uk", OPTIONAL_ON, "Пам\xCA\xBCятний сувенір.", "[Пам\xCA\xBCятний сувенір]>Сувенір");
  HAMMER_EXPECT_IN("fr", 0, "Je ai vu l" MODIFIER_APOSTROPHE "arbre.", "[Je ai]>J" MODIFIER_APOSTROPHE "ai");
}

static void english_quote_comma_test(void) {
  HAMMER_EXPECT(0, "\"Wait.\", she said. " LQ "Go." RQ ", he said. \"U.S.\", it read.", "[.\",]>,\"; [." RQ ",]>," RQ);
}

static void ui_language_test(void) {
  for (uint64_t i = 0; i < sizeof(MESSAGES) / sizeof(MESSAGES[0]); i++) {
    for (int id = 0; id < MESSAGE_COUNT; id++) HAMMER_CHECK(MESSAGES[i].text[id] != 0);
  }
  char *reply = hammer_test_run("{\"language\":\"en\",\"locale\":\"fr-CA\",\"paragraphs\":[\"It a a dog, could of.\"]}");
  HAMMER_CHECK(strstr(reply, "\"Mot répété\"") != 0);
  HAMMER_CHECK(strstr(reply, "\"Vouliez-vous dire «" NO_BREAK_SPACE "could have" NO_BREAK_SPACE "»" NO_BREAK_SPACE "?\"") != 0);
  free(reply);
  reply = hammer_test_run("{\"language\":\"de\",\"locale\":\"uk\",\"settings\":{\"longSentences\":true,"
                          "\"longSentenceWords\":10},\"paragraphs\":[\"Eins zwei drei vier fünf sechs sieben acht neun zehn elf.\"]}");
  HAMMER_CHECK(strstr(reply, "\"Довге речення, слів: 11\"") != 0);
  free(reply);
  // An unknown UI language, and then none, get English.
  reply = hammer_test_run("{\"language\":\"en\",\"locale\":\"pt-BR\",\"paragraphs\":[\"It a a dog.\"]}");
  HAMMER_CHECK(strstr(reply, "\"Repeated word\"") != 0);
  free(reply);
  reply = hammer_test_run("{\"language\":\"en\",\"paragraphs\":[\"It a a dog.\"]}");
  HAMMER_CHECK(strstr(reply, "\"Repeated word\"") != 0);
  free(reply);
}

int main(void) {
  repeated_words_test();
  articles_test();
  mistakes_test();
  than_test();
  lone_i_test();
  spacing_test();
  sentence_starts_test();
  doubled_punctuation_test();
  parentheses_test();
  dialogue_test();
  optional_rules_test();
  settings_test();
  languages_test();
  french_test();
  german_test();
  spanish_test();
  italian_test();
  ukrainian_test();
  english_quote_comma_test();
  ui_language_test();
  return hammer_test_summary();
}
