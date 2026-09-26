//! Name checker: keeps a story's names in line with its encyclopedia. As the text is written, it
//! underlines a word spelled close to an encyclopedia name, and a name written in lower case. From a
//! project's menu, it reports where each entry is mentioned, likely misspellings, and names used often
//! that have no entry.
//!
//! Only names the project language's word list does not know are checked, so a name that is also a
//! word ("Grace", "Harry") is left to spell check, and a word is never taken for a misspelled name.
//! For that reason it checks only languages it has a list for: `resources/words/<language>.txt`,
//! lower case and sorted, one word a line. English is SCOWL's (resources/words/SCOWL-COPYRIGHT);
//! French, German, Spanish, Italian, and Ukrainian are built by tools/word-lists.py (see
//! resources/words/NOTICE.txt).

use hammer::diagnose::{Diagnostic, Fix, Reply, Request};
use serde::Deserialize;
use serde_json::json;
use std::collections::{BTreeMap, HashMap};
use std::fmt::Write;
use std::sync::Mutex;
use ui::Ui;

mod ui;

const MIN_NAME_LENGTH: usize = 4;
const LONG_NAME_LENGTH: usize = 8;
const MIN_UNKNOWN_COUNT: usize = 2;
const UNKNOWN_SHOWN: usize = 30;
const SCENES_NAMED: usize = 3;

/// The word list file a language tag's primary subtag names, English's when there is no tag.
fn language_code(language: Option<&str>) -> Option<String> {
    let code = language.and_then(|tag| tag.split(['-', '_']).next()).unwrap_or("en").to_ascii_lowercase();
    (!code.is_empty() && code.bytes().all(|b| b.is_ascii_lowercase())).then_some(code)
}

/// `text`'s lines in byte order with `\n` endings, as [Words::contains] needs, however the file was
/// sorted and saved.
fn sorted(text: String) -> String {
    let mut lines: Vec<&str> = text.lines().collect();
    if !text.contains('\r') && lines.is_sorted() {
        return text;
    }
    lines.sort_unstable();
    lines.join("\n")
}

