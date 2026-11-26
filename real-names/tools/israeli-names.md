# Transliterating Israeli register names (Hebrew script) into their usual English spelling

The conventions `israeli-names.tsv` follows: each row is a sector, a kind (feminine, masculine, or surname), a name in
Hebrew letters, and its spelling, or "-" for a name left out. Keep new rows to these rules.

The input is names from Israel's Central Bureau of Statistics (CBS): given names from the population
register (by sex and sector) and surnames (2017), all in Hebrew script, unvocalised. CBS merged some
spelling variants and not others. They go into a name generator for novelists, so each name must be
spelled the way a reader from that community would recognise as the usual English spelling of that
name: how such a person most commonly writes their own name in English (passport, social media,
press), not a scholarly romanisation and not a translation into a different name.

Two communities, each with its own conventions:

## A. Jewish sector (Israeli Hebrew names, and the many foreign names immigrants brought)

Hebrew names: modern Israeli pronunciation, in the spelling Israelis usually use in English.
- Keep the Hebrew form, not the English Bible's: Yosef (not Joseph), Avraham, Yitzhak, Yaakov,
  Moshe, Shmuel, Eliyahu, Yehuda, Binyamin, Rivka, Yehudit, Elisheva, Shlomo, Yonatan, Yehoshua.
  Where the English spelling already matches the Hebrew sound, use it: David, Daniel, Rachel, Esther,
  Miriam, Sarah, Leah, Ruth, Naomi, Michael, Adam, Ariel, Gabriel. (Binyamin, not Benjamin.)
- Vowel-final ה is "a": Noa, Yehuda, Hana, Tova, Shoshana, Ahuva. Exceptions only where the English
  spelling is fixed: Sarah, Leah.
- Final י is "i": Itai, Shai, Hagai, Yishai, Yochai, Ido, Eli, Uri, Hili, Adi, Ori. Not "-ay".
- צ is "Tz": Tzvi, Tzipora, Tzion, Yitzhak, Tzachi, Matzliach. 
- ח and spirant כ: "H" at the start of a name (Haim, Hana, Hila, Hadas, Hagit, Hen, Hanoch,
  Hezi) and "ch" inside or at the end (Rachel, Michal, Menachem, Nachum, Yochai, Elchanan); נח is
  Noah. Use the clearly more usual form where a name has one (Yitzhak, Avraham with h).
- ע/א between vowels: double the vowel where Israelis do: Yaakov, Maayan, Yaara, Raanan, Shaul,
  Noa, Yael, Liel, Reut, Meir.
- ו as consonant "v" (Tova, Ahuva, Aviv, Yuval), vowel "o"/"u" as pronounced (Ofir, Uri, Yuval).
- ש "sh" (Shira, Moshe) and שׂ "s" (Sarah, Yisrael, Asaf).
- Hyphenate nothing in given names; compound given names keep their space if the input has one.
- Homographs: Hebrew is unvocalised, choose by sex and usage: אורי is Uri for boys, Ori for girls;
  אלה Ela; אור Or; חן Hen; עדי Adi; שי Shai; נועם Noam; גל Gal; טל Tal. Pick the reading most
  people with that spelling in that sex have.
- Foreign names, from immigrants and their children: their usual Latin spelling in the language they
  come from, not a Hebrew-letter-by-letter rendering:
  Russian/Ukrainian (Anna, Olga, Elena, Irina, Svetlana, Tatiana, Natalia, Yulia, Ekaterina, Ludmila,
  Alexander, Sergey, Dmitry, Vladimir, Andrey, Evgeny, Igor, Yuri, Alexey, Mikhail when spelled
  מיכאיל), French (Jeremy, Yohan, Kevin), English (Dylan, Jennifer, Steven),
  Amharic (Almaz, Tsega; only if you know the name), Yiddish (Feiga, Hersh; only if sure), Arabic-origin
  Mizrahi names (Mazal, Simcha, Zohra, Masoud).
- Russian names: use the common English/passport transliteration: Evgeny, Sergey, Andrey, Dmitry,
  Alexey, Yulia, Natalia, Tatiana, Ludmila, Ekaterina, Ksenia, Maxim, Artem, Kirill, Nikita,
  Vyacheslav, Valentina, Galina, Larisa, Anastasia, Polina, Daria, Sofia.

