# Character interview

Interviews a character a page of questions at a time, then writes the answers into a new
encyclopedia entry for them.

## Using it

Interview a character, on Project Home, asks for the character's name and an interview:

- **Quick:** 12 questions.
- **Full:** 33 questions.
- **Proust questionnaire:** the 31 questions of the confession album Marcel Proust filled in around
  1890, in the plugin's own English wording.

It then puts the questions a page at a time, each answered in a plain multiline field, with Back and
Next, and ends on a review of the entry it is about to write. Create entry adds a person entry with a
heading for each page answered and each question in bold over its answer, and opens it. Questions left
blank are left out, and an entry with nothing answered has the name alone.

A run closed part way is kept, and picks up where you left it. Switching interviews keeps each one's
answers.

## Development

Written in Kotlin. It asks for `entry.create` and nothing else.

- **Pages:** a page per heading of the interview, titled like "Mara: Wants and fears (2 of 4)". Back
  has `validate = false`, so it never stops on a field.
- **State:** every page's `state` is the whole run (the name, the interview, the page, and every
  answer by interview and question), so each call starts from the request alone. The manifest has
  `keep = true`, so Hammer keeps a run the user leaves. A `state` the plugin cannot read starts again
  on the first page, keeping the name and answers it can find.
- **Text fields:** each question is a `plain` multiline field, since a rich editor per question
  repeats its format bar down the page.
- **Errors:** a name Hammer would refuse for an entry, and a failed `entry.create`, reply with
  `errors`.
- **Open:** Create entry replies with `open` for the new entry, which ends the run.
- **Answers** go into the entry as typed, except that their headings move down until the highest is
  `###`, so an answer cannot start a section of the entry, and a code fence left open is closed.
- **Interviews** are markdown files in `resources/sets/<id>.md`: a `## Heading` per page, an optional
  paragraph under it as the page's intro, and a `- ` bullet per question. Adding one is a file there,
  an entry in `SETS` in `CharacterInterview.kt`, and an option in the manifest's `set` field.
