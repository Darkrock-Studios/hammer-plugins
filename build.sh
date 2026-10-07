#!/bin/sh
# Builds each named plugin (default: every one) into <plugin>/build/<plugin>.hammerplugin, with the SDK
# checked out beside this repo. See the README for what each language needs.
set -e
cd "$(dirname "$0")"
sdk=../hammer-plugin-sdk
plugins="$*"
[ -n "$plugins" ] || plugins=$(for manifest in */manifest.toml; do dirname "$manifest"; done)
for plugin in $plugins; do
	plugin="${plugin%/}"
	if [ ! -f "$plugin/manifest.toml" ]; then
		echo "No plugin named $plugin" >&2
		exit 1
	elif [ -f "$plugin/$plugin.c" ]; then
		"$sdk/c/build.sh" "$plugin"
	elif [ -f "$plugin/build.sh" ]; then
		"$plugin/build.sh"
	elif [ -f "$plugin/Cargo.toml" ]; then
		cargo build -q --release -p "$plugin"
		# Cargo names a crate's module with underscores for its hyphens.
		"$sdk/tools/package.sh" "$plugin" "target/wasm32-unknown-unknown/release/$(printf %s "$plugin" | tr - _).wasm"
		echo "Built $plugin"
	elif [ -f "$plugin/build.gradle.kts" ]; then
		./gradlew -q ":$plugin:hammerPlugin"
		echo "Built $plugin"
	fi
done
