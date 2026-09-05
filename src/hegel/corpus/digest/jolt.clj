(ns hegel.corpus.digest.jolt
  "Jolt-only platform SHA-256 adapter, independent of the Hegel native engine."
  (:require [clojure.string :as str]
            [jolt.ffi :as ffi]))

;; OpenSSL SHA256 and Windows bcrypt.h signatures. NTSTATUS and ULONG remain
;; 32-bit on Windows x64; do not substitute platform-sized size_t for ULONG.
;; https://docs.openssl.org/3.0/man3/SHA256_Init/
;; https://learn.microsoft.com/windows/win32/api/bcrypt/nf-bcrypt-bcrypthash
(def ^:private openssl-hash
  (ffi/foreign-fn "SHA256" [:pointer :size_t :pointer] :pointer))
(def ^:private cng-open
  (ffi/foreign-fn "BCryptOpenAlgorithmProvider"
                  [:pointer :pointer :pointer :uint32] :int32))
(def ^:private cng-hash
  (ffi/foreign-fn "BCryptHash"
                  [:pointer :pointer :uint32 :pointer :uint32 :pointer :uint32]
                  :int32))
(def ^:private cng-close
  (ffi/foreign-fn "BCryptCloseAlgorithmProvider" [:pointer :uint32] :int32))

(defn- native-error! [operation status]
  (throw (ex-info "platform corpus digest failed"
                  {:type ::digest-failed :operation operation :status status})))

(defn- success! [operation status]
  (when-not (zero? status) (native-error! operation status)))

(def ^:private windows?
  (str/starts-with? (System/getProperty "os.name") "Windows"))

(def ^:private platform-library
  (delay
    (if windows?
      ;; Use the OS library, not a file selected relative to a corpus directory.
      (if-let [root (System/getenv "SystemRoot")]
        (ffi/load-library (str root "\\System32\\bcrypt.dll"))
        (throw (ex-info "SystemRoot is required for the Windows corpus digest"
                        {:type ::crypto-unavailable})))
      (let [candidates (if (= "Mac OS X" (System/getProperty "os.name"))
                         ["/opt/homebrew/opt/openssl@3/lib/libcrypto.dylib"
                          "/usr/local/opt/openssl@3/lib/libcrypto.dylib"
                          "libcrypto.dylib"]
                         ["libcrypto.so.3" "libcrypto.so.1.1" "libcrypto.so"])]
        (when-not (some (fn [path]
                          (try (ffi/load-library path) true
                               (catch Throwable _ false)))
                        candidates)
          (throw (ex-info "OpenSSL libcrypto is required for the corpus digest"
                          {:type ::crypto-unavailable})))))))

(defn- with-provider [open! close! body]
  ;; Preserve the primary hash/read error if cleanup also reports a failure.
  ;; No handle cache or process-global SHA output buffer is introduced.
  (let [handle (open!)
        outcome (try {:value (body handle)}
                     (catch Throwable error {:error error}))
        closed (try (close! handle) nil
                    (catch Throwable error error))]
    (cond
      (:error outcome) (throw (:error outcome))
      closed (throw closed)
      :else (:value outcome))))

(defn- windows-digest [source length output]
  (ffi/with-alloc [algorithm 14]
    ;; L"SHA256", explicitly UTF-16LE; :string would incorrectly pass char*.
    (ffi/write-array algorithm (byte-array [83 0 72 0 65 0 50 0 53 0 54 0 0 0]))
    (ffi/with-alloc [holder (ffi/sizeof :pointer)]
      (ffi/write holder :pointer ffi/null)
      (with-provider
        (fn []
          (success! :open (cng-open holder algorithm ffi/null 0))
          (let [handle (ffi/read holder :pointer)]
            (when (ffi/null? handle) (native-error! :open :null-handle))
            handle))
        (fn [handle] (success! :close (cng-close handle 0)))
        (fn [handle]
          (success! :hash (cng-hash handle ffi/null 0 source length output 32))
          (ffi/read-array output 32))))))

(defn sha256-bytes
  "Hash at most 1 MiB of octets. Return an owned 32-byte host array.
  Windows uses CNG (Windows 10 / Server 2016+); POSIX uses system OpenSSL."
  [bytes]
  (let [length (alength bytes)]
    (when (> length 1048576)
      (throw (ex-info "corpus digest byte input exceeds its bound"
                      {:type ::input-too-large :hegel/usage-error? true})))
    @platform-library
    (ffi/with-alloc [source (max 1 length)]
      (ffi/with-alloc [output 32]
        (ffi/write-array source bytes)
        (if windows?
          (windows-digest source length output)
          (do
            (when (ffi/null? (openssl-hash source length output))
              (native-error! :hash :null-output))
            (ffi/read-array output 32)))))))
