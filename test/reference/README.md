# Cross-binding contracts

These tests exercise the same properties through jolt-hegel and through
hegel-clj, which uses hegel-java. Each binding runs in a fresh process. An
integer above 2³¹ must stay within its bounds, drawing two distinct values
from three must produce a two-element set, and an assumption must discard
cases while admitting valid ones. A failing threshold must shrink to 5 and
replay once with that same witness. Each expectation can catch a mistake
even if both bindings make it.

Two more properties exercise interactions between draws. A map with two entries
draws vector keys from `[0]`, `[1]`, and `[2]`. Duplicate keys must leave the
requested size intact even when the existing value is nil or false. A failing
property draws a nonempty vector and an index bounded by its length. After
shrinking, the index must still select a negative element. Replaying must
produce that binding’s exact vector and index in one call.

The stack property runs both state-machine drivers with a vector model and a
list-backed stack. Popping an empty stack rejects that step. An invariant
compares the value returned by every successful pop to the model. A correct
stack passes. A deliberately FIFO pop must yield a sequence that distinguishes
FIFO from LIFO, and each binding must replay its own exact sequence.

The revision property sends stale and current updates to a versioned store.
A stale request must preserve the value and leave the next current request
able to update it. A planted implementation increments the revision on rejection.
Each binding must find a stale request followed by a failed current update
and reproduce its own sequence and payloads.

Linux CI runs jolt-hegel on Jolt and JVM. The Java reference runs twice.
Published hegel-clj 0.1.1 brings hegel-java 0.11.1 (libhegel 0.51.0).
The matched run uses hegel-java 0.14.3 (libhegel 0.53.2). A failure on only
the published run calls for checking engine and Java-wrapper version changes.
Both bindings share the native engine, so agreement alone cannot establish
its correctness.

From this directory, with JDK 25 and libhegel installed for jolt-hegel:

```sh
export HEGEL_PROJECT_ROOT="$(git rev-parse --show-toplevel)"
export REFERENCE_HEGEL_SHA="$(git rev-parse HEAD)"
clojure -J--enable-native-access=ALL-UNNAMED -M:ffi -m reference.ffi
clojure -J--enable-native-access=ALL-UNNAMED -M:published -m reference.java
clojure -J--enable-native-access=ALL-UNNAMED -M:published:matched -m reference.java
```

The reference uses explicit collection bounds because hegel-clj 0.1.1's set
generator ignores the jolt-hegel `:size` shorthand. It calls hegel-java's
`Settings.reproduceFailure` directly because the wrapper accepts the replay
option but omits it when constructing settings. Each binding consumes its own
failure recording. The tests allow generation order and draw counts to vary.