Jewish surnames: the spelling Israeli families most commonly use in English.
- Hebrew and Mizrahi: Cohen, Levi, Mizrahi, Peretz, Biton, Dahan, Avraham, Friedman, Malka,
  Azoulay, Amar, Ohayon, Hadad (Jewish: one d), Gabay, Ben-David, Ben-Haim, Ben-Shimon, Shapira,
  Elbaz, Amsalem, Asulin, Vaknin, Ben-Ami, Yosef, Hazan, Katz, Sharabi, Nissim, Suissa, Shalom,
  Turgeman, Edri, Alon, Ashkenazi, Halevi, Tzur, Ben-Hamo, Bar-Lev, Bar-On.
- "בן X" and "בר X" surnames: hyphenated, X capitalised: Ben-David, Ben-Shushan, Bar-Lev.
- Ashkenazi surnames from German/Yiddish: their original German-style spelling: Friedman, Weiss,
  Schwartz, Goldberg, Rosenberg, Grossman, Katz, Klein, Shapira (Israelis write Shapira), Rabinovich
  (-וביץ is usually -vich in Israeli English: Rabinovich, Abramovich; but use the German -witz
  where Israelis mostly write it so, as Horowitz), Weinberg,
  Rosenthal, Hirsch, Fischer, Kaplan, Segal, Landau, Feldman; Zilberman for זילברמן and Silberman
  for סילברמן (follow the Hebrew letter where Israelis do). Spell -מן as -man.
- Russian/Ukrainian/Georgian/Bukharan/Mountain-Jewish surnames: their usual English
  transliteration: Ivanov, Kogan (קוגן, not Cohen), Abramov, Aminov, Yakubov, Davidov, Mamistvalov,
  Pinhasov, Ilyaev, Rubinstein. -וב: -ov; -ייב/-ייף: -iev/-yev as the name usually has it.
