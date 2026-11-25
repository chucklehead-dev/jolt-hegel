#!/usr/bin/env bash
set -euo pipefail

# Run the same property with three output policies in fresh processes. Native stderr bypasses
# Clojure's *err* binding, so capture the actual file descriptor here.
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
for mode in quiet quiet-with-stats verbose; do
  HEGEL_OUTPUT_TEST_MODE="$mode" "$@" -e '
    (require (quote [hegel.core :as h]))
    (let [mode (keyword (System/getenv "HEGEL_OUTPUT_TEST_MODE"))
          verbosity (if (= mode :quiet-with-stats) :quiet mode)
          calls (atom 0)
          run (h/run-test! {:verbosity verbosity :test-cases 1 :seed 21
                            :database "" :show-statistics? (= mode :quiet-with-stats)}
                           (fn [_]
                             (swap! calls inc)
                             (h/note! "run-output-probe")
                             (throw (ex-info "reporting failure" {:hegel/origin "output/probe"}))))]
      (assert (and (= :failed (:status run)) (get-in run [:failures 0 :reproduced?])))
      (println @calls))' >"$work/$mode.out" 2>"$work/$mode.err" || { cat "$work/$mode.err"; exit 1; }
done
if [[ -s "$work/quiet.err" ]]; then
  cat "$work/quiet.err"
  exit 1
fi
if ! grep -Fqi 'statistics' "$work/quiet-with-stats.err" || grep -Eq 'run-output-probe|reporting failure' "$work/quiet-with-stats.err"; then
  cat "$work/quiet-with-stats.err"
  echo "Explicit quiet statistics should print without case notes or failure diagnostics" >&2
  exit 1
fi
calls=$(cat "$work/verbose.out")
notes=$(grep -cFx 'run-output-probe' "$work/verbose.err" || true)
if ! [[ "$calls" =~ ^[1-9][0-9]*$ && "$calls" == "$notes" ]]; then
  cat "$work/verbose.err"
  echo "Expected one verbose note per property call ($calls), got $notes" >&2
  exit 1
fi
