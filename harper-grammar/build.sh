#!/bin/sh
# Builds harper-grammar.hammerplugin, with the SDK checked out beside this repo. Needs Rust with the
# wasm32-unknown-unknown target, wabt, Binaryen (on PATH, or its bin directory in BINARYEN), python3,
# and zip.
set -e
cd "$(dirname "$0")"
sdk=../../hammer-plugin-sdk
cargo build --release -p harper-grammar
mkdir -p build
python3 "$sdk/rust/tools/finish.py" ../target/wasm32-unknown-unknown/release/harper_grammar.wasm build/harper-grammar.wasm
"$sdk/tools/package.sh" . build/harper-grammar.wasm
echo "Built build/harper-grammar.hammerplugin"
