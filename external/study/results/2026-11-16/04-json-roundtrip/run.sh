#!/usr/bin/env bash
set -euo pipefail
cd /workspace/naive-hegel-study/04-json-roundtrip
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export TMPDIR="$PWD/.tmp"
export YYJSON_JOLT_LIB="$PWD/native/libyyjson-jolt.so"
mkdir -p native .tmp
if [[ ! -f "$YYJSON_JOLT_LIB" ]]; then
  timeout 120s cc -O3 -shared -fPIC -o "$YYJSON_JOLT_LIB" /workspace/pilot-targets/yyjson-jolt/native/shim.c /workspace/pilot-targets/yyjson-jolt/native/yyjson/yyjson.c -lm
fi
timeout 120s /workspace/tooling/jolt/jolt -m probe "${1:-baseline}"
