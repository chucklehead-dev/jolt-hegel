#!/usr/bin/env bash
set -euo pipefail
cd /workspace/hegel-gap-study/json-options
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
export YYJSON_JOLT_LIB="$PWD/libyyjson-jolt.so"
test "$(timeout 120s git -C /workspace/jolt-hegel rev-parse HEAD)" = 6321d5ab485db656a48823030453bc4b2d0af1cc
test "$(timeout 120s git -C /workspace/pilot-targets/yyjson-jolt rev-parse HEAD)" = e9562890a5f74b1cfd8added087860e7533df605
timeout 120s cc -O3 -shared -fPIC -o "$YYJSON_JOLT_LIB" /workspace/pilot-targets/yyjson-jolt/native/shim.c /workspace/pilot-targets/yyjson-jolt/native/yyjson/yyjson.c -lm
timeout 120s /workspace/tooling/jolt/jolt -M -m study
timeout 120s /workspace/tooling/jolt/jolt -M replay.clj
timeout 120s /workspace/tooling/jolt/jolt -M target-only.clj
