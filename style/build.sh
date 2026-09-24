#!/bin/sh
# Builds style.hammerplugin: freestanding wasm32, no libc or WASI. Needs clang, wasm-ld (lld), and zip.
set -e
cd "$(dirname "$0")"
mkdir -p build
clang --target=wasm32-unknown-unknown -O2 -nostdlib \
	-Wl,--no-entry -Wl,--export-dynamic \
	-o build/style.wasm style.c
mkdir -p build/package
cp manifest.toml build/package/
cp build/style.wasm build/package/plugin.wasm
rm -f build/style.hammerplugin
(cd build/package && zip -q -X ../style.hammerplugin manifest.toml plugin.wasm)
echo "Built build/style.hammerplugin"
