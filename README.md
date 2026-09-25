# hammer-plugins

Runtime plugins for [Hammer](https://github.com/Wavesonics/hammer-editor): WebAssembly modules that
follow the [Extism](https://extism.org) plugin convention, plus the Hammer-specific import and exports
described in hammer-editor's `docs/CLIENT-PLUGINS.md`.

| Directory | What |
| --- | --- |
| `c/include` | The C plugin kit: `hammer.h`, on top of Extism's `extism-pdk.h` (BSD-3, see `EXTISM-PDK-LICENSE`), and `hammer_json.h`, a JSON reader that needs no allocation |
| `c/plaintext` | Plain text export for submission forms, with scene break, italics, and paragraph settings; reads prose as Hammer parses it (`input = "prose"`); `build.sh` builds `plaintext.hammerplugin` |
| `c/style` | Style report: readability, adverbs, dialogue, and repetition per scene and for the whole story, shown from the project menu (`[[actions]]` with document output) and cached per scene; `build.sh` builds `style.hammerplugin` |
| `c/wordfreq` | Word frequency export, in freestanding C |
| `c/upper` | Upper-cases its input: the C twin of `kotlin/upper`, for benchmarks |
| `assemblyscript/hammer` | The AssemblyScript plugin kit: Hammer's imports, operations, the cache, and a JSON reader and writer |
| `assemblyscript/style`, `wordfreq`, `wordcount`, `upper` | AssemblyScript twins of `c/style`, `c/wordfreq`, `kotlin/wordcount`, and `c/upper`, for comparing languages |
| `rust/hammer` | The Rust plugin kit: Hammer's imports, operations, and the cache, with serde for JSON; `diagnose` types, byte offsets, an optional `getrandom` backend, and a zeroing allocator for Wizer builds |
| `rust/english-grammar` | English grammar on [Harper](https://github.com/Automattic/harper), underlined in the editor, with settings for each category of check and for long sentences; pre-initialized with Wizer; its own `build.sh` builds `english-grammar.hammerplugin` |
| `rust/tools/finish.py` | Makes a module built from crates written for the browser loadable by Hammer: drops wasm-bindgen's leftovers and checks the imports |
| `rust/style`, `wordfreq`, `wordcount`, `upper` | Rust twins of the same plugins |
| `zig/hammer` | The Zig plugin kit: Hammer's imports, operations, and the cache |
| `zig/style`, `wordfreq`, `wordcount`, `upper` | Zig twins of the same plugins |
| `go/hammer` | The Go plugin kit, for TinyGo: Hammer's imports, operations, and the cache |
| `go/style`, `wordfreq`, `wordcount`, `upper` | Go twins of the same plugins |
| `kotlin/hammer` | The Kotlin plugin kit: typed requests, operations, the cache, and a fake Hammer for JVM tests |
| `kotlin/gradle-plugin` | The `hammer.plugin` Gradle plugin: sets up a plugin project and builds its `.hammerplugin` |
| `kotlin/mcp` | An MCP server for AI agents, as the `hammer mcp` command: offers every operation it is granted (`content:read` and `content:write`) as a tool, with scene edits as drafts unless live edits are allowed in its settings |
| `kotlin/wordcount` | Word count report export with declared settings, calling back into Hammer |
| `kotlin/style`, `wordfreq`, `upper` | Kotlin twins of `c/style`, `c/wordfreq`, and `c/upper`; `style` has JVM unit tests |
| `host-tests` | WAT sources of hammer-editor's host test fixtures |

## Building

- C: clang with the wasm32 target and lld (`sudo apt install clang lld`), then a plugin's `build.sh`, such
  as `c/plaintext/build.sh`.
- AssemblyScript: Node.js 20 or later and `npm install` in `assemblyscript/`, then `assemblyscript/build.sh`
  for every plugin there, or `assemblyscript/build.sh style` for one.
- Rust: [rustup](https://rustup.rs) and `rustup target add wasm32-unknown-unknown`, then `rust/build.sh`.
  `rust/english-grammar/build.sh` also needs wabt, Binaryen (on PATH, or its `bin` in `BINARYEN`), python3, and
  `cargo install wizer --all-features`.
- Zig: [Zig](https://ziglang.org/download/) 0.16, then `zig/build.sh`, or `zig/build.sh style` for one plugin.
- Go: [TinyGo](https://tinygo.org) and the Go it builds with, then `go/build.sh`, or `go/build.sh style` for
  one plugin.
- Kotlin: a JDK and any Gradle 9, then `kotlin/build.sh` for every plugin's package (set `GRADLE` to a
  Gradle launcher if `gradle` is not on the path), or `gradle :style:hammerPlugin` in `kotlin/` for one.
  `gradle jvmTest` runs the plugins' unit tests.

## Choosing a language

Hammer supports Kotlin and Rust for plugins. [LANGUAGES.md](LANGUAGES.md) shows why, comparing them with
Zig, C, AssemblyScript, and Go on speed, size, setup, and what writing a plugin in each is like.

## Installing

In Hammer on desktop: Settings, Plugins, Install plugin, pick the `.hammerplugin`, approve its
permissions, and restart Hammer.
- Host fixtures: wabt's `wat2wasm`, then `host-tests/build.sh [path to hammer-editor]`.
