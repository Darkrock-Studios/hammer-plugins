//! LanguageTool's side of a check: the settings, the batches paragraphs travel in, and the matches that
//! come back, turned into Hammer's diagnostics. Nothing here calls the host, so it all runs in native
//! tests.

use hammer::diagnose::{Diagnostic, Fix, Severity};
use hammer::server::languagetool::{CHECK_PATH, CheckRequest, CheckResponse, Level, Match};
use hammer::server::{ErrorKind, ServerError};
use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

const FREE: &str = "https://api.languagetool.org";
const PREMIUM: &str = "https://api.languagetoolplus.com";

/// Between paragraphs in a batch: LanguageTool reads a blank line as a paragraph break.
const SEPARATOR: &str = "\n\n";
const MAX_FIXES: usize = 5;

#[derive(Debug, PartialEq)]
pub enum Service {
    Free,
    Premium { username: String, api_key: String },
    /// A server's address, without `/v2/check`.
    Custom(String),
    /// The user's Hammer sync server, which forwards to its own LanguageTool.
    Hammer,
}

#[derive(Debug)]
pub struct Options {
    pub service: Service,
    pub picky: bool,
    pub style: bool,
}

fn text<'a>(settings: &'a Map<String, Value>, key: &str) -> &'a str {
    settings.get(key).and_then(Value::as_str).unwrap_or("").trim()
}

fn flag(settings: &Map<String, Value>, key: &str, default: bool) -> bool {
    settings.get(key).and_then(Value::as_bool).unwrap_or(default)
}

impl Options {
    pub fn from_settings(settings: &Map<String, Value>) -> Result<Options, String> {
        let service = match text(settings, "service") {
            "premium" => {
                let (username, api_key) = (text(settings, "username"), text(settings, "apiKey"));
                if username.is_empty() || api_key.is_empty() {
                    return Err("LanguageTool Premium needs your email and API key, in the plugin's settings".into());
                }
                Service::Premium { username: username.into(), api_key: api_key.into() }
            }
            "custom" => {
                let server = text(settings, "server").trim_end_matches('/');
                // Pasted from LanguageTool's docs, the address often carries the endpoint too.
                let server = server.strip_suffix("/v2/check").or_else(|| server.strip_suffix("/v2")).unwrap_or(server);
                if !(server.starts_with("http://") || server.starts_with("https://")) {
                    return Err("Your own server needs an http:// or https:// address, in the plugin's settings".into());
                }
                Service::Custom(server.into())
            }
            "hammer" => Service::Hammer,
            _ => Service::Free,
        };
        Ok(Options { service, picky: flag(settings, "picky", false), style: flag(settings, "style", true) })
    }

    /// The URL a check goes to, or None for [Service::Hammer], which Hammer addresses itself.
    pub fn endpoint(&self) -> Option<String> {
        let server = match &self.service {
            Service::Free => FREE,
            Service::Premium { .. } => PREMIUM,
            Service::Custom(server) => server,
            Service::Hammer => return None,
        };
        Some(format!("{server}{CHECK_PATH}"))
    }

    /// The most UTF-8 bytes of text to send at once, under the service's limit per request: 20 KB
    /// free, 60,000 characters with Premium. A Hammer server caps the encoded form at 100,000 bytes by
    /// default, and encoding can triple non-ASCII text.
    pub fn batch_limit(&self) -> usize {
        match self.service {
            Service::Free => 18_000,
            Service::Hammer => 30_000,
            _ => 50_000,
        }
    }

    /// What changes the matches a paragraph gets, for cache keys. Style is left out: it only filters.
    pub fn fingerprint(&self) -> String {
        let service = match self.service {
            Service::Free => "free",
            Service::Premium { .. } => "premium",
            Service::Custom(_) => "custom",
            Service::Hammer => "hammer",
        };
        format!("{service}:{}", if self.picky { "picky" } else { "default" })
    }

    pub fn request(&self, language: &str, text: &str) -> CheckRequest {
        let level = if self.picky { Level::Picky } else { Level::Default };
        CheckRequest { level, ..CheckRequest::new(text, language) }
    }

    /// The form for a server the plugin reaches itself, with Premium's credentials.
    pub fn form(&self, language: &str, text: &str) -> String {
        let form = self.request(language, text).form();
        match &self.service {
            Service::Premium { username, api_key } => {
                format!("{form}&{}", hammer::http::form(&[("username", username), ("apiKey", api_key)]))
            }
            _ => form,
        }
    }
}

