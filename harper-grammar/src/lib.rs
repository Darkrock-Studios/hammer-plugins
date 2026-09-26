//! Harper grammar: Harper's English rules over the text being written. Spelling is left to
//! Hammer's own spell check, and a project in another language gets nothing.

use std::cell::RefCell;

use hammer::diagnose::{Diagnostic, Fix, Reply, Request, Severity};
use harper_core::linting::{Lint, LintGroup, LintKind, Linter, Suggestion};
use harper_core::spell::FstDictionary;
use harper_core::{Dialect, Document, TokenStringExt};

#[global_allocator]
static ALLOCATOR: hammer::ZeroingAllocator = hammer::ZeroingAllocator;

thread_local! {
    // Built on first use and kept, since the module stays loaded between calls.
    static LINTER: RefCell<Option<(Dialect, LintGroup)>> = const { RefCell::new(None) };
}

/// The dialect for a BCP 47 tag, or none for a language other than English. No tag means American.
fn dialect(language: Option<&str>) -> Option<Dialect> {
    let Some(tag) = language else { return Some(Dialect::American) };
    let mut parts = tag.split(['-', '_']);
    if !parts.next()?.eq_ignore_ascii_case("en") {
        return None;
    }
    Some(match parts.next().map(str::to_ascii_uppercase).as_deref() {
        Some("GB" | "UK" | "IE" | "NZ") => Dialect::British,
        Some("AU") => Dialect::Australian,
        Some("CA") => Dialect::Canadian,
        Some("IN") => Dialect::Indian,
        _ => Dialect::American,
    })
}

fn new_linter(dialect: Dialect) -> LintGroup {
    let mut linter = LintGroup::new_curated(FstDictionary::curated(), dialect);
    linter.config.set_rule_enabled("SpellCheck", false);
    linter
}

/// Builds the American linter ahead of time: build.sh runs this under Wizer and saves the memory it
/// leaves into the module, so Hammer loads it built rather than spending half a minute on it.
#[unsafe(export_name = "wizer.initialize")]
pub extern "C" fn initialize() {
    LINTER.with_borrow_mut(|cached| *cached = Some((Dialect::American, new_linter(Dialect::American))));
    // Parsing uses the curated dictionary too; this builds anything it keeps apart from the linter.
    drop(Document::new_plain_english_curated("Warm up."));
}

/// The setting that turns a kind of lint on or off. Harper's kinds are too fine to offer one by one.
fn category(kind: LintKind) -> &'static str {
    match kind {
        LintKind::Agreement
        | LintKind::Grammar
        | LintKind::WordOrder
        | LintKind::BoundaryError
        | LintKind::Repetition
        | LintKind::Miscellaneous => "grammar",
        LintKind::WordChoice
        | LintKind::Usage
        | LintKind::Malapropism
        | LintKind::Eggcorn
        | LintKind::Nonstandard
        | LintKind::Regionalism
        | LintKind::Typo => "wordChoice",
        LintKind::Punctuation | LintKind::Capitalization | LintKind::Formatting => "punctuation",
        _ => "style",
    }
}

/// Style lints are suggestions: underlined more quietly than mistakes.
fn severity(kind: LintKind) -> Severity {
    match kind {
        LintKind::Style | LintKind::Readability | LintKind::Enhancement | LintKind::Redundancy => Severity::Suggestion,
        _ => Severity::Error,
    }
}

/// Gives the lint [harper_core::remove_overlaps] keeps for a span, the one of highest priority, the
/// suggestions of the others on exactly the same words, which it would drop with their lints.
fn merge_same_span_suggestions(lints: &mut [Lint]) {
    lints.sort_by_key(|lint| (lint.span.start, lint.span.end, lint.priority));
    let mut first = 0;
    while first < lints.len() {
        let mut next = first + 1;
        while next < lints.len() && lints[next].span == lints[first].span {
            for suggestion in lints[next].suggestions.clone() {
                if !lints[first].suggestions.contains(&suggestion) {
                    lints[first].suggestions.push(suggestion);
                }
            }
            next += 1;
        }
        first = next;
    }
}

/// Whether the lint spans a whole sentence, first word to last token, as a long sentence's does.
fn covers_sentence(document: &Document, lint: &Lint) -> bool {
    document.iter_sentences().any(|sentence| {
        let (Some(first), Some(last)) = (sentence.first_word(), sentence.last()) else { return false };
        lint.span.start == first.span.start && lint.span.end == last.span.end
    })
}

/// A toggle's value; on when missing, as every toggle is by default.
fn enabled(request: &Request, key: &str) -> bool {
    request.settings.get(key).and_then(|value| value.as_bool()).unwrap_or(true)
}

fn check(request: &Request, dialect: Dialect) -> Reply {
    let mut reply = Reply::default();
    LINTER.with_borrow_mut(|cached| {
        if cached.as_ref().is_none_or(|(built, _)| *built != dialect) {
            *cached = Some((dialect, new_linter(dialect)));
        }
        let (_, linter) = cached.as_mut().unwrap();
        linter.config.set_rule_enabled("LongSentences", enabled(request, "longSentences"));
        for (index, paragraph) in request.paragraphs.iter().enumerate() {
            if paragraph.trim().is_empty() {
                continue;
            }
            let document = Document::new_plain_english_curated(paragraph);
            let chars = document.get_source();
            let offsets = hammer::char_byte_offsets(paragraph);
            // Filtered first, so a lint of a category turned off never hides one turned on.
            let mut lints: Vec<Lint> =
                linter.lint(&document).into_iter().filter(|lint| enabled(request, category(lint.lint_kind))).collect();
            merge_same_span_suggestions(&mut lints);
            // A long sentence's lint covers the sentence, so it would hide every other issue in it.
            let (whole, mut lints): (Vec<Lint>, Vec<Lint>) =
                lints.into_iter().partition(|lint| lint.lint_kind == LintKind::Readability && covers_sentence(&document, lint));
            harper_core::remove_overlaps(&mut lints);
            lints.extend(whole);
            for lint in lints {
                let (Some(&start), Some(&end)) = (offsets.get(lint.span.start), offsets.get(lint.span.end)) else {
                    continue;
                };
                let original: String = chars[lint.span.start..lint.span.end].iter().collect();
                let fixes = lint
                    .suggestions
                    .iter()
                    .map(|suggestion| match suggestion {
                        Suggestion::ReplaceWith(with) => Fix::Replace(with.iter().collect()),
                        Suggestion::InsertAfter(after) => {
                            let inserted: String = after.iter().collect();
                            Fix::Labelled { replacement: original.clone() + &inserted, label: format!("Insert “{}”", inserted.trim()) }
                        }
                        Suggestion::Remove => Fix::Labelled { replacement: String::new(), label: format!("Remove “{}”", original.trim()) },
                    })
                    .collect();
                let severity = severity(lint.lint_kind);
                reply.diagnostics.push(Diagnostic { paragraph: index, start, end, message: lint.message, fixes, severity });
            }
        }
    });
    reply
}

#[unsafe(no_mangle)]
pub extern "C" fn diagnose() -> i32 {
    hammer::run(|| {
        let request: Request = hammer::input_json()?;
        let reply = match dialect(request.language.as_deref()) {
            Some(dialect) => check(&request, dialect),
            None => Reply::default(),
        };
        Ok(serde_json::to_vec(&reply)?)
    })
}
