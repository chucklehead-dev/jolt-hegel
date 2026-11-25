All work used `/workspace/hegel-gap-study/glitter-reconcile` as cwd except target-only replay (its child directory). Shell tool calls were bounded with timeout 120s. No target/Hegel edits.

Environment for main consumer:
```bash
export JOLT_CACHE_DIR=/workspace/hegel-gap-study/glitter-reconcile/.jolt-cache
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
```

Executed commands and original transcripts:
- `/workspace/tooling/jolt/jolt -M src/probe.clj` with deps-original.edn: dependency resolution failure (`logs/probe.log`). Shell wrapper initially returned ongoing session; transcript records unresolved dependency exception. Do not count as a target defect.
- Same command with source-path deps.edn: exit 0, `logs/probe-source-path.log`.
- `/workspace/tooling/jolt/jolt -M src/lifecycle_probe.clj`: exit 0, `logs/lifecycle-probe.log`.
- `/workspace/tooling/jolt/jolt -M -m study generate`: exit 1 initially (`logs/generate-original.log`), due to oracle using keyword attribute lookup; corrected to string lookup per core/set-attr-val. No target defect claimed from this attempt.
- Same generation command after correction: exit 0 (`logs/generate-corrected.log`), explicitly requires each property to return :passed? false, :status :failed and nonflaky. Saves plain minima and bundles.
- `/workspace/tooling/jolt/jolt -M -m study replay`: exit 0, fresh process, `logs/replay-original.log`; checks incompatible provenance executes zero callbacks.
- `/workspace/tooling/jolt/jolt -M -m study positives`: exit 0, `logs/positives-original.log`.
- From target-only directory: `/workspace/tooling/jolt/jolt -M ../src/target_repro.clj`: exit 0, `logs/target-only-original.log`. Target-only deps do not load Hegel/native.
- `timeout 120s ./verify.sh`: all known failures asserted as known behavior, all positive tests must pass. Writes new logs without overwriting evidence.

To regenerate bundles intentionally (overwrites current artifacts): `timeout 120s /workspace/tooling/jolt/jolt -M -m study generate`. Normal verification only replays them.
