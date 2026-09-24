#!/bin/sh
# Builds plaintext.hammerplugin: freestanding wasm32, no libc or WASI. Needs clang, wasm-ld (lld), and zip.
set -e
cd "$(dirname "$0")"
mkdir -p build
clang --target=wasm32-unknown-unknown -O2 -nostdlib \
	-Wl,--no-entry -Wl,--export-dynamic \
	-o build/plaintext.wasm plaintext.c
mkdir -p build/package
cp manifest.toml settings.toml build/package/
cp build/plaintext.wasm build/package/plugin.wasm
rm -f build/plaintext.hammerplugin
(cd build/package && zip -q -X ../plaintext.hammerplugin manifest.toml settings.toml plugin.wasm)
echo "Built build/plaintext.hammerplugin"
