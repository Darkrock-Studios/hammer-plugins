# LanguageTool

Grammar, punctuation, and style checks from [LanguageTool](https://languagetool.org), underlined as
you write, in more than 30 languages. The text you write is sent to LanguageTool's servers to be
checked, or to your own LanguageTool server if you run one.

## Settings

- **Service:** LanguageTool's free service (the default), LanguageTool Premium, or your own server.
- **Premium email** and **Premium API key**, from your LanguageTool account, for Premium.
- **Server:** your own server's address, such as `http://localhost:8081`. An address pasted with
  `/v2` or `/v2/check` on the end works too.
- **Picky:** more checks of style and typography. Off by default.
- **Style suggestions:** wordiness, redundancy, and register, underlined more quietly than mistakes.
  On by default.

The project's language goes to LanguageTool as it is; a project with none lets LanguageTool guess.
Spelling is left to Hammer's own spell check, so LanguageTool's spelling matches are dropped. Style,
register, and locale issues, and the style and redundancy categories, are suggestions. Each match
offers up to five fixes.

## Limits

The free service allows 20 requests a minute. Changed paragraphs travel together, a blank line
between them, in requests under the service's size limit: up to 18,000 bytes for the free service and
50,000 for Premium or your own server. Each paragraph's matches are cached by a hash of its text, the
service, Picky, and the language, so a scene opened again is not sent again; Style suggestions only
filters what is cached. Each batch is cached as it comes back, so a failure part way costs only the
rest. When the limit is reached, the check fails with a message and resumes once requests are allowed
again.

## Development

Written in Rust. `permissions.http` names LanguageTool's two hosts, `api.languagetool.org` and
`api.languagetoolplus.com`, and `permissions.userHosts` names the `server` setting, for your own
server. The API key and the server address are `secret` settings.

LanguageTool counts offsets in UTF-16 units; the plugin turns each match into UTF-8 byte offsets in
its paragraph, and drops a match that runs past its paragraph or starts or ends inside a character.
`src/check.rs` holds everything that does not call Hammer (settings, batches, matches), so it runs in
native tests.
