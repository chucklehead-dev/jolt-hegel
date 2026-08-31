# Releasing jolt-hegel

`chucklehead-dev/jolt-hegel` is the public source and release authority.
Development may be proven in a private fork first, but a release candidate is
not publishable until its public pull request and public `main` workflows pass.

## Current release

`v0.5.0` is the current portable jolt-hegel release. It supports Jolt,
FFI-capable Babashka, and JVM Clojure through the upstream `babashka.ffi`
implementation and includes the experimental jank and ClojureCLR ports. The
source declaration in `hegel.version/jolt-hegel-version`, annotated tag, and
GitHub release must all identify `0.5.0`.

## Private proving ground

1. Push normal development branches to the private repository.
2. Require the supported host-by-platform CI matrix to pass. The core matrix is
   Jolt, checksum-pinned FFI-capable assets from the official Babashka release,
   and JVM Clojure across Linux x86_64, Windows x86_64, and macOS arm64. The
   Linux Babashka cell must use the non-static release asset.
3. Do not create consumer tags in the private repository. Release publication
   is explicitly gated to `chucklehead-dev/jolt-hegel`.
4. Keep downloaded native libraries, Jolt caches, release output, and local
   research checkouts out of source control. `.gitignore` covers them.

## Public release

For every release:

1. If development happened in another repository, record its CI-green tree with
   `git rev-parse <release-candidate-sha>^{tree}` and publish that tree through
   a pull request to public `main`. Do not merge unrelated private history into
   the public repository.
2. Confirm the public pull request and resulting `main` push pass the complete
   supported matrix: Jolt, pinned official Babashka release, JVM Clojure, ABI data,
   static checks, and consumer smoke tests. Experimental jank and ClojureCLR
   workflows should also be green on `main`, but they are not supported-release
   targets until their remaining gates close.
3. Confirm `hegel.version/jolt-hegel-version` matches the intended tag without
   the `v` prefix. Use a fresh `JOLT_CACHE_DIR`; the installer rejects a loaded
   release version that differs from the resolved source.
4. For a libhegel ABI upgrade, compare the canonical descriptor to the exact
   upstream tag and commit, verify every published asset digest, regenerate
   jank and CLR bindings, and run real native ownership/retry tests on all
   supported hosts. Concurrency above one remains a separate nondeterministic
   API decision and must not enter sequential `hegel.stateful/run!` implicitly.
5. Create and push an annotated tag on the public merge commit, for example
   `v0.5.0`.
6. Wait for the Release workflow. It:
   - installs the checksum-pinned libhegel release in each matrix cell;
   - runs the shared integration suite under the selected host; and
   - runs the independent consumer fixture again.
7. Verify that GitHub created the source release only after all reusable
   portable-CI and release-verification jobs passed.
8. Resolve the tag's full commit SHA and use it in consumer `deps.edn` files:

   ```bash
   git rev-list -n 1 v0.5.0
   ```

jolt-hegel no longer publishes native assets. Do not move or replace an
existing release tag; publish a new version instead.

## Version pins

Review these together for each release:

- `src/hegel/version.cljc`: jolt-hegel release and libhegel ABI pins
- `.github/workflows/ci.yml`: host and OS matrix, pinned Jolt and Babashka releases
- `.github/workflows/clr.yml`: experimental ClojureCLR/.NET pins and matrix
- `.github/workflows/jank.yml`: experimental jank packages/actions and matrix
- `.github/workflows/release.yml`: release verification target matrix
- `README.md` and `THIRD_PARTY_NOTICES.md`: documented versions and notices

Jolt 0.7.23 and FFI-capable Babashka 1.13.220 assets are checksum-pinned in CI.
The ordinary Linux asset is required; the static Linux asset has neither
dynamic-library loading nor the libffi route required by libhegel aggregates.
The standalone
`babashka/ffi` JVM dependency matches the source embedded by that Babashka
release at `aacb153618bc39ca1e4c397b8f30fb81c76d0c4c`. libhegel 0.33.3
release hashes are pinned in `hegel.install`. The JVM lane uses JDK 25 and
retains a JDK 22 minimum-version compatibility gate. Experimental CLR CI pins
ClojureCLR 1.12.2 and .NET 8. The release workflow also rejects a tag that does
not match `hegel.version/jolt-hegel-version`.
