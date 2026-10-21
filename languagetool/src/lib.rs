//! LanguageTool: grammar, punctuation, and style from a LanguageTool server, the public one, Premium, or
//! the user's own. Each paragraph's matches are cached by its text, so a scene opened again is not
//! sent again, which matters under the free service's limit of 20 requests a minute.

mod check;

use check::{Issue, Options, Response};
use hammer::diagnose::{Reply, Request};

/// In every cache key, so a change to what is cached leaves the old values unread.
const CACHE_VERSION: u32 = 1;

/// FNV-1a: cache keys carry a paragraph's hash rather than its text.
fn hash(text: &str) -> u64 {
    text.bytes().fold(0xcbf29ce484222325, |hash, byte| (hash ^ byte as u64).wrapping_mul(0x100000001b3))
}

fn cache_key(options: &Options, language: &str, paragraph: &str) -> String {
    format!("v{CACHE_VERSION}:{}:{language}:{:016x}:{}", options.fingerprint(), hash(paragraph), paragraph.len())
}

fn send(options: &Options, language: &str, text: &str) -> hammer::Result<Response> {
    let body = options.form(language, text);
    let headers = [("Content-Type", "application/x-www-form-urlencoded"), ("Accept", "application/json")];
    let response = hammer::http::request("POST", &options.endpoint(), &headers, Some(body.as_bytes()))?;
    match response.status {
        _ if response.is_success() => Ok(serde_json::from_slice(&response.body)?),
        429 => Err("LanguageTool's limit of requests is reached; checking resumes in a minute".into()),
        401 | 403 => Err("LanguageTool refused the email or API key in the plugin's settings".into()),
        status => {
            let detail: String = String::from_utf8_lossy(&response.body).chars().take(200).collect();
            Err(format!("LanguageTool replied {status}: {detail}").into())
        }
    }
}

fn check(request: &Request) -> hammer::Result<Reply> {
    let options = Options::from_settings(&request.settings)?;
    let language = check::language(request.language.as_deref());
    let mut found: Vec<Option<Vec<Issue>>> = Vec::with_capacity(request.paragraphs.len());
    let mut pending = Vec::new();
    for (index, paragraph) in request.paragraphs.iter().enumerate() {
        let cached = match paragraph.trim().is_empty() {
            true => Some(Vec::new()),
            false => hammer::cache_get(&cache_key(&options, language, paragraph)).and_then(|it| serde_json::from_slice(&it).ok()),
        };
        if cached.is_none() {
            pending.push((index, paragraph.as_str()));
        }
        found.push(cached);
    }
    // Each batch's results are cached as they come, so a failure part way costs only the rest.
    for batch in check::batches(&pending, options.batch_limit()) {
        let response = send(&options, language, &batch.text)?;
        for (part, issues) in batch.parts.iter().zip(check::issues(&batch, &request.paragraphs, &response)) {
            let paragraph = &request.paragraphs[part.paragraph];
            hammer::cache_set(&cache_key(&options, language, paragraph), &serde_json::to_vec(&issues)?);
            found[part.paragraph] = Some(issues);
        }
    }
    let mut reply = Reply::default();
    for (index, issues) in found.iter().enumerate() {
        for issue in issues.iter().flatten().filter(|it| options.style || !it.suggestion) {
            reply.diagnostics.push(issue.diagnostic(index, &request.paragraphs[index]));
        }
    }
    Ok(reply)
}

#[unsafe(no_mangle)]
pub extern "C" fn diagnose() -> i32 {
    hammer::run(|| {
        let request: Request = hammer::input_json()?;
        let reply = check(&request).inspect_err(|error| hammer::log::warn(&error.to_string()))?;
        Ok(serde_json::to_vec(&reply)?)
    })
}
