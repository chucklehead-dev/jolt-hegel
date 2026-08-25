# Releasing jolt-hegel

Development is proven in a private repository before the exact release
candidate is pushed to the public source and release repository,
`chucklehead-dev/jolt-hegel`.

## Private proving ground

1. Push normal development branches to the private repository.
2. Require the supported host-by-platform CI matrix to pass. The core matrix is
   Jolt, the pinned Babashka fork, and JVM Clojure across Linux x86_64, Windows
   x86_64, and macOS arm64. Keep the pinned Babashka native-image build lanes
   separate in reporting so they do not obscure which host/backend failed.
3. Do not create consumer tags in the private repository. Release publication
   is explicitly gated to `chucklehead-dev/jolt-hegel`.
4. Keep downloaded native libraries, Jolt caches, release output, and local
   research checkouts out of source control. `.gitignore` covers them.

## Public release

For the first public release, create an empty `chucklehead-dev/jolt-hegel`
repository without starter files, then add it as a second remote:

```bash
git remote add public git@github.com:chucklehead-dev/jolt-hegel.git
```

Keep `origin` pointed at the private proving ground.

For every release:

1. Record the exact tree of the private CI-green merge:
   `git rev-parse <release-candidate-sha>^{tree}`.
2. Create a public release-branch commit with that tree and public `main` as its
   parent, then open a pull request to the protected public default branch. Do
   not merge the private history into the public repository.
3. Confirm the same host-by-platform cells pass in the public repository, merge
   the pull request, and verify that public `main` has the recorded tree.
   After the first public matrix, configure the default branch to require the
   aggregate Jolt, Babashka, JVM, ABI, and native-image gates.
4. Confirm `hegel.version/jolt-hegel-version` matches the intended tag without
   the `v` prefix. Use a fresh `JOLT_CACHE_DIR`; the installer rejects a loaded
   release version that differs from the resolved source.
5. Create and push an annotated tag on the public merge commit, for example
   `v0.3.0`.
6. Wait for the Release workflow. It:
   - installs the checksum-pinned libhegel release in each matrix cell;
   - runs the shared integration suite under the selected host; and
   - runs the independent consumer fixture again.
7. Resolve the tag's full commit SHA and use it in consumer `deps.edn` files:

   ```bash
   git rev-list -n 1 v0.3.0
   ```

jolt-hegel no longer publishes native assets. Do not move or replace an
existing release tag; publish a new version instead.

## Version pins

Review these together for each release:

- `src/hegel/version.cljc`: jolt-hegel release and libhegel ABI pins
- `.github/workflows/ci.yml`: host and OS matrix, pinned Jolt and Babashka builds
- `.github/workflows/release.yml`: release verification target matrix
- `README.md` and `THIRD_PARTY_NOTICES.md`: documented versions and notices

Jolt 0.7.23 and the required Babashka fork commit are pinned in CI.
libhegel 0.33.0 release hashes are pinned in `hegel.install`. The JVM lane uses
JDK 25 and should retain a JDK 22 minimum-version compatibility gate. The
release workflow also rejects a tag that does not match
`hegel.version/jolt-hegel-version`.