/// A language's word list: lower case, one word a line, in byte order.
#[derive(Clone, Copy)]
struct Words(&'static str);

impl Words {
    /// The list for `language`, read from the package the first time it is asked for, and kept.
    fn for_language(language: Option<&str>) -> Option<Words> {
        static LISTS: Mutex<BTreeMap<String, Option<&'static str>>> = Mutex::new(BTreeMap::new());
        let code = language_code(language)?;
        let mut lists = LISTS.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        let list = lists.entry(code).or_insert_with_key(|code| {
            let bytes = hammer::resource(&format!("words/{code}.txt"))?;
            let Ok(text) = String::from_utf8(bytes) else {
                hammer::log::warn(&format!("words/{code}.txt is not UTF-8, so {code} projects are not checked"));
                return None;
            };
            Some(Box::leak(sorted(text).into_boxed_str()))
        });
        list.map(Words)
    }

    /// Whether `word`, in lower case, is in the list, whose apostrophes are straight.
    fn contains(self, word: &str) -> bool {
        if word.contains('’') { self.search(&word.replace('’', "'")) } else { self.search(word) }
    }

    /// A binary search over the list's sorted lines.
    fn search(self, word: &str) -> bool {
        let bytes = self.0.as_bytes();
        let (mut low, mut high) = (0, bytes.len());
        while low < high {
            let middle = (low + high) / 2;
            let start = bytes[low..middle].iter().rposition(|&b| b == b'\n').map_or(low, |at| low + at + 1);
            let end = bytes[start..].iter().position(|&b| b == b'\n').map_or(bytes.len(), |at| start + at);
            match self.0[start..end].cmp(word) {
                std::cmp::Ordering::Equal => return true,
                std::cmp::Ordering::Less => low = end + 1,
                std::cmp::Ordering::Greater => high = start,
            }
        }
        false
    }
}

// ---------------------------------------------------------------------------------------------
// Words

/// The words of `text` with their byte offsets: letters, and apostrophes between letters.
fn words(text: &str) -> Vec<(usize, &str)> {
    let chars: Vec<(usize, char)> = text.char_indices().collect();
    let mut out = Vec::new();
    let mut i = 0;
    while i < chars.len() {
        if !chars[i].1.is_alphabetic() {
            i += 1;
            continue;
        }
        let start = chars[i].0;
        let mut j = i + 1;
        loop {
            if j < chars.len() && chars[j].1.is_alphabetic() {
                j += 1;
            } else if j + 1 < chars.len() && matches!(chars[j].1, '\'' | '’') && chars[j + 1].1.is_alphabetic() {
                j += 2;
            } else {
                break;
            }
        }
        let end = chars.get(j).map_or(text.len(), |c| c.0);
        out.push((start, &text[start..end]));
        i = j;
    }
    out
}

/// A word without its possessive "'s".
fn stem(word: &str) -> &str {
    word.strip_suffix("'s").or_else(|| word.strip_suffix("’s")).unwrap_or(word)
}

fn capitalized(word: &str) -> bool {
    word.chars().next().is_some_and(char::is_uppercase)
}

fn shouted(word: &str) -> bool {
    word.chars().count() > 1 && word.chars().all(|c| !c.is_alphabetic() || c.is_uppercase())
}

/// Whether the word at `start` begins a sentence: nothing before it but spaces and opening marks
/// back to the start of the line or the end of a sentence.
fn starts_sentence(line: &str, start: usize) -> bool {
    for c in line[..start].chars().rev() {
        if c.is_whitespace() || matches!(c, '"' | '“' | '‘' | '\'' | '(' | '[' | '*' | '_' | '—' | '–' | '-') {
            continue;
        }
        return matches!(c, '.' | '!' | '?' | '…');
    }
    true
}

/// Edits, a swap of neighbours counting as one, to turn `a` into `b`.
fn distance(a: &[char], b: &[char]) -> usize {
    let mut rows = vec![vec![0; b.len() + 1]; a.len() + 1];
    for (i, row) in rows.iter_mut().enumerate() {
        row[0] = i;
    }
    for j in 0..=b.len() {
        rows[0][j] = j;
    }
    for i in 1..=a.len() {
        for j in 1..=b.len() {
            let cost = usize::from(a[i - 1] != b[j - 1]);
            let mut best = (rows[i - 1][j] + 1).min(rows[i][j - 1] + 1).min(rows[i - 1][j - 1] + cost);
            if i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1] {
                best = best.min(rows[i - 2][j - 2] + 1);
            }
            rows[i][j] = best;
        }
    }
    rows[a.len()][b.len()]
}

// ---------------------------------------------------------------------------------------------
// The encyclopedia's names

#[derive(Deserialize)]
struct Entries {
    entries: Vec<Entry>,
}

#[derive(Deserialize)]
struct Entry {
    name: String,
    #[serde(default)]
    aliases: Vec<String>,
}

struct Names {
    /// Each entry's name, as shown.
    entries: Vec<String>,
    /// Each name and alias as its words, by its first word in lower case, with its entry.
    phrases: HashMap<String, Vec<(usize, Vec<String>)>>,
    /// The words of names the word list does not know, by lower case: as written, and the entries
    /// whose names use them.
    unknown_words: HashMap<String, (String, Vec<usize>)>,
    /// Those that start with a capital and are long enough to measure a near miss against.
    targets: Vec<(String, Vec<char>)>,
}

