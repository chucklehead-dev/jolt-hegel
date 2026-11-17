#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
jolt=/workspace/tooling/jolt/jolt
logdir=$(mktemp -d "$PWD/logs/verify-XXXXXXXX")
exec > >(tee "$logdir/transcript.log") 2>&1
set -x
test "$(timeout 120s git -C /workspace/jolt-hegel rev-parse HEAD)" = "$(cat hegel-sha.txt)"
test "$(timeout 120s git -C /workspace/pilot-targets/augur rev-parse HEAD)" = "$(cat target-sha.txt)"
test "$(timeout 120s "$jolt" --version)" = "jolt v0.8.27"
timeout 120s "$jolt" -m study replay
timeout 120s "$jolt" witnesses.clj
timeout 120s "$jolt" -e '(require (quote study)) (let [r (hegel.core/run-test! {:test-cases 200 :stateful-step-count 35 :seed 414 :database "" :verbosity :normal} study/model-property)] (prn (select-keys r [:passed? :status :valid-test-cases :flaky?])) (assert (:passed? r)))'
cd target-only
unset HEGEL_LIBHEGEL_LIBRARY
timeout 120s "$jolt" repro.clj
timeout 120s "$jolt" tests.clj
