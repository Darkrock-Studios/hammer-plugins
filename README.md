# hammer-plugins

Runtime plugins for [Hammer](https://github.com/Wavesonics/hammer-editor): WebAssembly modules that
follow the [Extism](https://extism.org) plugin convention, plus the Hammer-specific import and exports
described in hammer-editor's `docs/CLIENT-PLUGINS.md`.

| Directory | What |
| --- | --- |
| `c/include` | The C plugin kit: `hammer.h`, on top of Extism's `extism-pdk.h` (BSD-3, see `EXTISM-PDK-LICENSE`) |
| `c/wordfreq` | Word frequency export, in freestanding C |
| `kotlin/upper` | Upper-cases its input, in Kotlin/Wasm |
| `host-tests` | WAT sources of hammer-editor's host test fixtures |

## Building

- C: clang with the wasm32 target and lld (`sudo apt install clang lld`), then `c/wordfreq/build.sh`.
- Kotlin: `./gradlew compileDevelopmentExecutableKotlinWasmWasi` in `kotlin/upper`, with any Gradle 9.
- Host fixtures: wabt's `wat2wasm`, then `host-tests/build.sh [path to hammer-editor]`.
