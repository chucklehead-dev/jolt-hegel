# External-pilot DX/HCI study

The subsequent [source test-gap round](results/2026-11-17-gaps/README.md)
uses six fresh agents, adds augur and glitter-core, and records seven
independently reproduced target defects. Its original evidence, task prompts,
replay bundles and parent verification are archived separately below that link.

The reference implementation tests six concrete properties. A separate set of
fresh agents attempts the same tasks using only public Hegel documentation,
source when needed, and pinned target checkouts. They do not receive the
reference pack code, its outcomes, other agents' work, or conversation history.
The exact prompts are archived under `prompts/`; their absolute paths describe
the original execution workspace and need adapting for another environment.

## Questions and evidence

| Question | Probe/evidence | Possible response if friction recurs |
| --- | --- | --- |
| Can an author select and express the intended input model? | Grammar versus AST choice, docs lookups, arity mistakes, filtering and category coverage | Task-oriented recipes, clearer signatures, composition improvements |
| Can they defend the oracle and domain? | Independent grammar/evaluator/encoder, supported types, deliberately broken semantic control | Domain examples and clearer failure attribution |
| Is the minimized failure meaningful? | Domain validity, retained defect, author's explanation and next debugging step | Labelled values, semantic rendering or dependent-generation work |
| Can they distinguish Unicode/numerical representations? | NUL, supplementary characters and byte positions; augur numerics in a later pack | Escaped text, code-point/byte views and expected/actual/difference displays |
| Can another process reproduce it? | Saved native blob, ordinary value fixture, mismatched provenance, missing environment information | Replay workflow and actionable mismatch diagnostics |
| Can a passing run conceal missing behavior? | Presence/absence of required categories; author interpretation of hit counts | Coverage recipes and behavior-oriented reports |
| Can stateful resources be reconstructed safely? | Later glitter/augur edit/command sequences, then duratom lifecycle/fault cases | Trace views, fixtures or command helpers if demonstrated |

## Agent probe protocol

Six agents were given separate scratch workspaces, local dependency manifests,
Jolt 0.8.27, Hegel source baseline `8179cb83eda81c708dc834ee38647f2354fe3d55`,
libhegel 0.51.6 and pinned target sources. Their public Hegel source/docs remain
unchanged during the tasks. Each agent has a 20-minute budget and a 120-second
per-command limit, and must retain implementation, commands, evidence, a report
and a self-reported chronological journal. Lack of a successful reproduction is
an outcome to record, not permission to invent one.

This environment is pre-provisioned. It does **not** measure cold-install
onboarding. Agent self-reports and independent tool runs can reveal concrete
API/documentation problems, but they do not establish human comprehension,
preferences, representative usability rates or statistically independent
behavior. Infrastructure/transport failures are reported separately.

After collecting reports, verify submitted commands/artifacts where feasible,
and classify findings as documentation/example, pack-local helper, shared
harness, or core API. Preserve unresolved claims as hypotheses. Do not teach
agents a solution midway through the probe; operational clarification must be
recorded as assistance.

## Human follow-up

Use 3–5 developers over two short formative rounds, including someone new to
Hegel. Give bounded authoring, diagnosis and replay tasks with current docs;
record assistance, wrong assumptions and correctness of explanations. Include a
passing run that missed an important category. Test improvements in the second
round, varying presentation order when comparing diagnostic representations.

A finding should retain: question → reproducing task/artifact → observed
friction → proposed change → follow-up result. Passing tests alone do not answer
comprehension questions, and a smaller counterexample need not be easier to
understand. Concurrency/schedule replay remains a later study with ebb/ensemble.
