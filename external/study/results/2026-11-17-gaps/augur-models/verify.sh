#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
JOLT=/workspace/tooling/jolt/jolt
[[ "$(git -C /workspace/jolt-hegel rev-parse HEAD)" == 6321d5ab485db656a48823030453bc4b2d0af1cc ]]
[[ "$(git -C /workspace/pilot-targets/augur rev-parse HEAD)" == 58fef3f2077515c216cd7a295e4599e8579ad5bb ]]
"$JOLT" --version
timeout 120s "$JOLT" -m target-repro
timeout 120s "$JOLT" -m study replay
timeout 120s "$JOLT" -m controls
timeout 120s "$JOLT" -A:target-tests -m existing-tests
