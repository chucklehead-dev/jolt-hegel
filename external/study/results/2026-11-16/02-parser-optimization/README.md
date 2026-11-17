Run from `/workspace/naive-hegel-study/02-parser-optimization`:

```sh
timeout 120s /workspace/tooling/jolt/jolt -M -m probe good
timeout 120s /workspace/tooling/jolt/jolt -M -m probe control
timeout 120s /workspace/tooling/jolt/jolt -M -m probe replay
timeout 120s /workspace/tooling/jolt/jolt -M -m probe fixture
```

The good run checks 200 cases with seed 424242 and requires both formats and both empty/nonempty categories. Grammar: `S = chunk*; chunk = 'a' | 'b'`, with 0–32 chunks. Comparison is structural tree equality, excluding metadata. The good run passed; 187 cases used the optimization and 13 fell back.

The control intentionally loses the final chunk and is expected to fail. Its command succeeds only if Hegel reports a stable failure and bundle export succeeds. The retained minimum is hiccup `"a"`: `[:S [:chunk "a"]]` becomes `[:S]`. The replay command replays the saved native blob, checks independently declared provenance, and asserts reproduction. The fixture command checks the ordinary input against both real and deliberately broken behavior. Both fresh-process checks succeeded.

`report.edn` records exact coverage, limitations, lookups and revisions. `journal.jsonl` is chronological agent self-report, not independent telemetry. This is a pre-provisioned environment; no installation timing or memory benchmark was performed.
