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
