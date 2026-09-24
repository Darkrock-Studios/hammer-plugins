# hammer-plugins

Runtime plugins for [Hammer](https://github.com/Wavesonics/hammer-editor): WebAssembly modules that
follow the [Extism](https://extism.org) plugin convention, plus the Hammer-specific import and exports
described in hammer-editor's `docs/CLIENT-PLUGINS.md`.

| Directory | What |
| --- | --- |
| `c/include` | The C plugin kit: `hammer.h`, on top of Extism's `extism-pdk.h` (BSD-3, see `EXTISM-PDK-LICENSE`) |
| `c/wordfreq` | Word frequency export, in freestanding C |
| `c/upper` | Upper-cases its input: the C twin of `kotlin/upper`, for benchmarks |
| `kotlin/upper` | Upper-cases its input, in Kotlin/Wasm |
| `kotlin/wordcount` | Word count report export with declared settings, calling back into Hammer; `package.sh` builds `wordcount.hammerplugin` |
| `host-tests` | WAT sources of hammer-editor's host test fixtures |

## Building

- C: clang with the wasm32 target and lld (`sudo apt install clang lld`), then `c/wordfreq/build.sh`.
- Kotlin: `./gradlew compileProductionExecutableKotlinWasmWasiOptimize` in a plugin's directory, with any
  Gradle 9. The optimized build is a tenth to a fiftieth the size and two to three times faster than the
  development one.
  `kotlin/wordcount/package.sh` also zips the package (set `GRADLE` to a Gradle launcher if `gradle`
  is not on the path).

## Performance

See [PERFORMANCE.md](PERFORMANCE.md): C against Kotlin/Wasm on the same jobs, and what it means for
choosing a language.

## Installing

In Hammer on desktop: Settings, Plugins, Install plugin, pick the `.hammerplugin`, approve its
permissions, and restart Hammer.
- Host fixtures: wabt's `wat2wasm`, then `host-tests/build.sh [path to hammer-editor]`.
