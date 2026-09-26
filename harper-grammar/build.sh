#!/bin/sh
# Builds harper-grammar.hammerplugin, with locales/ and resources/ if there are any. Needs Rust with the
# wasm32-unknown-unknown target, wizer, wabt, Binaryen (on PATH, or its bin directory in BINARYEN),
# python3, and zip.
set -e
cd "$(dirname "$0")"
cargo build --release -p harper-grammar
rm -rf build/package
mkdir -p build/package
python3 ../tools/finish.py ../target/wasm32-unknown-unknown/release/harper_grammar.wasm build/finished.wasm
# Runs wizer.initialize now and keeps the memory it leaves, so the linter arrives built.
wizer --allow-wasi --wasm-bulk-memory true --wasm-reference-types true -o build/package/plugin.wasm build/finished.wasm
cp manifest.toml settings.toml build/package/
[ -d locales ] && cp -r locales build/package/
[ -d resources ] && cp -r resources build/package/
rm -f build/harper-grammar.hammerplugin
(cd build/package && zip -q -X -r ../harper-grammar.hammerplugin .)
echo "Built build/harper-grammar.hammerplugin"
