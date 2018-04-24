(ns cli-matic.core
  (:require [cli-matic.specs :as S]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.spec.alpha :as s]
            [orchestra.spec.test :as st]
            ))

;
; Cli-matic has one main entry-point: run!
; Actually, most of the logic will be run in run*
; to make testing easier.
;


(defn exception [s]
  (prn "Exception: " s))

;
; Known presets
;

(defn parseInt [s]
  (Integer/parseInt s))


(def known-presets
  {:int {:parse-fn parseInt
         :placeholder "N"}
   :string {:placeholder "S"}})


;
; Rewrite options to our format
;
; {:opt "x" :as "Port number" :type :int}
; ["-x" nil "Port number"
;     :parse-fn #(Integer/parseInt %)]

(defn mk-cli-option
  [{:keys [option shortened as type] :as cm-option}]
  (let [preset (get known-presets type :unknown)
        head [(if (string? shortened)
                (str "-" shortened)
                nil)
              (str "--" option " " (:placeholder preset))
              as]

        opts  (dissoc preset :placeholder)]

    (apply
      conj head
      (flatten (seq opts)))

    ))


(s/fdef
  mk-cli-option
  :args (s/cat :opts ::S/climatic-option)
  :ret some?)


(defn get-subcommand
  [climatic-args subcmd]
  (let [subcommands (:commands climatic-args)]
    (first (filter #(= (:command %) subcmd) subcommands))))

(s/fdef
  get-subcommand
  :args (s/cat :args ::S/climatic-cfg :subcmd string?)
  :ret ::S/a-command)


(defn all-subcommands
  "Returns all subcommands, as strings"
  [climatic-args]
  (let [subcommands (:commands climatic-args)]
    (into #{}
      (map :command subcommands))))

(s/fdef
  all-subcommands
  :args (s/cat :args ::S/climatic-cfg)
  :ret set?)


;
; Out of a cli-matic arg list,
; generates a set of commands for tools.cli
;

(defn rewrite-opts
  [climatic-args subcmd]

  (let [opts (if (nil? subcmd)
               (:global-opts climatic-args)
               (:opts (get-subcommand climatic-args subcmd)))]
    (map mk-cli-option opts)))


(s/fdef
  rewrite-opts
  :args (s/cat :args some?
               :mode (s/or :common nil?
                           :a-subcommand string?))
  :ret some?)



;
; We parse our command line here
;
;

(defn mkError
  [config subcommand error text]
  {:subcommand     subcommand
   :subcommand-def (if (or (= error :ERR-UNKNOWN-SUBCMD)
                           (= error :ERR-NO-SUBCMD)
                           (= error :ERR-PARMS-COMMON))
                     nil
                     (get-subcommand config subcommand))
   :commandline    {}
   :parse-errors   error
   :error-text     text
   })


(defn parse-cmds
  [cmdline config]

  (let [possible-subcmds (all-subcommands config)

        cli-gl-options (rewrite-opts config nil)
        ;_ (prn "Options" cli-top-options)
        parsed-gl-opts (parse-opts cmdline cli-gl-options :in-order true)
        ;_ (prn "Common cmdline" parsed-common-cmdline)

        {gl-errs :errors gl-opts :options gl-args :arguments} parsed-gl-opts]

    (cond
      (some? gl-errs)
      (mkError config nil :ERR-PARMS-COMMON "")

      :else
      (let [subcommand (first gl-args)
            subcommand-parms (vec (rest gl-args))]

        (cond
          (nil? subcommand)
          (mkError config nil :ERR-NO-SUBCMD "")

          (nil? (possible-subcmds subcommand))
          (mkError config subcommand :ERR-UNKNOWN-SUBCMD "")

          :else
          (let [cli-cmd-options (rewrite-opts config subcommand)
                parsed-cmd-opts (parse-opts subcommand-parms cli-cmd-options)
                ;_ (prn "Subcmd cmdline" parsed-subcmd-cmdline)
                {cmd-errs :errors cmd-opts :options cmd-args :arguments} parsed-cmd-opts]

            (if (nil? cmd-errs)

              {:subcommand     subcommand
               :subcommand-def (get-subcommand config subcommand)
               :commandline     (into
                                  (into gl-opts cmd-opts)
                                  {:_arguments cmd-args})
               :parse-errors    :NONE
               :error-text     ""
               }
              )

          ))))))


(s/fdef
  parse-cmds
  :args (s/cat :args (s/coll-of string?)
               :opts ::S/climatic-cfg)
  :ret ::S/lineParseResult
  )


;
; builds a return value
;



(defn ->RV
  [return-code type stdout subcmd stderr]
  (let [fnStrVec (fn [s]
                   (cond
                     (nil? s) []
                     (string? s) [s]
                     :else  s ))]

  {:retval return-code
   :status type
   :stdout stdout
   :subcmd subcmd
   :stderr (fnStrVec stderr)}
  ))

(s/fdef
  ->RV
  :args (s/cat :rv int? :status some? :stdout any? :subcmd any? :stderr any?)
  :rets ::S/RV)



;
; Invokes a subcommand.
;
; The subcommand may:
; - return an integer (to specify exit code)
; - return nil
; - throw a Throwable object
;

(defn invoke-subcmd
  [subcommand-def options]

  (try
    (let [rv ((:runs subcommand-def)  options)]
      (cond
        (nil? rv)    (->RV 0 :OK nil nil nil)
        (zero? rv)   (->RV 0 :OK nil nil nil)
        (int? rv)    (->RV rv :ERR nil nil nil)
        :else        (->RV -1 :ERR nil nil nil)
        ))

    (catch Throwable t
      (->RV -1 :EXCEPTION nil nil "**exception**")
      )
    ))


;
; Executes our code.
; It will try and parse the arguments via clojure.tools.cli
; and detect our subcommand.

; If no subcommand was found, it will print the error reminder.
; On exceptions, it will raise an exception message.
;

(defn run-cmd*
  [setup args]

    (let [parsed-opts (parse-cmds args setup)]

      ; maybe there was an error parsing
      (condp = (:parse-errors parsed-opts)

        :ERR-CFG (->RV -1 :ERR-CFG nil nil  "Error in cli-matic configuration")
        :ERR-NO-SUBCMD (->RV -1 :ERR-NO-SUBCMD nil nil "No sub-command specified")
        :ERR-UNKNOWN-SUBCMD (->RV -1 :ERR-UNKNOWN-SUBCMD nil nil "Unknown sub-command")
        :HELP-COMMON (->RV -1 :OK nil nil nil)
        :ERR-PARMS-COMMON (->RV -1 :ERR-PARMS-COMMON nil nil "Error: ")
        :HELP-SUBCMD (->RV -1 :OK nil nil nil)
        :ERR-PARMS-SUBCMD (->RV -1 :ERR-PARMS-SUBCMD nil nil "Error: ")

        :NONE (invoke-subcmd (:subcommand-def parsed-opts) (:commandline parsed-opts))

        )))


(defn run-cmd
  [args setup]

  (let [result (run-cmd* setup args)]
  ;  (System.out/println  (:stdout result))
  ;  (System.err/println  (:stderr result))
    (System/exit (:retval result))))



(st/instrument)