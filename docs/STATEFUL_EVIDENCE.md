# Did the state machine exercise the behavior you care about?

Pass this option to `h/run-test!`. Each application counts, including
repeated applications in the same valid case.

```clojure
{:stateful-coverage
 {:rules {:insert {:min-applied 10} :revise {:min-applied 5}
          :cycle {:min-applied 1} :not-found {:min-applied 1}}}}
```

Each rule may require any combination of `:min-applied`, `:min-selected`,
`:min-guard-rejected`, and `:min-eligible`. The last requires `:census? true`.
For example, `{:min-eligible 10 :min-selected 1}` checks that a sampled
eligible rule was selected at least once. The counts under `:checks` use
valid cases, even when other outcomes appear under `:rules`.

For each missed rule minimum the default reporter shows the valid-case
selection, guard rejection, assumption rejection, and application counts.
With a census it also shows how many sampled steps saw an eligible guard.
A rule eligible at seven steps and selected at zero calls for investigating
selection. A rule eligible at zero of seven calls for inspecting the guard
and the model states generated. Zero sampled steps means there was no
valid-case census evidence for that rule.

An empty configuration reports counts without imposing minima. Under
`:stateful-coverage :rules` each declared rule has counts for `:selected`,
`:guard-passed`, `:guard-rejected`, `:assumption-rejected`, `:applied` and
`:failed`. An invariant failure after a rule returns still counts as an
application and increments its separate `:invariant-failed` counter. A rule exception counts as failed. The summary includes attempts
through the first failing exploration case. It stops there so shrinking and
confirmation cannot inflate totals. Only completed valid cases contribute to
`:checks`. A missed minimum changes an otherwise passing run to
`:coverage-failed`. For multiple machines within one property use distinct
rule names unless you intend to aggregate them. To require a particular
outcome within a rule, emit an `h/event!` for it and set a categorical
coverage requirement on that label.

By default eligibility of unselected rules is unknown. If **all** guards
are pure functions of model state, set `:census? true` inside
`:stateful-coverage`. At each native selection the census checks every model
guard once and reuses the selected answer. A positive `:eligible` count with
zero selections identifies a possible weighting or swarm-selection issue.
Zero means that no sampled selection saw an eligible model state. Keep SUT
reads, draws, assumptions and side effects out of census guards.

## Save one confirmed command sequence

Require `[hegel.stateful.journal :as journal]` and use
`(journal/run! opts machine-config)` in the property. The machine has the same
rules and invariants as `hegel.stateful/run!`. Inside a rule, call
`(journal/entry! {:input {:id id} :outcome :inserted})` after the operation
and before an assertion that might throw. Optional `:model-before` and
`:model-after` fields are caller-supplied portable summaries. The journal
adds the normalized rule name. Use `(hs/rule-id :store/insert)` to obtain
`"store/insert"` when joining a coverage key to a journal entry. Rejected attempts roll back their entries.

```clojure
{:max-entries 64 :max-bytes 16384
 :redact (fn [entry] (update entry :input select-keys [:id]))}
```

For expensive diagnostics, guard construction of the entry itself. Clojure
evaluates the argument to `entry!` on every call.

```clojure
(h/when-final
  (journal/entry! {:input {:id id} :model-after (model-digest model)}))
```

Keep the command, model update, and assertions outside this guard so every
candidate exercises the same property.

For a durability scan after the last step, supply `:final-check` to
`journal/run!`. It receives the final model on each successfully completed
machine run, including replay. Throw to signal failure and use `:hegel/origin`
to identify distinct checks. A normal return preserves the machine's final
state. A confirmed check failure carries the machine's redacted journal.

Only native capture executions record entries. A redactor runs before
validation and retention. Its exception aborts the run without echoing its
input. The default redactor is identity. The limits above are the defaults.
The byte limit measures the restricted EDN encoding of the events vector
in UTF-8, including delimiters. Truncation retains a bounded prefix and
marks `:truncated? true` in the failure exception's
`:hegel.stateful.journal/journal` data. `journal/fixture` accepts one
confirmed failure and returns a complete event vector. It rejects a truncated
journal or a caveated failure. `(journal/to-bundle provenance result origin)`
exports only the selected failure and its events using the ordinary replay
bundle schema. Redaction covers these journal entries. Handle native blobs,
exception messages and other diagnostics according to their own contracts.

The [JDK 25 SQLite consumer](../test/sqlite-consumer/probe.clj) opens a
fresh SQLite file per candidate. The writer sees the updated row, but a
second connection loses the revision if the writer closed without committing.
The probe checks both transaction paths and deletes the file after each run.
