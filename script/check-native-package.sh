#!/usr/bin/env bash
# Exercise the opt-in dependency's lazy acquisition and static linking.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
probe_root="$(mktemp -d "${TMPDIR:-/tmp}/hegel-native-package.XXXXXXXX")"
echo "Native-package evidence retained at $probe_root"
mkdir -p "$probe_root/library/packaging" "$probe_root/app/src" "$probe_root/shipped"
cp -R "$repo_root/src" "$repo_root/resources" "$probe_root/library/"
cp -R "$repo_root/packaging/jolt" "$probe_root/library/packaging/"
cat > "$probe_root/app/deps.edn" <<'EDN'
{:paths ["src"]
 :deps {io.github.chucklehead-dev/jolt-hegel {:local/root "../library/packaging/jolt"}}}
EDN
cat > "$probe_root/app/src/probe.clj" <<'CLJ'
(ns probe (:require [hegel.core :as h]
                    [hegel.generator :as g]
                    [hegel.report :as report]))
(defn -main [& _]
  (assert (= [42] (h/sample 1 (g/integer 42 42))))
  (let [result (h/run-test!
                {:seed 42 :database "" :test-cases 100 :verbosity :quiet}
                (fn [_]
                  (let [n (h/draw! (g/integer 0 10))]
                    (when (>= n 5)
                      (throw (ex-info "threshold" {:hegel/origin "package/threshold"
                                                   :input n}))))))]
    (assert (= [{:origin "package/threshold" :input 5}]
               (report/failure-fixtures result)))
    (println "PASS packaged Hegel draw, shrink and confirmed fixture")))
CLJ
unset HEGEL_LIBHEGEL_LIBRARY HEGEL_CACHE_DIR HEGEL_PROJECT_ROOT JOLT_PWD
export JOLT_CACHE_DIR="$probe_root/cache"
export JOLT_GATEBOOT_BUILD_DIR="$probe_root/gateboot"
# A dependency that is not required must not fetch or load its native library.
(cd "$probe_root/app" && jolt -e '(println :dependency-unused)') > "$probe_root/lazy.log" 2>&1
test ! -e "$probe_root/library/.hegel-lib"
(cd "$probe_root/app" && jolt -m probe) > "$probe_root/source.log" 2>&1 || { tail -60 "$probe_root/source.log"; exit 1; }
# A different patch version distinguishes an override from the bundled engine.
case "$(uname -s)" in
  Linux*) asset=libhegel-linux-amd64.so; hash=b5972a7dd2c3408b2f79e56696757850eecd5f4c1d1a875f9e80377aaca63c3c ;;
  Darwin*) asset=libhegel-darwin-arm64.dylib; hash=36694cd1b900c36e42a70f2e1a0ae520b88dfe3768373baca40e4d6f253ce7bb ;;
  MINGW*|MSYS*|CYGWIN*) asset=libhegel-windows-amd64.dll; hash=90f06cb9b8148b49f8e29e371c07d4fda801f6e4108c6d9e8e960bc16915aa75 ;;
esac
mkdir "$probe_root/override"
gh release download libhegel/0.53.1 --repo hegeldev/hegel --pattern "$asset" --dir "$probe_root/override"
(cd "$probe_root/override" && if command -v sha256sum >/dev/null; then echo "$hash  $asset" | sha256sum -c -; else echo "$hash  $asset" | shasum -a 256 -c -; fi)
override="$probe_root/override"
if command -v cygpath >/dev/null; then override="$(cygpath -m "$override")"; fi
run_override() {
  (cd "$probe_root/app" && HEGEL_LIBHEGEL_LIBRARY="$1" jolt -m probe)
}
cp "$probe_root/library/.hegel-lib/libhegel_c.${asset##*.}" "$probe_root/override/custom.${asset##*.}"
run_override "$override/custom.${asset##*.}"
if run_override "$override/$asset" > "$probe_root/version.log" 2>&1; then
  echo "wrong-version override unexpectedly passed"; exit 1
fi
grep -F 'but loaded "0.53.1"' "$probe_root/version.log" || { cat "$probe_root/version.log"; exit 1; }
if run_override "$override/missing-library" > "$probe_root/missing.log" 2>&1; then
  echo "missing override unexpectedly passed"; exit 1
fi
grep -F 'missing-library' "$probe_root/missing.log" || { cat "$probe_root/missing.log"; exit 1; }
# Windows currently qualifies dependency acquisition and dynamic loading only.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) cat "$probe_root/source.log"; exit 0 ;;
esac
test -f "$probe_root/library/.hegel-lib/libhegel_c.a"
(cd "$probe_root/app" && jolt build -m probe -o "$probe_root/shipped/probe") > "$probe_root/build.log" 2>&1 || { tail -60 "$probe_root/build.log"; exit 1; }
# Both the source and every shared/static library vanish from their old paths.
mv "$probe_root/library" "$probe_root/preserved-library"
mv "$probe_root/app" "$probe_root/preserved-app"
(cd "$probe_root/shipped" && ./probe) > "$probe_root/run.log" 2>&1 || { cat "$probe_root/run.log"; exit 1; }
cat "$probe_root/run.log"
