#!/usr/bin/env bash
set -euo pipefail
cd /workspace/naive-hegel-study/03-parser-meaning
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
timeout 120s /workspace/tooling/jolt/jolt -m parser-meaning correct
# Expected negative property verdict; command verifies that failure was observed.
timeout 120s /workspace/tooling/jolt/jolt -m parser-meaning control
# Each invocation starts a fresh process.
timeout 120s /workspace/tooling/jolt/jolt -m parser-meaning replay
timeout 120s /workspace/tooling/jolt/jolt -m parser-meaning fixture