impl Names {
    /// Without a word list, every word of a name counts as a name.
    fn load(project: &str, list: Option<Words>) -> hammer::Result<Names> {
        let listed: Entries = hammer::call("entry.list", &json!({ "project": project }))?;
        let mut names = Names { entries: Vec::new(), phrases: HashMap::new(), unknown_words: HashMap::new(), targets: Vec::new() };
        for (index, entry) in listed.entries.into_iter().enumerate() {
            for name in std::iter::once(&entry.name).chain(&entry.aliases) {
                let parts: Vec<String> = words(name).into_iter().map(|(_, word)| word.to_owned()).collect();
                let Some(first) = parts.first() else { continue };
                names.phrases.entry(first.to_lowercase()).or_default().push((index, parts.clone()));
                for part in parts {
                    let lower = part.to_lowercase();
                    if list.is_some_and(|list| list.contains(&lower)) {
                        continue;
                    }
                    let (_, users) = names.unknown_words.entry(lower).or_insert_with(|| (part, Vec::new()));
                    if !users.contains(&index) {
                        users.push(index);
                    }
                }
            }
            names.entries.push(entry.name);
        }
        let mut targets: Vec<&String> = names
            .unknown_words
            .values()
            .map(|(written, _)| written)
            .filter(|written| capitalized(written) && written.chars().count() >= MIN_NAME_LENGTH)
            .collect();
        targets.sort();
        names.targets = targets.into_iter().map(|written| (written.clone(), written.to_lowercase().chars().collect())).collect();
        Ok(names)
    }

    /// The name word `lower` is the plural of, as in "the Grangers", with its entries.
    fn plural_of(&self, lower: &str) -> Option<&(String, Vec<usize>)> {
        ["es", "s"].iter().find_map(|ending| self.unknown_words.get(lower.strip_suffix(ending)?))
    }

    /// The names `word` is a slip of, closest first: one edit away, or two for a long name, and
    /// starting with the same letter, which a slip rarely changes.
    fn near_misses(&self, word: &str) -> Vec<&str> {
        let chars: Vec<char> = word.to_lowercase().chars().collect();
        let mut found: Vec<(usize, &str)> = self
            .targets
            .iter()
            .filter(|(_, target)| target.first() == chars.first())
            .filter_map(|(written, target)| {
                let limit = if target.len() >= LONG_NAME_LENGTH { 2 } else { 1 };
                if target.len().abs_diff(chars.len()) > limit {
                    return None;
                }
                let edits = distance(&chars, target);
                (edits > 0 && edits <= limit).then_some((edits, written.as_str()))
            })
            .collect();
        found.sort();
        found.into_iter().map(|(_, written)| written).collect()
    }

    /// The longest name or alias starting at `words[at]`, with only spaces between its words: its
    /// entry and how many words it takes.
    fn phrase_at(&self, line: &str, words: &[(usize, &str)], at: usize) -> Option<(usize, usize)> {
        let candidates = self.phrases.get(&stem(words[at].1).to_lowercase())?;
        candidates
            .iter()
            .filter(|(_, parts)| {
                at + parts.len() <= words.len()
                    && parts.iter().enumerate().all(|(k, part)| {
                        let (start, word) = words[at + k];
                        let spaced = k == 0 || {
                            let (before, previous) = words[at + k - 1];
                            line[before + previous.len()..start].chars().all(|c| c == ' ')
                        };
                        // Only the last word may be possessive.
                        let word = if k + 1 == parts.len() { stem(word) } else { word };
                        spaced && word == part
                    })
            })
            .max_by_key(|(_, parts)| parts.len())
            .map(|(entry, parts)| (*entry, parts.len()))
    }
}

/// What the text-wide checks make of one word: a near miss, a name in lower case, or neither.
enum Finding<'a> {
    NearMiss(Vec<&'a str>),
    LowerCase(&'a str),
}

fn finding<'a>(names: &'a Names, list: Words, word: &str) -> Option<Finding<'a>> {
    let lower = word.to_lowercase();
    if let Some((written, _)) = names.unknown_words.get(&lower) {
        return (word == lower && capitalized(written)).then_some(Finding::LowerCase(written));
    }
    if !capitalized(word) || shouted(word) || list.contains(&lower) || names.plural_of(&lower).is_some() {
        return None;
    }
    let near = names.near_misses(word);
    (!near.is_empty()).then_some(Finding::NearMiss(near))
}

// ---------------------------------------------------------------------------------------------
// Checking text as it is written

