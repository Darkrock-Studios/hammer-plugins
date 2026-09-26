#!/bin/sh
# Runs every plugin's unit tests: C plugins' native tests, Rust crates' tests on this machine, and
# Kotlin plugins' JVM tests.
set -e
cd "$(dirname "$0")"
sdk=../hammer-plugin-sdk
for test in */test.c; do "$sdk/c/test.sh" "$(dirname "$test")"; done
cargo test -q --target "$(rustc -vV | sed -n 's/^host: //p')"
"${GRADLE:-gradle}" -q jvmTest
