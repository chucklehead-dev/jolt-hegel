(ns hegel.suites.install
  "Installer contract scenarios, loaded only when selected."
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [hegel.install :as install]
            [hegel.install.backend :as install-backend]
            [hegel.install-publication-test]
            [hegel.host :as host]
            [hegel.native :as native]
            [hegel.test-support :as support]
            [hegel.version :as version]))

(defn installer-source-identity [context]
  (support/check! context "installer recognizes POSIX and Windows absolute paths"
         (and (native/absolute-path? "/tmp/jolt-hegel")
              (native/absolute-path? "C:\\src\\jolt-hegel")
              (native/absolute-path? "D:/src/jolt-hegel")
              (not (native/absolute-path? "src/jolt-hegel"))))
  (support/check! context "installer verifies the loaded release against current source"
         (= version/jolt-hegel-version
            (install/verify-source-version!)))
  (if (= :jolt (host/runtime))
    (let [error
          (with-redefs [version/jolt-hegel-version "0.0.0-stale"]
            (try
              (install/verify-source-version!)
              nil
              (catch Throwable error
                error)))]
      (support/check! context "installer rejects a stale Jolt AOT namespace"
             (and (= ::install/stale-aot-cache (:type (ex-data error)))
                  (= "0.0.0-stale" (:loaded-version (ex-data error)))
                  (= version/jolt-hegel-version
                     (:source-version (ex-data error)))
                  (str/includes? (ex-message error) "JOLT_CACHE_DIR"))))
    (support/check! context "installer skips Jolt-only source identity checks on this host"
           (= "0.0.0-stale"
              (with-redefs [version/jolt-hegel-version "0.0.0-stale"]
                (install/verify-source-version!))))))

(defn portable-path-contracts [context]
  (support/check! context "absolute path classification is portable across host syntaxes"
         (and (every? native/absolute-path?
                      ["/tmp/jolt-hegel"
                       "C:\\src\\jolt-hegel"
                       "D:/src/jolt-hegel"
                       "\\\\server\\share\\jolt-hegel"
                       "\\rooted"])
              (not-any? native/absolute-path?
                        ["" "." "src/jolt-hegel" "C:relative"])))
  (support/check! context "parent paths preserve POSIX, drive, UNC, and relative syntax"
         (= ["/tmp/jolt-hegel"
             "C:\\src\\jolt-hegel"
             "\\\\server\\share\\jolt-hegel"
             "/"
             "\\"
             "src"
             nil
             nil]
            (mapv native/parent-path
                  ["/tmp/jolt-hegel/libhegel_c.so"
                   "C:\\src\\jolt-hegel\\libhegel_c.dll"
                   "\\\\server\\share\\jolt-hegel\\libhegel_c.dll"
                   "/src"
                   "\\src"
                   "src/libhegel_c.so"
                   "libhegel_c.so"
                   ""])))
  (support/check! context "drive and UNC roots preserve absolute parent contracts"
         (= ["/"
             "/"
             "C:/"
             "C:\\"
             "C:/"
             "C:\\"
             "C:/src"
             "C:\\src"
             "C:/src"
             "C:\\src"
             "\\\\server\\share"
             "\\\\server\\share"
             "\\\\server\\share"]
            (mapv native/parent-path
                  ["/"
                   "/src"
                   "C:/"
                   "C:\\"
                   "C:/src"
                   "C:\\src"
                   "C:/src/nested"
                   "C:\\src\\nested"
                   "C:/src/"
                   "C:\\src\\"
                   "\\\\server\\share"
                   "\\\\server\\share\\item"
                   "\\\\server\\share\\"])))
  (support/check! context "drive and UNC parents round-trip through portable joins"
         (let [paths ["C:/src"
                      "C:\\src"
                      "C:/src/nested"
                      "C:\\src\\nested"
                      "\\\\server\\share\\item"]]
           (and (every? native/absolute-path?
                        (map native/parent-path paths))
                (= paths
                   (mapv (fn [path]
                           (native/join-path
                            (native/parent-path path)
                            (last (str/split path #"[/\\]"))))
                         paths)))))
  (support/check! context "path joining preserves syntax and treats empty components as identity"
         (= ["/tmp/jolt-hegel/libhegel_c.so"
             "/tmp/jolt-hegel/libhegel_c.so"
             "C:\\src\\jolt-hegel\\libhegel_c.dll"
             "\\\\server\\share\\jolt-hegel\\libhegel_c.dll"
             "src/jolt-hegel/libhegel_c.so"
             "libhegel_c.so"
             "/tmp/jolt-hegel"]
            [(native/join-path "/tmp/jolt-hegel" "libhegel_c.so")
             (native/join-path "/tmp/jolt-hegel/" "libhegel_c.so")
             (native/join-path "C:\\src\\jolt-hegel" "libhegel_c.dll")
             (native/join-path "\\\\server\\share\\jolt-hegel"
                               "libhegel_c.dll")
             (native/join-path "src/jolt-hegel" "libhegel_c.so")
             (native/join-path "" "libhegel_c.so")
             (native/join-path "/tmp/jolt-hegel" "")])))

(defn installer-checksum-contract [context]
  (let [path "hegel-checksum-contract.bin"
        expected (apply str (repeat 64 "a"))
        actual (apply str (repeat 64 "b"))
        hashes (atom 0)
        checksum-matches? @#'install/checksum-matches?
        verify-file! @#'install/verify-file!]
    (with-redefs [install-backend/path-exists? (fn [candidate]
                                                 (= path candidate))
                  install-backend/directory? (constantly false)
                  install-backend/sha256 (fn [_os candidate]
                                           (swap! hashes inc)
                                           (when (= path candidate) actual))]
      (support/check! context "shared checksum policy accepts the backend's exact digest"
             (true? (checksum-matches? path actual)))
      (support/check! context "shared checksum policy rejects a different digest"
             (false? (checksum-matches? path expected)))
      (let [error (try
                    (verify-file! path expected)
                    nil
                    (catch Throwable error error))]
        (support/check! context "checksum mismatch retains expected, actual, and path"
               (= {:type ::install/checksum-mismatch
                   :expected expected
                   :actual actual
                   :path path}
                  (ex-data error)))))
    (let [missing "hegel-checksum-contract-missing.bin"]
      (with-redefs [install-backend/path-exists? (constantly false)
                    install-backend/sha256 (fn [& _]
                                             (swap! hashes inc)
                                             nil)]
        (let [before @hashes
              error (try
                      (verify-file! missing expected)
                      nil
                      (catch Throwable error error))]
          (support/check! context "a missing file fails closed without invoking a digest provider"
                 (and (= before @hashes)
                      (false? (checksum-matches? missing nil))
                      (= before @hashes)
                      (= {:type ::install/checksum-mismatch
                          :expected expected
                          :actual nil
                          :path missing}
                         (ex-data error))))))))
  (let [path (str (:progress-path context) ".checksum")
        abc-sha256
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"]
    (try
      (spit path "abc")
      (support/check! context "selected host digest provider matches the SHA-256 abc vector"
             (= abc-sha256
                (install-backend/sha256 (:os (native/platform)) path)))
      (finally
        (install-backend/delete-file! path)))))

(defn installer-publication-contract [context]
  (let [result (t/run-tests 'hegel.install-publication-test)]
    (support/check! context "installer publication contract suite"
                    (zero? (+ (:fail result) (:error result))))))