fn check(request: &Request) -> hammer::Result<Reply> {
    let mut reply = Reply::default();
    let Some(list) = Words::for_language(request.language.as_deref()) else { return Ok(reply) };
    if request.paragraphs.iter().all(|p| p.trim().is_empty()) {
        return Ok(reply);
    }
    let names = Names::load(&request.project, Some(list))?;
    let ui = Ui::for_locale(request.locale.as_deref());
    for (index, paragraph) in request.paragraphs.iter().enumerate() {
        for (start, word) in words(paragraph) {
            let word = stem(word);
            let (message, fixes) = match finding(&names, list, word) {
                Some(Finding::NearMiss(near)) => {
                    let message = match near.as_slice() {
                        [name] => ui.close_to_name(word, name),
                        _ => ui.close_to_names(word),
                    };
                    (message, near.iter().map(|name| Fix::Replace((*name).to_owned())).collect())
                }
                Some(Finding::LowerCase(name)) => {
                    (ui.capitalized(name), vec![Fix::Replace(name.to_owned())])
                }
                None => continue,
            };
            reply.diagnostics.push(Diagnostic {
                paragraph: index,
                start,
                end: start + word.len(),
                message,
                fixes,
                severity: Default::default(),
            });
        }
    }
    Ok(reply)
}

#[unsafe(no_mangle)]
pub extern "C" fn diagnose() -> i32 {
    hammer::run(|| {
        let request: Request = hammer::input_json()?;
        Ok(serde_json::to_vec(&check(&request)?)?)
    })
}

// ---------------------------------------------------------------------------------------------
// The report

#[derive(Deserialize)]
struct ActionRequest {
    project: String,
    #[serde(default)]
    locale: Option<String>,
}

#[derive(Deserialize)]
struct Info {
    language: Option<String>,
}

#[derive(Deserialize)]
struct Tree {
    nodes: Vec<Node>,
}

#[derive(Deserialize)]
struct Node {
    id: i64,
    name: String,
    kind: String,
    #[serde(default)]
    children: Vec<Node>,
}

#[derive(Deserialize)]
struct SceneText {
    markdown: String,
}

/// The story's scenes in order, groups opened up.
fn add_scenes<'a>(nodes: &'a [Node], scenes: &mut Vec<&'a Node>) {
    for node in nodes {
        if node.kind == "scene" {
            scenes.push(node);
        } else {
            add_scenes(&node.children, scenes);
        }
    }
}

/// How often something turns up, and in which scenes, by their order in the story.
#[derive(Default)]
struct Seen {
    count: usize,
    scenes: Vec<usize>,
    /// Whether once, at least, it was not the first word of a sentence, which a name need not be.
    mid_sentence: bool,
}

impl Seen {
    fn add(&mut self, scene: usize) {
        self.count += 1;
        if self.scenes.last() != Some(&scene) {
            self.scenes.push(scene);
        }
    }
}

/// `text` as it should read, with the characters that would format it escaped.
fn markdown_text(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for c in text.chars() {
        if matches!(c, '\\' | '*' | '_' | '`' | '[' | ']' | '<' | '>' | '#') {
            out.push('\\');
        }
        out.push(c);
    }
    out
}

/// The first few scenes by name, and how many more.
fn scene_list(ui: Ui, seen: &Seen, scenes: &[&Node]) -> String {
    let mut named: Vec<String> = seen.scenes.iter().take(SCENES_NAMED).map(|&at| format!("*{}*", markdown_text(&scenes[at].name))).collect();
    if seen.scenes.len() > SCENES_NAMED {
        named.push(ui.more(seen.scenes.len() - SCENES_NAMED));
    }
    ui.and(&named)
}

