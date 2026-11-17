#!/usr/bin/env bash
set -euo pipefail
cd /workspace/hegel-gap-study/parser-gap
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
JOLT=/workspace/tooling/jolt/jolt
[[ "$(timeout 120s git -C /workspace/jolt-hegel rev-parse HEAD)" == 6321d5ab485db656a48823030453bc4b2d0af1cc ]]
[[ "$(timeout 120s git -C /workspace/pilot-targets/instaparse rev-parse HEAD)" == 6105ad194ab5f52c255fe475fb20c59f0dde64e9 ]]
timeout 120s "$JOLT" --version
timeout 120s "$JOLT" -m study replay
timeout 120s "$JOLT" -m study run
(cd target-only && timeout 120s "$JOLT" repro.clj)