/// What to tell the user when LanguageTool, reached directly, replies with a status other than success.
pub fn failure(service: &Service, status: u16, body: &[u8]) -> String {
    match status {
        429 => "LanguageTool's limit of requests is reached; checking resumes in a minute".into(),
        401 | 403 if matches!(service, Service::Premium { .. }) => {
            "LanguageTool refused the email or API key in the plugin's settings".into()
        }
        status => {
            let detail: String = String::from_utf8_lossy(body).chars().take(200).collect();
            format!("LanguageTool replied {status}: {detail}")
        }
    }
}

/// What to tell the user when a check through their Hammer server fails.
pub fn hammer_failure(error: &ServerError) -> String {
    match error.kind {
        ErrorKind::NotSignedIn => "Sign in to sync to check with your Hammer server".into(),
        ErrorKind::Unavailable => "Your Hammer server doesn't offer LanguageTool".into(),
        ErrorKind::Network => "Your Hammer server couldn't be reached".into(),
        ErrorKind::RateLimited => "Your Hammer server's limit of checks is reached; checking resumes in a minute".into(),
        ErrorKind::ServiceFailed => "Your Hammer server couldn't reach its LanguageTool; try again later".into(),
        // The sync server's own refusals carry a message meant for the user; LanguageTool's pass through
        // as the SDK's "The service replied ...".
        ErrorKind::Rejected => error.message.replacen("The service replied", "LanguageTool replied", 1),
        ErrorKind::Invalid => format!("Your Hammer server's reply couldn't be read: {}", error.message),
        _ => format!("Your Hammer server refused the check: {}", error.message),
    }
}

/// LanguageTool's language for a project's BCP 47 tag, which it takes as it is; with none, it guesses.
pub fn language(tag: Option<&str>) -> &str {
    tag.map(str::trim).filter(|it| !it.is_empty()).unwrap_or("auto")
}

/// One paragraph's place in a batch, in UTF-16 units, which LanguageTool's offsets count.
#[derive(Debug, PartialEq)]
pub struct Part {
    pub paragraph: usize,
    pub start: usize,
    pub length: usize,
}

#[derive(Debug, PartialEq)]
pub struct Batch {
    pub text: String,
    pub parts: Vec<Part>,
}

/// Joins paragraphs, each its index and text, into batches of at most `limit` bytes. A paragraph
/// over the limit goes alone, for the server to take or refuse.
pub fn batches(paragraphs: &[(usize, &str)], limit: usize) -> Vec<Batch> {
    let mut batches: Vec<Batch> = Vec::new();
    for &(paragraph, text) in paragraphs {
        let fits = batches.last().is_some_and(|it| it.text.len() + SEPARATOR.len() + text.len() <= limit);
        if !fits {
            batches.push(Batch { text: String::new(), parts: Vec::new() });
        }
        let batch = batches.last_mut().unwrap();
        if !batch.parts.is_empty() {
            batch.text.push_str(SEPARATOR);
        }
        let start = batch.text.encode_utf16().count();
        batch.text.push_str(text);
        batch.parts.push(Part { paragraph, start, length: text.encode_utf16().count() });
    }
    batches
}

/// Hammer's own spell check underlines these already.
fn is_spelling(found: &Match) -> bool {
    found.rule.issue_type == "misspelling" && found.rule.category.id == "TYPOS"
}

fn is_suggestion(found: &Match) -> bool {
    matches!(found.rule.issue_type.as_str(), "style" | "register" | "locale-violation")
        || matches!(found.rule.category.id.as_str(), "STYLE" | "REDUNDANCY")
}

/// A match in one paragraph, kept in the cache as it is, before the style setting filters it.
#[derive(Serialize, Deserialize, Debug, PartialEq)]
pub struct Issue {
    /// UTF-8 byte offsets into the paragraph.
    pub start: usize,
    pub end: usize,
    pub message: String,
    pub fixes: Vec<String>,
    pub suggestion: bool,
}

impl Issue {
    pub fn diagnostic(&self, paragraph: usize, text: &str) -> Diagnostic {
        let fixes = self
            .fixes
            .iter()
            .map(|fix| match fix.is_empty() {
                true => Fix::Labelled { replacement: String::new(), label: format!("Remove “{}”", text[self.start..self.end].trim()) },
                false => Fix::Replace(fix.clone()),
            })
            .collect();
        let severity = if self.suggestion { Severity::Suggestion } else { Severity::Error };
        Diagnostic { paragraph, start: self.start, end: self.end, message: self.message.clone(), fixes, severity }
    }
}