fn report() -> hammer::Result<String> {
    let request = hammer::input_json::<ActionRequest>().map_err(|_| "No project to report on")?;
    let (project, ui) = (request.project, Ui::for_locale(request.locale.as_deref()));
    let info: Info = hammer::call("project.info", &json!({ "project": project }))?;
    let list = Words::for_language(info.language.as_deref());
    let names = Names::load(&project, list)?;
    let tree: Tree = hammer::call("scene.tree", &json!({ "project": project }))?;
    let mut scenes = Vec::new();
    add_scenes(&tree.nodes, &mut scenes);

    let mut mentions: Vec<Seen> = names.entries.iter().map(|_| Seen::default()).collect();
    let mut slips: BTreeMap<String, (Seen, Vec<String>)> = BTreeMap::new();
    let mut unknown: BTreeMap<String, Seen> = BTreeMap::new();
    for (at, scene) in scenes.iter().enumerate() {
        let text: SceneText = hammer::call("scene.read", &json!({ "project": project, "id": scene.id }))?;
        for line in text.markdown.lines().filter(|line| !line.trim_start().starts_with('#')) {
            let found = words(line);
            let mut k = 0;
            while k < found.len() {
                if let Some((entry, length)) = names.phrase_at(line, &found, k) {
                    mentions[entry].add(at);
                    k += length;
                    continue;
                }
                let (start, word) = found[k];
                k += 1;
                let word = stem(word);
                let lower = word.to_lowercase();
                if let Some((written, users)) = names.unknown_words.get(&lower) {
                    // A word of one entry's name alone, such as a first name, is a mention of it.
                    if let [entry] = users.as_slice() {
                        if word == written {
                            mentions[*entry].add(at);
                        }
                    }
                    continue;
                }
                if let Some((written, users)) = names.plural_of(&lower) {
                    if let [entry] = users.as_slice() {
                        if word.starts_with(written.as_str()) {
                            mentions[*entry].add(at);
                        }
                    }
                    continue;
                }
                if list.is_none_or(|list| list.contains(&lower)) || !capitalized(word) || shouted(word) || word.chars().count() < 3 {
                    continue;
                }
                let near = names.near_misses(word);
                if !near.is_empty() {
                    let near = || (Seen::default(), near.iter().map(|&name| name.to_owned()).collect());
                    slips.entry(word.to_owned()).or_insert_with(near).0.add(at);
                    continue;
                }
                let seen = unknown.entry(word.to_owned()).or_default();
                seen.add(at);
                seen.mid_sentence |= !starts_sentence(line, start);
            }
        }
    }

    let mut out = format!("{}\n", ui.title());
    if scenes.is_empty() {
        write!(out, "\n{}", ui.no_scenes()).unwrap();
        return Ok(out);
    }

    write!(out, "\n{}\n\n", ui.entries_heading()).unwrap();
    if names.entries.is_empty() {
        writeln!(out, "{}", ui.no_entries()).unwrap();
    } else {
        let mut order: Vec<usize> = (0..names.entries.len()).filter(|&e| mentions[e].count > 0).collect();
        order.sort_by(|&a, &b| mentions[b].count.cmp(&mentions[a].count).then_with(|| names.entries[a].cmp(&names.entries[b])));
        for entry in order {
            let seen = &mentions[entry];
            let first = markdown_text(&scenes[seen.scenes[0]].name);
            writeln!(out, "{}", ui.entry_line(&markdown_text(&names.entries[entry]), seen.count, seen.scenes.len(), &first)).unwrap();
        }
        let mut unmentioned: Vec<&String> = names.entries.iter().zip(&mentions).filter(|(_, seen)| seen.count == 0).map(|(name, _)| name).collect();
        unmentioned.sort();
        if !unmentioned.is_empty() {
            let list: Vec<String> = unmentioned.iter().map(|name| format!("**{}**", markdown_text(name))).collect();
            writeln!(out, "{}", ui.not_mentioned(&list.join(", "))).unwrap();
        }
    }

    if list.is_some() {
        let mut listed: Vec<(&String, &(Seen, Vec<String>))> = slips.iter().collect();
        listed.sort_by(|a, b| b.1.0.count.cmp(&a.1.0.count).then_with(|| a.0.cmp(b.0)));
        if !listed.is_empty() {
            write!(out, "\n{}\n\n", ui.misspellings_heading()).unwrap();
            for (word, (seen, close)) in listed {
                let close: Vec<String> = close.iter().map(|name| markdown_text(name)).collect();
                let line = ui.slip_line(&markdown_text(word), seen.count, &scene_list(ui, seen, &scenes), &ui.or(&close));
                writeln!(out, "{line}").unwrap();
            }
        }

        let mut listed: Vec<(&String, &Seen)> =
            unknown.iter().filter(|(_, seen)| seen.mid_sentence && seen.count >= MIN_UNKNOWN_COUNT).collect();
        listed.sort_by(|a, b| b.1.count.cmp(&a.1.count).then_with(|| a.0.cmp(b.0)));
        if !listed.is_empty() {
            write!(out, "\n{}\n\n", ui.unknown_heading()).unwrap();
            for (word, seen) in listed.into_iter().take(UNKNOWN_SHOWN) {
                writeln!(out, "{}", ui.unknown_line(&markdown_text(word), seen.count, seen.scenes.len())).unwrap();
            }
        }
    }
    Ok(out.trim_end().to_owned())
}

