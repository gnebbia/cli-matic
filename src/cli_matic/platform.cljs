(ns cli-matic.platform
  "
  ## Platform-specific functions for Node.

  At the moment it's all empty.

  BTW, in this NS, we avoid using Spec / Orchestra.

  ")

(defn read-env
  "Reads an environment variable.
  If undefined, returns nil."
  [var]
  nil)

(defn exit-script
  "Terminates execution with a return value."
  [retval]
  nil)

(defn add-shutdown-hook
  "Add a shutdown hook.

  Does not work (?) on CLJS.
  "
  [fnToCallOnShutdown]

  nil)

;
; Conversions
;

(defn parseInt
  "Converts a string to an integer. "
  [s]
  nil)

(defn parseFloat
  "Converts a string to a float."
  [s]
  nil)

(defn asDate
  "Converts a string in format yyyy-mm-dd to a
  Date object; if conversion
  fails, returns nil."
  [s]
  nil)

(defn parseEdn
      "
      See https://stackoverflow.com/questions/44661385/how-do-i-read-an-edn-file-from-clojurescript-running-on-nodejs
      "
      [edn-in]
      nil)