/// The UTF-8 byte offset at each UTF-16 unit of `text`, plus one for its end; None inside a character.
fn utf16_byte_offsets(text: &str) -> Vec<Option<usize>> {
    let mut offsets = Vec::with_capacity(text.len() + 1);
    for (at, char) in text.char_indices() {
        offsets.push(Some(at));
        offsets.extend(std::iter::repeat_n(None, char.len_utf16() - 1));
    }
    offsets.push(Some(text.len()));
    offsets
}

/// Each of the batch's parts' issues, in the order of its parts. A match that runs past its
/// paragraph, or starts or ends inside a character, is dropped.
pub fn issues(batch: &Batch, paragraphs: &[String], response: &CheckResponse) -> Vec<Vec<Issue>> {
    let mut found: Vec<Vec<Issue>> = batch.parts.iter().map(|_| Vec::new()).collect();
    let offsets: Vec<_> = batch.parts.iter().map(|part| utf16_byte_offsets(&paragraphs[part.paragraph])).collect();
    for found_match in response.matches.iter().filter(|it| !is_spelling(it)) {
        let Some(index) = batch.parts.iter().rposition(|part| part.start <= found_match.offset) else { continue };
        let part = &batch.parts[index];
        let (start, end) = (found_match.offset - part.start, found_match.offset - part.start + found_match.length);
        if end > part.length {
            continue;
        }
        let (Some(Some(start)), Some(Some(end))) = (offsets[index].get(start), offsets[index].get(end)) else { continue };
        found[index].push(Issue {
            start: *start,
            end: *end,
            message: found_match.message.clone(),
            fixes: found_match.replacements.iter().take(MAX_FIXES).map(|it| it.value.clone()).collect(),
            suggestion: is_suggestion(found_match),
        });
    }
    found
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn settings(value: Value) -> Map<String, Value> {
        value.as_object().unwrap().clone()
    }

    fn matched(offset: usize, length: usize, issue_type: &str, category: &str, fixes: &[&str]) -> Value {
        json!({
            "message": "Message",
            "replacements": fixes.iter().map(|it| json!({"value": it})).collect::<Vec<_>>(),
            "offset": offset,
            "length": length,
            "rule": {"id": "RULE", "issueType": issue_type, "category": {"id": category}},
        })
    }

    #[test]
    fn premium_needs_its_credentials() {
        let error = Options::from_settings(&settings(json!({"service": "premium", "username": "me@example.com"})));
        assert!(error.is_err());
        let options =
            Options::from_settings(&settings(json!({"service": "premium", "username": " me@example.com ", "apiKey": "key"}))).unwrap();
        assert_eq!(options.service, Service::Premium { username: "me@example.com".into(), api_key: "key".into() });
        assert_eq!(options.endpoint().unwrap(), "https://api.languagetoolplus.com/v2/check");
        assert_eq!(options.form("en-US", "Hi"), "text=Hi&language=en-US&username=me%40example.com&apiKey=key");
    }

    #[test]
    fn a_custom_server_takes_its_address_with_or_without_the_endpoint() {
        for server in ["http://localhost:8081", "http://localhost:8081/", "http://localhost:8081/v2/check", "http://localhost:8081/v2"] {
            let options =
                Options::from_settings(&settings(json!({"service": "custom", "server": server}))).unwrap();
            assert_eq!(options.endpoint().unwrap(), "http://localhost:8081/v2/check");
        }
        assert!(Options::from_settings(&settings(json!({"service": "custom", "server": "localhost:8081"}))).is_err());
    }

    #[test]
    fn the_hammer_server_needs_no_address_or_credentials() {
        let options = Options::from_settings(&settings(json!({"service": "hammer", "apiKey": "key"}))).unwrap();
        assert_eq!(options.service, Service::Hammer);
        assert_eq!(options.endpoint(), None);
        assert_eq!(options.form("en-US", "Hi"), "text=Hi&language=en-US");
        assert!(options.fingerprint().starts_with("hammer:"));
    }

    #[test]
    fn failures_name_the_hammer_server_when_it_is_in_the_way() {
        let error = |kind, message: &str| ServerError { kind, message: message.into() };
        assert!(hammer_failure(&error(ErrorKind::RateLimited, "")).starts_with("Your Hammer server's limit"));
        assert!(hammer_failure(&error(ErrorKind::ServiceFailed, "")).contains("couldn't reach its LanguageTool"));
        assert!(hammer_failure(&error(ErrorKind::NotSignedIn, "")).starts_with("Sign in"));
        assert_eq!(hammer_failure(&error(ErrorKind::Rejected, "The request is too large")), "The request is too large");
        assert_eq!(hammer_failure(&error(ErrorKind::Rejected, "The service replied 400: Bad language")), "LanguageTool replied 400: Bad language");
        assert!(hammer_failure(&error(ErrorKind::Invalid, "expected value")).contains("couldn't be read"));
        let premium = Service::Premium { username: "me@example.com".into(), api_key: "key".into() };
        assert!(failure(&Service::Free, 429, b"").starts_with("LanguageTool's limit"));
        assert!(failure(&premium, 403, b"").contains("API key"));
        assert_eq!(failure(&Service::Custom("http://localhost:8081".into()), 403, b"Forbidden"), "LanguageTool replied 403: Forbidden");
        assert_eq!(failure(&Service::Free, 400, b"Bad language"), "LanguageTool replied 400: Bad language");
    }

    #[test]
    fn missing_settings_mean_the_free_service() {
        let options = Options::from_settings(&Map::new()).unwrap();
        assert_eq!(options.service, Service::Free);
        assert!(!options.picky && options.style);
        assert_eq!(options.form("auto", "a b"), "text=a+b&language=auto");
    }

    #[test]
    fn no_language_lets_languagetool_guess() {
        assert_eq!(language(None), "auto");
        assert_eq!(language(Some(" ")), "auto");
        assert_eq!(language(Some("de-DE")), "de-DE");
    }

    #[test]
    fn batches_split_at_the_limit_and_count_utf16() {
        let batches = batches(&[(0, "One 😀."), (2, "Two."), (3, "Three is long.")], 16);
        assert_eq!(batches.len(), 2);
        assert_eq!(batches[0].text, "One 😀.\n\nTwo.");
        assert_eq!(batches[0].parts, vec![Part { paragraph: 0, start: 0, length: 7 }, Part { paragraph: 2, start: 9, length: 4 }]);
        assert_eq!(batches[1].parts, vec![Part { paragraph: 3, start: 0, length: 14 }]);
    }

    #[test]
    fn matches_land_in_their_paragraphs_as_byte_offsets() {
        let paragraphs = vec!["Héllo 😀 wrld.".to_string(), String::new(), "It are fine.".to_string()];
        let batch = &batches(&[(0, &paragraphs[0]), (2, &paragraphs[2])], 1000)[0];
        let second = batch.parts[1].start;
        let response: CheckResponse = serde_json::from_value(json!({"matches": [
            matched(9, 4, "misspelling", "TYPOS", &["world"]),
            matched(second + 3, 3, "grammar", "GRAMMAR", &["is", ""]),
            matched(second + 7, 4, "style", "REDUNDANCY", &[]),
            matched(13, 4, "grammar", "GRAMMAR", &[]),
            matched(7, 1, "grammar", "GRAMMAR", &[]),
        ]}))
        .unwrap();
        let found = issues(batch, &paragraphs, &response);
        assert!(found[0].is_empty(), "spelling, a match across the break, and one inside the emoji are dropped");
        assert_eq!(
            found[1],
            vec![
                Issue { start: 3, end: 6, message: "Message".into(), fixes: vec!["is".into(), "".into()], suggestion: false },
                Issue { start: 7, end: 11, message: "Message".into(), fixes: vec![], suggestion: true },
            ]
        );
    }

    #[test]
    fn an_emoji_before_a_match_shifts_its_bytes() {
        let paragraphs = vec!["😀 It are.".to_string()];
        let batch = &batches(&[(0, &paragraphs[0])], 1000)[0];
        let response: CheckResponse = serde_json::from_value(json!({"matches": [matched(6, 3, "grammar", "GRAMMAR", &["is"])]})).unwrap();
        let issue = &issues(batch, &paragraphs, &response)[0][0];
        assert_eq!(&paragraphs[0][issue.start..issue.end], "are");
    }

    #[test]
    fn an_empty_fix_is_labelled_as_removing_the_text() {
        let issue = Issue { start: 3, end: 7, message: "M".into(), fixes: vec!["".into()], suggestion: true };
        let diagnostic = serde_json::to_value(issue.diagnostic(4, "It really is.")).unwrap();
        assert_eq!(
            diagnostic,
            json!({"paragraph": 4, "start": 3, "end": 7, "message": "M", "fixes": [{"replacement": "", "label": "Remove “real”"}], "severity": "suggestion"})
        );
    }
}