#[unsafe(no_mangle)]
pub extern "C" fn action() -> i32 {
    hammer::run(report)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_word_list_is_searched() {
        let words = Words(include_str!("../resources/words/en.txt"));
        assert!(words.contains("grace") && words.contains("a") && words.contains("zucchini"));
        assert!(words.contains("don't") && words.contains("don’t"));
        assert!(!words.contains("hermione") && !words.contains("") && !words.contains("zzzz"));
    }

    #[test]
    fn each_language_knows_its_words_and_not_names() {
        let lists = [
            ("fr", include_str!("../resources/words/fr.txt"), "maison"),
            ("de", include_str!("../resources/words/de.txt"), "haus"),
            ("es", include_str!("../resources/words/es.txt"), "casa"),
            ("it", include_str!("../resources/words/it.txt"), "casa"),
            ("uk", include_str!("../resources/words/uk.txt"), "\u{431}\u{443}\u{434}\u{438}\u{43d}\u{43e}\u{43a}"),
        ];
        for (language, text, word) in lists {
            assert_eq!(sorted(text.to_owned()), text, "{language} is not in byte order");
            let words = Words(text);
            assert!(words.contains(word), "{language} lacks {word}");
            for name in ["hermione", "harry", "gandalf"] {
                assert!(!words.contains(name), "{language} has {name}");
            }
        }
    }

    #[test]
    fn a_list_is_put_in_byte_order_with_plain_line_endings() {
        let words = Words(Box::leak(sorted("zebra\r\n\u{e9}t\u{e9}\r\napple\r\n".to_owned()).into_boxed_str()));
        assert!(words.contains("apple") && words.contains("zebra") && words.contains("\u{e9}t\u{e9}"));
        assert_eq!(sorted("a\nb".to_owned()), "a\nb");
    }

    #[test]
    fn a_language_tag_names_its_list_by_its_language() {
        assert_eq!(language_code(Some("en-GB")).as_deref(), Some("en"));
        assert_eq!(language_code(Some("FR_ca")).as_deref(), Some("fr"));
        assert_eq!(language_code(None).as_deref(), Some("en"));
        assert_eq!(language_code(Some("")), None);
        assert_eq!(language_code(Some("../x")), None);
    }

    #[test]
    fn a_swap_is_one_edit() {
        let chars = |s: &str| s.chars().collect::<Vec<_>>();
        assert_eq!(distance(&chars("hermoine"), &chars("hermione")), 1);
        assert_eq!(distance(&chars("kaelan"), &chars("kaelen")), 1);
        assert_eq!(distance(&chars("brenick"), &chars("brennick")), 1);
    }

    #[test]
    fn words_keep_apostrophes_between_letters() {
        let found: Vec<&str> = words("“Kaelen’s sword,” O'Brien said.").into_iter().map(|(_, w)| w).collect();
        assert_eq!(found, ["Kaelen’s", "sword", "O'Brien", "said"]);
        assert_eq!(stem("Kaelen’s"), "Kaelen");
    }

    #[test]
    fn a_sentence_starts_after_a_full_stop_or_an_opening_quote() {
        let line = "He left. “Brennick waited,” said Brennick.";
        assert!(starts_sentence(line, 0));
        assert!(starts_sentence(line, line.find("Brennick").unwrap()));
        assert!(!starts_sentence(line, line.rfind("Brennick").unwrap()));
    }
}
