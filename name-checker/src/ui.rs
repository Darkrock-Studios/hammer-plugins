//! The plugin's words in the UI's language: English, French, German, Spanish, Italian, or Ukrainian,
//! and English for any other. Each line is written whole in each language, since word order and case
//! differ; counts take the language's plural forms.

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum Ui {
    En,
    Fr,
    De,
    Es,
    It,
    Uk,
}

use Ui::*;

/// French puts a no-break space before a colon.
const FR_COLON: &str = "\u{a0}:";

impl Ui {
    /// The language of a UI's BCP 47 tag.
    pub fn for_locale(locale: Option<&str>) -> Ui {
        match locale.and_then(|tag| crate::language_code(Some(tag))).as_deref() {
            Some("fr") => Fr,
            Some("de") => De,
            Some("es") => Es,
            Some("it") => It,
            Some("uk") => Uk,
            _ => En,
        }
    }

    /// Which of a noun's forms a count takes: one, a few, or many. Only Ukrainian has a few (2 to 4,
    /// but not 12 to 14); French counts 0 as one.
    fn form<'a>(self, n: usize, one: &'a str, few: &'a str, many: &'a str) -> &'a str {
        match self {
            Uk if n % 10 == 1 && n % 100 != 11 => one,
            Uk if (2..=4).contains(&(n % 10)) && !(12..=14).contains(&(n % 100)) => few,
            Uk => many,
            Fr if n <= 1 => one,
            _ if n == 1 => one,
            _ => many,
        }
    }

    // Checking text as it is written

    pub fn close_to_name(self, word: &str, name: &str) -> String {
        match self {
            En => format!("“{word}” is close to “{name}” in the encyclopedia"),
            Fr => format!("«\u{a0}{word}\u{a0}» est proche de «\u{a0}{name}\u{a0}» dans l’encyclopédie"),
            De => format!("„{word}“ ähnelt „{name}“ aus der Enzyklopädie"),
            Es => format!("«{word}» se parece a «{name}» de la enciclopedia"),
            It => format!("«{word}» è simile a «{name}» dell’enciclopedia"),
            Uk => format!("«{word}» схоже на «{name}» з енциклопедії"),
        }
    }

    pub fn close_to_names(self, word: &str) -> String {
        match self {
            En => format!("“{word}” is close to names in the encyclopedia"),
            Fr => format!("«\u{a0}{word}\u{a0}» est proche de noms de l’encyclopédie"),
            De => format!("„{word}“ ähnelt Namen aus der Enzyklopädie"),
            Es => format!("«{word}» se parece a nombres de la enciclopedia"),
            It => format!("«{word}» è simile a nomi dell’enciclopedia"),
            Uk => format!("«{word}» схоже на імена з енциклопедії"),
        }
    }

    pub fn capitalized(self, name: &str) -> String {
        match self {
            En => format!("“{name}” is capitalized in the encyclopedia"),
            Fr => format!("«\u{a0}{name}\u{a0}» prend une majuscule dans l’encyclopédie"),
            De => format!("„{name}“ wird in der Enzyklopädie großgeschrieben"),
            Es => format!("«{name}» va con mayúscula en la enciclopedia"),
            It => format!("«{name}» ha la maiuscola nell’enciclopedia"),
            Uk => format!("«{name}» в енциклопедії пишеться з великої літери"),
        }
    }

    // The report

    pub fn title(self) -> &'static str {
        match self {
            En => "# Names",
            Fr => "# Noms",
            De => "# Namen",
            Es => "# Nombres",
            It => "# Nomi",
            Uk => "# Імена",
        }
    }

    pub fn no_scenes(self) -> &'static str {
        match self {
            En => "No scenes yet.",
            Fr => "Aucune scène pour l’instant.",
            De => "Noch keine Szenen.",
            Es => "Todavía no hay escenas.",
            It => "Ancora nessuna scena.",
            Uk => "Сцен поки немає.",
        }
    }

    pub fn entries_heading(self) -> &'static str {
        match self {
            En => "## Encyclopedia entries",
            Fr => "## Entrées de l’encyclopédie",
            De => "## Einträge der Enzyklopädie",
            Es => "## Entradas de la enciclopedia",
            It => "## Voci dell’enciclopedia",
            Uk => "## Статті енциклопедії",
        }
    }

    pub fn no_entries(self) -> &'static str {
        match self {
            En => "The encyclopedia has no entries yet.",
            Fr => "L’encyclopédie n’a pas encore d’entrée.",
            De => "Die Enzyklopädie hat noch keine Einträge.",
            Es => "La enciclopedia aún no tiene entradas.",
            It => "L’enciclopedia non ha ancora voci.",
            Uk => "В енциклопедії поки немає статей.",
        }
    }

    pub fn misspellings_heading(self) -> &'static str {
        match self {
            En => "## Possible misspellings",
            Fr => "## Fautes possibles",
            De => "## Mögliche Tippfehler",
            Es => "## Posibles errores",
            It => "## Possibili refusi",
            Uk => "## Можливі помилки",
        }
    }

    pub fn unknown_heading(self) -> &'static str {
        match self {
            En => "## Names with no entry",
            Fr => "## Noms sans entrée",
            De => "## Namen ohne Eintrag",
            Es => "## Nombres sin entrada",
            It => "## Nomi senza voce",
            Uk => "## Імена без статті",
        }
    }

    fn mentions(self, n: usize) -> String {
        let noun = match self {
            En => self.form(n, "mention", "", "mentions"),
            Fr => self.form(n, "mention", "", "mentions"),
            De => self.form(n, "Erwähnung", "", "Erwähnungen"),
            Es => self.form(n, "mención", "", "menciones"),
            It => self.form(n, "menzione", "", "menzioni"),
            Uk => self.form(n, "згадка", "згадки", "згадок"),
        };
        format!("{n} {noun}")
    }

    fn times(self, n: usize) -> String {
        match self {
            En => format!("{n} {}", self.form(n, "time", "", "times")),
            Fr => format!("{n} fois"),
            De => format!("{n}-mal"),
            Es => format!("{n} {}", self.form(n, "vez", "", "veces")),
            It => format!("{n} {}", self.form(n, "volta", "", "volte")),
            Uk => format!("{n} {}", self.form(n, "раз", "рази", "разів")),
        }
    }

    /// "in 3 scenes", in the case a place takes.
    fn in_scenes(self, n: usize) -> String {
        match self {
            En => format!("in {n} {}", self.form(n, "scene", "", "scenes")),
            Fr => format!("dans {n} {}", self.form(n, "scène", "", "scènes")),
            De => format!("in {n} {}", self.form(n, "Szene", "", "Szenen")),
            Es => format!("en {n} {}", self.form(n, "escena", "", "escenas")),
            It => format!("in {n} {}", self.form(n, "scena", "", "scene")),
            Uk => format!("у {n} {}", self.form(n, "сцені", "сценах", "сценах")),
        }
    }

    /// "in" before a list of scenes.
    fn in_list(self, list: &str) -> String {
        match self {
            En | De | It => format!("in {list}"),
            Fr => format!("dans {list}"),
            Es => format!("en {list}"),
            Uk => format!("у {list}"),
        }
    }

    /// The last item of a list of scenes, standing for `n` more.
    pub fn more(self, n: usize) -> String {
        match self {
            En => format!("{n} more"),
            Fr => format!("{n} {}", self.form(n, "autre", "", "autres")),
            De => format!("{n} weiteren"),
            Es => format!("{n} más"),
            It if n == 1 => "un’altra".to_owned(),
            It => format!("altre {n}"),
            Uk => format!("ще {n}"),
        }
    }

    /// Items as a list: "a, b and c".
    pub fn and(self, items: &[String]) -> String {
        let and = match self {
            En => "and",
            Fr => "et",
            De => "und",
            Es => "y",
            It => "e",
            Uk => "і",
        };
        match items {
            [] => String::new(),
            [one] => one.clone(),
            [rest @ .., last] => format!("{} {} {last}", rest.join(", "), self.before(and, last)),
        }
    }

    /// Items as alternatives: "a or b".
    pub fn or(self, items: &[String]) -> String {
        let or = match self {
            En => "or",
            Fr => "ou",
            De => "oder",
            Es | It => "o",
            Uk => "або",
        };
        let mut out = items.first().cloned().unwrap_or_default();
        for item in items.iter().skip(1) {
            out.push_str(&format!(" {} {item}", self.before(or, item)));
        }
        out
    }

    /// Spanish's "y" and "o" become "e" and "u" before the sound they would run into: "Clara u Olga".
    fn before<'a>(self, word: &'a str, next: &str) -> &'a str {
        if self != Es {
            return word;
        }
        let next = next.trim_start_matches('*').to_lowercase();
        let starts = |sounds: &[&str]| sounds.iter().any(|sound| next.starts_with(sound));
        match word {
            "y" if starts(&["i", "í", "hi"]) && !starts(&["hia", "hie", "hio", "hiu"]) => "e",
            "o" if starts(&["o", "ó", "ho"]) => "u",
            _ => word,
        }
    }

    fn colon(self) -> &'static str {
        if self == Fr { FR_COLON } else { ":" }
    }

    /// An entry's line: how often it is mentioned, in how many scenes, and the first.
    pub fn entry_line(self, name: &str, mentions: usize, scenes: usize, first: &str) -> String {
        let (said, colon, in_scenes) = (self.mentions(mentions), self.colon(), self.in_scenes(scenes));
        let first = match self {
            En => format!("first in *{first}*"),
            Fr => format!("d’abord dans *{first}*"),
            De => format!("zuerst in *{first}*"),
            Es => format!("la primera en *{first}*"),
            It => format!("la prima in *{first}*"),
            Uk => format!("уперше в *{first}*"),
        };
        format!("- **{name}**{colon} {said} {in_scenes}, {first}")
    }

    pub fn not_mentioned(self, list: &str) -> String {
        let label = match self {
            En => "Not mentioned",
            Fr => "Sans mention",
            De => "Nicht erwähnt",
            Es => "Sin menciones",
            It => "Mai menzionate",
            Uk => "Без згадок",
        };
        format!("- {label}{} {list}", self.colon())
    }

    /// A likely misspelling's line: how often, where, and the names it is close to.
    pub fn slip_line(self, word: &str, count: usize, scene_list: &str, close: &str) -> String {
        let close_to = match self {
            En => "Close to",
            Fr => "Ressemble à",
            De => "Ähnlich wie",
            Es => "Parecido a",
            It => "Simile a",
            Uk => "Схоже на",
        };
        format!("- **{word}**{} {}, {}. {close_to} {close}.", self.colon(), self.times(count), self.in_list(scene_list))
    }

    /// A name with no entry: how often, and in how many scenes.
    pub fn unknown_line(self, word: &str, count: usize, scenes: usize) -> String {
        format!("- **{word}**{} {}, {}", self.colon(), self.times(count), self.in_scenes(scenes))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_locale_picks_its_language_and_any_other_english() {
        assert_eq!(Ui::for_locale(Some("fr-CA")), Fr);
        assert_eq!(Ui::for_locale(Some("uk_UA")), Uk);
        assert_eq!(Ui::for_locale(Some("pt-BR")), En);
        assert_eq!(Ui::for_locale(None), En);
    }

    #[test]
    fn counts_take_each_language_s_plural() {
        assert_eq!(En.entry_line("Ada", 1, 1, "Rain"), "- **Ada**: 1 mention in 1 scene, first in *Rain*");
        assert_eq!(En.unknown_line("Bo", 3, 2), "- **Bo**: 3 times, in 2 scenes");
        assert_eq!(Fr.unknown_line("Bo", 1, 2), "- **Bo**\u{a0}: 1 fois, dans 2 scènes");
        assert_eq!(De.unknown_line("Bo", 3, 1), "- **Bo**: 3-mal, in 1 Szene");
        let counted = |n| Uk.entry_line("Ада", n, n, "Дощ");
        assert_eq!(counted(1), "- **Ада**: 1 згадка у 1 сцені, уперше в *Дощ*");
        assert_eq!(counted(3), "- **Ада**: 3 згадки у 3 сценах, уперше в *Дощ*");
        assert_eq!(counted(12), "- **Ада**: 12 згадок у 12 сценах, уперше в *Дощ*");
        assert_eq!(counted(21), "- **Ада**: 21 згадка у 21 сцені, уперше в *Дощ*");
    }

    #[test]
    fn spanish_and_and_or_change_before_their_own_sound() {
        let names = |list: &[&str]| list.iter().map(|name| name.to_string()).collect::<Vec<_>>();
        assert_eq!(Es.or(&names(&["Clara", "Olga", "Ana"])), "Clara u Olga o Ana");
        assert_eq!(Es.and(&names(&["*Mar*", "*Isla*"])), "*Mar* e *Isla*");
        assert_eq!(Es.and(&names(&["*Mar*", "*Hielo*"])), "*Mar* y *Hielo*");
        assert_eq!(En.or(&names(&["Olga", "Ian"])), "Olga or Ian");
    }

    #[test]
    fn a_list_ends_with_and() {
        let items = ["*A*".to_owned(), "*B*".to_owned(), It.more(1)];
        assert_eq!(It.and(&items), "*A*, *B* e un’altra");
        assert_eq!(En.and(&items[..1]), "*A*");
    }
}
