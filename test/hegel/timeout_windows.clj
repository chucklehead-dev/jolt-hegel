(ns hegel.timeout-windows
  "Test-only Windows child supervision that bypasses Jolt's POSIX process shim."
  (:require [clojure.string :as str]
            [jolt.ffi :as ffi]))

(ffi/load-library)

(def ^:private c-system
  (ffi/foreign-fn "system" [:string] :int))

(defn- powershell-literal [value]
  (str "'" (str/replace value "'" "''") "'"))

(defn- windows-command [arguments]
  ;; cmd.exe requires an outer quote when the executable is quoted. This is
  ;; the same direct libc/PowerShell boundary used by hegel.install.jolt.
  (str "\""
       (str/join " "
                 (map #(str "\"" (str/replace % "\"" "\\\"") "\"")
                      arguments))
       "\""))

(defn run-child!
  "Run the released jolt.exe through PowerShell, with bounded wait and forced
  reap. `output-file` is deliberately relative: Jolt's Windows File shim does
  not safely consume drive-rooted paths after the child exits."
  [arguments output-file wait-ms reap-ms]
  (let [error-file (str output-file ".stderr")
        script (str "$ErrorActionPreference='Stop';"
                    "$p=Start-Process -FilePath " (powershell-literal (first arguments))
                    " -ArgumentList @(" (str/join "," (map powershell-literal (rest arguments))) ")"
                    " -PassThru -NoNewWindow -RedirectStandardOutput "
                    (powershell-literal output-file)
                    " -RedirectStandardError " (powershell-literal error-file) ";"
                    "if(-not $p.WaitForExit(" wait-ms ")){"
                    ;; Windows PowerShell 5.1 only supports Kill(), unlike
                    ;; newer pwsh's Kill(bool). jolt.exe is the direct child.
                    "$p.Kill();"
                    "if(-not $p.WaitForExit(" reap-ms ")){exit 125};"
                    "exit 124};"
                    "if($null -eq $p.ExitCode){exit 126};"
                    "exit [int]$p.ExitCode")
        exit (c-system (windows-command ["powershell.exe" "-NoLogo" "-NoProfile"
                                         "-NonInteractive" "-Command" script]))]
    {:command ["powershell.exe" "-NoLogo" "-NoProfile" "-NonInteractive"
               "-Command" script]
     :forced? (= 124 exit)
     :exit exit
     :output-file output-file
     ;; Keep stderr separate: Windows PowerShell's text append encoding would
     ;; corrupt the UTF-8 progress stream that the parent parses.
     :error-file error-file}))
