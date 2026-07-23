# Releasing jolt-hegel

Development is proven in a private repository before the exact release
candidate is pushed to the public source and release repository,
`chucklehead-dev/jolt-hegel`.

## Private proving ground

1. Push normal development branches to the private repository.
2. Require all three CI matrix jobs to pass:
   Linux x86_64, Windows x86_64, and macOS arm64.
3. Do not create consumer tags in the private repository. The release workflow
   may build tag artifacts there, but its publish job is explicitly gated to
   `chucklehead-dev/jolt-hegel`.
4. Keep generated native libraries, Jolt caches, release output, and the local
   `refs/` research checkouts out of source control. `.gitignore` covers them.

## Public release

For the first public release, create an empty `chucklehead-dev/jolt-hegel`
repository without starter files, then add it as a second remote:

```bash
git remote add public git@github.com:chucklehead-dev/jolt-hegel.git
```

Keep `origin` pointed at the private proving ground.

For every release:

1. Push the exact CI-green source commit to the public repository's default
   branch: `git push public <release-candidate-sha>:refs/heads/main`.
2. Confirm the same three CI jobs pass in the public repository.
   After the first public matrix, configure the default branch to require those
   Linux, Windows, and macOS jobs.
3. Confirm `hegel.version/jolt-hegel-version` matches the intended tag without
   the `v` prefix.
4. Create and push that tag, for example `v0.1.0`.
5. Wait for the Release workflow. It:
   - builds and tests a shim on each supported runner;
   - creates SHA-256 sidecars;
   - publishes the six binary/checksum assets only from the public repository;
   - downloads the public assets through `hegel.install`; and
   - runs the consumer smoke test against each downloaded shim.
6. Resolve the tag's full commit SHA and use it in consumer `deps.edn` files:

   ```bash
   git rev-list -n 1 v0.1.0
   ```

The release assets are:

- `jolt-hegel-shim-linux-amd64.so`
- `jolt-hegel-shim-windows-amd64.dll`
- `jolt-hegel-shim-darwin-arm64.dylib`
- one `.sha256` sidecar for each binary

The source corresponding to every shim is the tagged `native/hegel_shim.c`.
The repository's EPL-2.0 license and source link satisfy the release binary's
source-availability notice. Do not move or replace an existing release tag;
publish a new version instead.

## Version pins

Review these together for each release:

- `src/hegel/version.clj`: jolt-hegel release and libhegel ABI pins
- `.github/workflows/ci.yml`: tested Jolt asset names and SHA-256 values
- `.github/workflows/release.yml`: build/release Jolt assets and target matrix
- `README.md` and `THIRD_PARTY_NOTICES.md`: documented versions and notices

Jolt 0.4.15 release archives are checksum-pinned in both workflows. libhegel
0.30.1 release hashes are pinned in `hegel.install`. The release workflow also
rejects a tag that does not match `hegel.version/jolt-hegel-version`.
