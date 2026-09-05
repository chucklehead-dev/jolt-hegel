#!/usr/bin/env bash
# Run with the selected Jolt on PATH. Local workspace callers must wrap this
# script with tools/jolt-with-chez-10.4.1. Requires an installed native library.
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
probe_root="$(mktemp -d "${TMPDIR:-/tmp}/hegel-standalone.XXXXXXXX")"
echo "Standalone evidence retained at $probe_root"
mkdir -p "$probe_root/build-input/test" "$probe_root/consumer"
cp -R "$repo_root/src" "$repo_root/resources" "$probe_root/build-input/"
cp -R "$repo_root/test/standalone-resources" "$probe_root/build-input/test/"

# Isolate build state from the source checkout and other concurrent CI gates.
export JOLT_CACHE_DIR="$probe_root/cache"
export JOLT_GATEBOOT_BUILD_DIR="$probe_root/gateboot"
(
  cd "$probe_root/build-input/test/standalone-resources"
  jolt build -m consumer.resources -o "$probe_root/consumer/resource-probe"
) >"$probe_root/build.log" 2>&1 || { tail -80 "$probe_root/build.log"; exit 1; }

# Relocation alone is insufficient: boot-time forms may retain absolute build
# roots. Make every copied build input unavailable at its compiled path, while
# retaining the source and log evidence for diagnosis. Never move user sources.
mv "$probe_root/build-input" "$probe_root/preserved-build-input"
(
  cd "$probe_root/consumer"
  unset CLASSPATH JOLT_PWD
  ./resource-probe
) >"$probe_root/run.log" 2>&1 || { cat "$probe_root/run.log"; exit 1; }
cat "$probe_root/run.log"
