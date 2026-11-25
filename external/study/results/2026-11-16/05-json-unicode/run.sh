#!/bin/sh
set -eu
cd /workspace/naive-hegel-study/05-json-unicode
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export YYJSON_JOLT_LIB="$PWD/libyyjson-jolt.so"
exec timeout 120s /workspace/tooling/jolt/jolt -m probe "$@"
