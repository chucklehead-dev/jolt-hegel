#!/usr/bin/env bash
set -eu
cd /workspace/naive-hegel-study/01-parser-acceptance
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
timeout 120s /workspace/tooling/jolt/jolt -M -m probe good
timeout 120s /workspace/tooling/jolt/jolt -M -m probe broken
timeout 120s /workspace/tooling/jolt/jolt -M -m probe replay
timeout 120s /workspace/tooling/jolt/jolt -M -m probe fixture
