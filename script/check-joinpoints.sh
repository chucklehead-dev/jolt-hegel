#!/usr/bin/env bash
set -euo pipefail

# Linux developer gate for an explicitly selected aspect-capable compiler.
# The caller owns compiler selection (including the workspace Chez wrapper).
: "${JOLT_BIN:?Set JOLT_BIN to an absolute aspect-capable Jolt executable}"
: "${HEGEL_LIBHEGEL_LIBRARY:?Set the verified libhegel asset path}"
case "$JOLT_BIN" in /*) ;; *) echo 'JOLT_BIN must be absolute' >&2; exit 2 ;; esac
case "$HEGEL_LIBHEGEL_LIBRARY" in /*) ;; *) echo 'native asset must be absolute' >&2; exit 2 ;; esac
test -x "$JOLT_BIN"
test -f "$HEGEL_LIBHEGEL_LIBRARY"
command -v timeout >/dev/null
command -v bb >/dev/null
command -v rg >/dev/null

root=$(cd -- "$(dirname -- "$0")/.." && pwd)
stage=$(mktemp -d "${TMPDIR:-/tmp}/hegel-joinpoints.XXXXXXXX")
echo "Preserving join-point gate evidence at $stage"
# Stage only owned inputs; no old build reports/binaries can satisfy this run.
cp -R "$root/src" "$root/resources" "$stage/"
cp "$root/deps.edn" "$stage/deps.edn"
fixture="$stage/test/fixtures/joinpoints"
mkdir -p "$fixture/plain" "$fixture/stale"
cp -R "$root/test/fixtures/joinpoints/src" "$fixture/"
cp "$root/test/fixtures/joinpoints/deps.edn" "$fixture/deps.edn"
cp "$root/test/fixtures/joinpoints/check_report.clj" "$fixture/check_report.clj"
cp "$root/test/fixtures/joinpoints/plain/deps.edn" "$fixture/plain/deps.edn"
cp "$root/test/fixtures/joinpoints/stale/deps.edn" "$fixture/stale/deps.edn"
timeout 30 "$JOLT_BIN" --version
(cd "$stage" && JOLT_CACHE_DIR="$stage/cache-manifest" timeout 180 "$JOLT_BIN" aspects manifest --check)
(cd "$fixture" && JOLT_CACHE_DIR="$stage/cache-woven" timeout 300 "$JOLT_BIN" build -m fixture.app -o target/app)
(cd "$fixture" && timeout 30 bb check_report.clj target/aspects.edn)
(cd "$fixture" && timeout 60 ./target/app)

set +e
(cd "$fixture/stale" && JOLT_CACHE_DIR="$stage/cache-stale" timeout 300 "$JOLT_BIN" build -m fixture.app -o target/app) >"$stage/stale.log" 2>&1
stale_status=$?
set -e
if test "$stale_status" -ne 1; then
  echo "stale pin did not fail decisively (exit $stale_status)" >&2
  exit 1
fi
rg -q 'provider is incompatible with the manifest library revision' "$stage/stale.log"
rg -q 'provider-version deliberately-stale' "$stage/stale.log"
test ! -e "$fixture/stale/target/app"
(cd "$fixture/plain" && JOLT_CACHE_DIR="$stage/cache-plain" timeout 300 "$JOLT_BIN" build -m fixture.app -o target/app)
(cd "$fixture/plain" && timeout 60 ./target/app plain)
echo 'Join-point manifest, woven report/runtime, stale pin, and plain controls passed'
