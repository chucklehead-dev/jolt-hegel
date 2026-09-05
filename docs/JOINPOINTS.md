# Compiler-aspect join-point contract

Status: experimental pending issue #23 acceptance. This document describes the
current tested surface and the proposed compatibility boundary; it does not
declare that an aspect-enabled compiler is part of the supported Jolt release.
Normal BB/JVM/Jolt property use never requires compiler aspects or a provider.

## Three separate identities

The resource `META-INF/jolt/aspects/hegel.edn` declares schema 1 and library
`chucklehead-dev/jolt-hegel`. Its revision is currently
`86a70acf7880184707a77069b60b2e8fd4acbbbb`, the historical declaration revision.
It is not the current source checkout SHA, native ABI version, or event-profile
revision. Pin the actual Hegel dependency by full Git SHA independently.

A provider must independently declare the same manifest library revision.
Reading the incoming manifest to manufacture a matching provider revision would
defeat the compiler's stale-provider check. The compiler does not authenticate
the manifest or prove that all source at that revision behaves identically.

`hegel.operation-events` revision `1` is a separate semantic event profile.
Selecting advice neither creates that envelope nor runs its validator. A
provider owns capture, payload shaping, bounds and concurrency bookkeeping;
assertions must run outside fail-open instrumentation advice.

## Declared entry boundaries

| ID | Selected entry | Arity | Advice role | Physical matches |
| --- | --- | --- | --- | --- |
| `:hegel.core/run-test` | `hegel.core/run-test!` | 2 | `:test/property-run` | 1 |
| `:hegel.stateful/run` | `hegel.stateful/run!` | 1 | `:test/state-machine-run` | 1 |

These are function-entry boundaries, not individual generated cases, rule
steps, shrinking attempts or final replays. The property-run boundary spans
the complete invocation, including engine work and cleanup. A state-machine
entry can execute repeatedly within one property run. Physical compiler match
count is not runtime invocation count.

Arguments, return values and exceptions follow the ordinary public APIs.
Advice must preserve their behavior; instrumentation errors are not reliable
test verdicts. Current tests exercise successful stateful execution and a
stateful usage exception propagating through both boundaries unchanged.

## Proposed compatibility policy

Retain existing identifiers and roles while these entry meanings and arities
remain unchanged. A selector, role, arity or semantic-boundary change requires
a new manifest revision and coordinated provider/pin migration, even if the
ordinary function API remains source-compatible. Additions also require
coordination because providers selecting all roles must implement every role.

Compiler-generated site IDs, source positions and build identities are
artifact-specific evidence, not stable public Hegel identifiers. Rebuild and
validate report cardinality after source/compiler changes; do not pin generated
site IDs across unrelated artifacts or infer source identity from the manifest
revision alone. The historical revision need not change for unrelated Hegel
implementation changes that preserve this declaration contract.

## Reproduce the current evidence

The pure resource test `hegel.joinpoint-contract-test` runs in the supported
aggregate suite. It checks literal identity, schema and declarations without
loading libhegel when run directly. It does not establish compiler matching.

The separate Linux developer gate requires an explicitly selected
aspect-capable compiler, a verified compatible native asset, Bash, GNU
`timeout`, Babashka and ripgrep:

```sh
JOLT_BIN=/absolute/path/to/aspect-capable/jolt \
HEGEL_LIBHEGEL_LIBRARY=/absolute/path/to/libhegel.so \
  bash script/check-joinpoints.sh
```

In the shared Jolt workspace invoke that command through the prescribed
`tools/jolt-with-chez-10.4.1` wrapper. Current local compiler evidence uses
`09f4218828f93320f3b43eb4e8ae5df76fc65210`, separately from released Jolt.
The gate stages only current inputs in a fresh temporary directory, retains
its evidence, checks source/manifest agreement, builds a real consumer,
validates exact report matches and executes it, rejects a stale provider, then
builds and executes the same application without selection. Each operation is
bounded; a stale-build timeout is not an accepted negative control.

Compiler manifest generation loads Hegel namespaces and therefore requires
libhegel; do not advertise that command as native-free. The bundled fixture
is a consumer, not a production instrumentation package. The report validator
checks ten malformed-report controls; stale provider rejection exercises the
compiler itself. Full #23 acceptance still requires reviewed compatibility
policy, CI/integration
evidence and coordinated documentation updates.
