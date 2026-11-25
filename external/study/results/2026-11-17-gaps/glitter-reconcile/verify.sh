#!/usr/bin/env bash
set -euo pipefail
cd /workspace/hegel-gap-study/glitter-reconcile
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
JOLT=/workspace/tooling/jolt/jolt
run_logs=$(mktemp -d "$PWD/logs/verify-XXXXXX")
[[ $(timeout 120s git -C /workspace/pilot-targets/glitter-core rev-parse HEAD) == 95b4d94322e4d901091d9d45b15b52b8c9146d54 ]]
[[ $(timeout 120s git -C /workspace/jolt-hegel rev-parse HEAD) == 6321d5ab485db656a48823030453bc4b2d0af1cc ]]
[[ $(timeout 120s "$JOLT" --version) == "jolt v0.8.27" ]]
timeout 120s "$JOLT" -M -m study positives 2>&1 | tee "$run_logs/positives.log"
timeout 120s "$JOLT" -M -m study replay 2>&1 | tee "$run_logs/replay.log"
(cd target-only && timeout 120s "$JOLT" -M ../src/target_repro.clj) 2>&1 | tee "$run_logs/target-only.log"
printf "Verified expected known failures and passing controls. Logs: %s\n" "$run_logs"
