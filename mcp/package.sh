#!/bin/sh
# Builds mcp.hammerplugin: manifest, settings, and the optimized Kotlin/Wasm module. Needs zip.
set -e
cd "$(dirname "$0")"
"${GRADLE:-gradle}" compileProductionExecutableKotlinWasmWasiOptimize -q
out=build/package
rm -rf "$out"
mkdir -p "$out"
cp manifest.toml settings.toml "$out/"
cp build/compileSync/wasmWasi/main/productionExecutable/optimized/mcp.wasm "$out/plugin.wasm"
rm -f build/mcp.hammerplugin
(cd "$out" && zip -q -X ../mcp.hammerplugin manifest.toml settings.toml plugin.wasm)
echo "Built build/mcp.hammerplugin"
