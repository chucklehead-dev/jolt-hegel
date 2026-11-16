# External property packs and DX probes

Use this workflow when testing a separate library through Hegel, or evaluating
how people/agents author, diagnose and replay those tests. It supplements the
[API reference](api.md); ordinary application properties do not need a research
study or a separate pack.

## Build an isolated consumer

1. Pin the target, Hegel source, runtime and native version. Keep target-only
   dependencies outside normal Hegel consumer paths. Record native compiler or
   platform requirements; use the target's documented build/cache overrides.
2. State the semantic domain and independent oracle before choosing generators.
   Roundtrip equality needs explicit normalization/type limits. A grammar
   generator and target parser derived through the same translator can share
   a bug; retain independently authored grammars, witnesses or semantic models.
3. Construct valid inputs directly, with bounded depth, size and execution time.
   Keep domain encoders, evaluators and normalization helpers in the pack first.
4. Assert the returned verdict. Add a semantic broken control showing the
   property catches the intended defect; an unconditional throw does not do so.
   An unrelated setup/native error must not count as detecting the control.
5. Record behavior-oriented coverage labels and explicit boundary witnesses.
   A threshold of one establishes presence, not distribution quality or
   completeness. Keep generation, shrinking and diagnostic-capture counts
   distinguishable; they are not independent random samples.
6. Save the retained ordinary input and a validated native bundle for stable
   failures. Reproduce in a fresh process with independently declared
   provenance; verify mismatches do not execute the property. Preserve the
   original failure evidence when a rerun fails to reproduce it.
7. Report target failures, coverage failures, setup errors and inconclusive
   executions distinctly. Capture actual process exit statuses and transcripts.
   A passing recovery call does not establish leak freedom; identify whether
   the tested error path reached native code or only a wrapper.

The maintained examples and commands are in the repository's
[external packs](../../../external/README.md). Their
[manifest](../../../external/targets.edn) pins Instaparse and yyjson-jolt; the
[study results](../../../external/study/RESULTS.md) distinguish reference runs,
agent reports and independently rerun evidence. Existing blobs retain their
original source revision and must not be relabelled after a change.

## Evaluate friction without inventing user findings

For an explicitly requested agent probe, supply each fresh agent a separate
workspace, public documentation, an independently specified task and bounded
runtime. Withhold reference solutions, other agents' work and earlier findings.
Record any assistance; provisioned environments do not measure cold install.
Do not delegate merely because this workflow is available—follow the session's
agent-use authorization and constraints.

Collect the implementation, commands, verdicts, coverage, minimized input,
replay/fixture artifacts, failed attempts, consulted docs/source and proposed
improvements. Mark an agent's journal as self-report, and independently rerun
submitted commands before treating outcomes as verified. Retain original
artifacts so verification does not erase earlier mistakes.

Use a small evidence ledger:

> Question → task/artifact → observed friction → proposed change → follow-up result

Ask whether authors can choose the input model, defend the oracle, explain the
minimum, identify the failing layer, recognise missing coverage and reproduce
someone else's failure. Automated tests verify behavior; observed human tasks
are needed to evaluate comprehension and discoverability. Same-family agents
are not independent human participants. Infrastructure failures are not API
findings, and speed alone is not understanding.

Classify proposed changes as documentation/example, pack-local helper, shared
harness or core API. Prefer recurring or correctness-critical evidence for a
core abstraction. The first six pilot tasks needed no new core API; useful
improvements concerned retained-input/replay guidance and immediate draws
versus reusable generators. Do not generalize that finding to resource or
concurrency workflows that were not tested.

For later stateful packs, compare an independent model with actual state and
identity, not just plausible event logs. Reconstruct fresh resources for every
execution, including shrink/replay. For concurrent targets, generated commands
and a seed do not capture an OS/fiber schedule; preserve explicit synchronization
decisions and observed histories, and distinguish inconclusive outcomes.
