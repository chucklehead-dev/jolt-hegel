# Reproduction commands

From `/workspace/hegel-gap-study/augur-models`:

```bash
export HEGEL_LIBHEGEL_LIBRARY=/workspace/jolt-hegel/.hegel-lib/libhegel_c.so
export JOLT_CACHE_DIR="$PWD/.jolt-cache"
timeout 120s /workspace/tooling/jolt/jolt -m study generate
# Generates and overwrites the four summaries, two ordinary minima, and two bundles.
# To preserve original study artifacts, use verify.sh instead:
timeout 120s ./verify.sh
```

`verify.sh` contains exact component commands and SHA gates. It does not overwrite original evidence. The final recorded run used:

```bash
timeout 120s bash -c './verify.sh > logs/verify-final.log 2>&1; result=$?; echo "$result" > logs/verify-final.exit; exit "$result"'
```

Exit was 0. Generation and initial fresh-process replay outputs are preserved separately in `logs/generate-02.log` and `logs/replay-original.log`. Failed initial authoring attempts are retained in `logs/generate-original.log` and `logs/verify-original.log`.

Inspection used bounded `cat`, `rg`, `nl`, `sed`, git rev-parse, jolt --version, and uname. Consulted source/test locations and documentation are enumerated in report.md. No target edits or issue publication commands were run.
