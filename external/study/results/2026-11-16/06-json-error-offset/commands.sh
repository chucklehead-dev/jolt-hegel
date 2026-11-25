#!/usr/bin/env bash
set -euo pipefail
cd /workspace/naive-hegel-study/06-json-error-offset
# Keep native build output inside the assigned scratch directory.
timeout 120s cc -O3 -shared -fPIC -o libyyjson-jolt.so /workspace/pilot-targets/yyjson-jolt/native/shim.c /workspace/pilot-targets/yyjson-jolt/native/yyjson/yyjson.c -lm
export YYJSON_JOLT_LIB="$PWD/libyyjson-jolt.so"
timeout 120s /workspace/tooling/jolt/jolt -M -m probe baseline > baseline.log 2>&1
timeout 120s /workspace/tooling/jolt/jolt -M -m probe control > control.log 2>&1
timeout 120s /workspace/tooling/jolt/jolt -M -m probe replay > replay.log 2>&1
timeout 120s /workspace/tooling/jolt/jolt -M -m probe fixture > fixture.log 2>&1
