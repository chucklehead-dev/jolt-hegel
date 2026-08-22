# Releasing jolt-hegel

Development is proven in a private repository before the exact release
candidate is pushed to the public source and release repository,
`chucklehead-dev/jolt-hegel`.

## Private proving ground

1. Push normal development branches to the private repository.
2. Require all three CI matrix jobs to pass:
   Linux x86_64, Windows x86_64, and macOS arm64.
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
3. Confirm the same three CI jobs pass in the public repository, merge the pull
   request, and verify that public `main` has the recorded tree.
   After the first public matrix, configure the default branch to require those
   Linux, Windows, and macOS jobs.
4. Confirm `hegel.version/jolt-hegel-version` matches the intended tag without
   the `v` prefix. Use a fresh `JOLT_CACHE_DIR`; the installer rejects a loaded
   release version that differs from the resolved source.
5. Create and push an annotated tag on the public merge commit, for example
   `v0.3.0`.
6. Wait for the Release workflow. It:
   - installs the checksum-pinned libhegel release on each supported runner;
   - runs the integration suite; and
   - runs the independent consumer fixture again.
7. Resolve the tag's full commit SHA and use it in consumer `deps.edn` files:

   ```bash
   git rev-list -n 1 v0.3.0
   ```

jolt-hegel no longer publishes native assets. Do not move or replace an
existing release tag; publish a new version instead.

## Version pins

Review these together for each release:

- `src/hegel/version.clj`: jolt-hegel release and libhegel ABI pins
- `.github/workflows/ci.yml`: tested Jolt asset names and SHA-256 values
- `.github/workflows/release.yml`: release verification target matrix
- `README.md` and `THIRD_PARTY_NOTICES.md`: documented versions and notices

Jolt 0.7.23 release archives are checksum-pinned in both workflows.
libhegel 0.33.0 release hashes are pinned in `hegel.install`. The release
workflow also rejects a tag that does not match
`hegel.version/jolt-hegel-version`.
