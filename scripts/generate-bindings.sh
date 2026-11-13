#!/bin/sh
# Builds the keep-core uniffi engine and generates the Kotlin binding into src/main/kotlin/uniffi/ (gitignored).
#
#   KEEP_CORE=<keep-core checkout> [CARGO_TARGET_DIR=<dir>] [FEATURES=test-trust-root] sh scripts/generate-bindings.sh [<engine-out-dir>]
#
# FEATURES=test-trust-root compiles the published TEST root into the engine (needed only to replay the trust KAT).
# The engine built here is written to <engine-out-dir> (default jvm-work/engine, gitignored) and is for host tests
# ONLY: never copy a test-root build into src/main/jniLibs or any shipped artifact. The Android ABIs
# (cargo-ndk, WITHOUT test-trust-root) are built by the publish pipeline.
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
: "${KEEP_CORE:?set KEEP_CORE to a keep-core checkout}"
OUT="${1:-$HERE/jvm-work/engine}"
FEATURES="${FEATURES:-}"
case "$(uname -s)" in Darwin) EXT=dylib ;; MINGW*|MSYS*|CYGWIN*) EXT=dll ;; *) EXT=so ;; esac
cd "$KEEP_CORE/ffi-uniffi"
CARGO_FEATURES="cli${FEATURES:+,$FEATURES}"
cargo build --features "$CARGO_FEATURES"
TARGET="${CARGO_TARGET_DIR:-$KEEP_CORE/ffi-uniffi/target}"
LIB="$TARGET/debug/libk9o_keep_core_ffi_uniffi.$EXT"
mkdir -p "$OUT"
cp "$LIB" "$OUT/"
cargo run --bin uniffi-bindgen --features "$CARGO_FEATURES" -- generate \
  --library "$OUT/libk9o_keep_core_ffi_uniffi.$EXT" --language kotlin --out-dir "$HERE/src/main/kotlin"
echo "binding: $HERE/src/main/kotlin/uniffi/k9o_keep_core_ffi_uniffi/  engine: $OUT/libk9o_keep_core_ffi_uniffi.$EXT"