- Ethiopian surnames (often a father's given name): your best common spelling if you know it
  (Tadesse, Mekonen, Berhanu, Alemu, Takele, Worku, Mengistu, Tesfaye); otherwise leave out.

## B. Arab sectors (Muslim, Christian-Arab, Druze): Palestinian/Levantine naming

CBS writes Arabic names in Hebrew letters nearly letter for letter, long vowels written:
ג' = ج j, ח' = خ kh, ע' = غ gh, ת' = ث th, ד' = ذ (th/d/z), ט' = ظ z, צ' = ض d, ח = ح h,
ע = ع, ק = ق q, ט = ط t, צ = ص s, א medial = long a, ו = w / long u / o, י = y / long i / e.
CBS sometimes drops the geresh (גאנם = Ghanem, חורי = Khoury), and writes one name several ways
(variants should get the same English spelling).

Use the usual Palestinian/Levantine English spelling, consistently:
- Given, masculine: Mohammad, Ahmad, Mahmoud, Mustafa, Yousef, Ibrahim, Omar, Ali, Hassan,
  Hussein, Khaled, Khalil, Saleh, Majed, Samer, Saeed, Adam, Amir, Karim, Rami, Wasim, Nizar,
  Jamal, Kamal, Jihad, Ghassan, Fadi, Basel, Tareq, Qasem, Qais, Raed, Iyad, Ayman, Osama, Bilal,
  Hamza, Anas, Moataz, Muhannad. Always Yousef (never Youssef/Yusuf/Joseph). Abdallah, Abdel Rahman, Abdel Karim,
  Abdel Aziz, Abdel Hamid, Abdel Latif (عبد + name: "Abdel" + space + name; عبد الله alone:
  Abdallah). "X al-Din" names: Salah al-Din, Alaa al-Din, Nour al-Din, Izz al-Din, Saif al-Din.
  Kh/Gh/Th/Sh digraphs; long u as "ou" (Mahmoud, Mansour, Nour, Yousef, Masoud, Mamoun);
  short i in Levantine as "e" where the usual spelling has it (Khaled, Majed, Saleh, Wael, Raed,
  Nael, Hatem, Hazem; but Hisham); long i "i" (Ibrahim, Karim, Samir, Wasim, Rafiq); ق as "q" (Qasem, Qais, Tareq, Rafiq, Sadiq, Shafiq, Qusai);
  final ي of nisba "i" (Ali, Hani, Sami, Fadi, Rami); ء: Alaa, Wafaa, Asmaa, Hanaa, Safaa,
  Bahaa; ة in female names "a" (Fatima, Amira, Rania, Samira, Nadia, Hiba, Ruba).
- Given, feminine: Fatima, Aya, Lana, Hala, Maryam, Nour, Rahaf, Rawan, Sara (one form only:
  Sara), Malak, Leen (לין), Jana, Layan, Aisha, Amina, Asmaa, Alaa, Iman, Amal, Hanan, Huda,
  Suha, Samah, Samar, Sanaa, Rasha, Reem, Rania, Raneen, Nisreen, Shireen, Yasmin,
  Haneen, Abeer, Areej, Doaa, Islam, Bayan, Bushra, Lama, Lamis, Maha, Manal,
  Mona, Nadia, Najwa, Nawal, Nihad, Rula, Ruba, Sabreen, Sahar, Salma, Sawsan, Wafaa,
  Zeinab, Zahra, Khadija, Hadeel, Dima, Dana, Tala, Masa, Mira, Yara.
  Doubled ee for long i where that is the usual Levantine spelling in the name: Haneen,
  Nisreen, Shireen, Abeer, Areej, Raneen, Sabreen, Reem, Leen. Pick one and keep it for every name
  of the same pattern (-ین names: -een).
- Christian-Arab names: many are Western or church forms; use those: George (ג'ורג'), Elias,
  Hanna (male, حنا), Boulos, Boutros, Michel (מישל), Jiries, Nicola, Anton, Maria, Rita, Rima, Nancy, Christine, Marie, Juliet, Sandy; Arabic names as above. Where
  the same Hebrew spelling is Muslim and Christian and should differ, give two rows (sector
  "christian" and "arab").
- Druze names: Levantine Arabic as above (Salman, Kamal, Nabih, Fadel, Wajdi, Rafiq, Amal, Wafaa).
- Surnames: Masarwa, Abu Ahmad, Khatib, Haj Yahya, Jabarin, Mahamid, Agbaria, Mahajna, Zoabi,
  Sawaed, Taha, Amash, Khoury, Haddad, Shehadeh, Elias, Matar, Bishara, Awad, Tannous, Abboud,
  Farah, Hanna, Halabi, Ghanem, Ibrahim, Amer, Azzam, Abu Saleh, Hassoun, Nasr al-Din, Fares,
  Safadi, Odeh, Salameh, Abu Mukh, Ganaim, Hijazi, Mansour, Al-Krenawi.
  - "אבו X": "Abu X", X capitalised, space: Abu Ahmad, Abu Saleh, Abu Rass, Abu Leil, Abu Hussein.
  - "אל" article fused to a surname (אלקרינאוי, אלחורי): "Al-X": Al-Krenawi, Al-Khoury.
    חאג' is Haj, alone or in a compound (Haj, Haj Yahya).
  - "X אל דין" / "X אלדין": "X al-Din" (Nasr al-Din, Salah al-Din, Fakhr al-Din, Zahr al-Din).
  - "עבד אל X" surname: "Abdel X" as given names.
  - Feminine ة at the end of a surname: the family's usual form, usually "-eh" in Levantine
    (Salameh, Shehadeh, Odeh; but Mahajna, Masarwa and Zahalka keep "-a" as the families
    write them). When unsure use "-a" after ח ע ק ר ו ט צ and "-eh" otherwise.
  - Nisba -י: "-i" (Zoabi, Halabi, Safadi, Tibi). -אוי: "-awi" (Krenawi, Barghouti is -ותי).
  - Long u in surnames "ou" (Mansour, Khoury, Abboud, Hassoun, Daoud, Tannous).
  - Doubled consonants (shadda) are invisible in Hebrew letters; restore them where the usual
    spelling has them: Haddad, Abboud, Azzam, Hassoun, Hassan, Najjar, Sabbagh, Khayyat, Fayyad.

## Rules for everyone

- Capitalise each word; hyphenated parts capitalised (Ben-David, Al-Krenawi); only "al-" inside "X
  al-Din" stays small.
- ASCII letters plus hyphen, space and, rarely, an apostrophe only where it is the established
  spelling (e.g. none in Arabic names: write "Saad", not "Sa'ad").
- Different Hebrew spellings of the same name get exactly the same English spelling, so they merge.
  Different names must not merge into one spelling unless they really are the same name.
- If you cannot tell what the name is, or cannot pick its usual English spelling with confidence,
  write "-" as its spelling: it will be left out. Better left out than guessed. Typos and junk
  (letters that cannot be a name, single letters, titles) also get "-".
- Do not translate meanings and do not replace a name with its English cognate (no Joseph for
  יוסף, no Jacob for יעקב, no Hannah for حنا Hanna).
