Run from `/workspace/hegel-gap-study/augur-ledger`.

```bash
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
timeout 120s /workspace/tooling/jolt/jolt -m study run
timeout 120s /workspace/tooling/jolt/jolt -m study replay
timeout 120s /workspace/tooling/jolt/jolt witnesses.clj
cd target-only
timeout 120s /workspace/tooling/jolt/jolt repro.clj
timeout 120s /workspace/tooling/jolt/jolt tests.clj
```

Prefer `timeout 120s ./verify.sh` to verify without overwriting original generation artifacts. Its transcript includes exact commands and creates a new log directory on every invocation. Generation-original.log is the preserved initial syntax error (exit 1); generation-fixed.log is the successful corrected harness (exit 0). All other original process logs exited 0. A tool invocation before creating target-only failed to launch because its cwd did not yet exist; no target code ran. That setup mistake was corrected by creating the scratch subdirectory.
