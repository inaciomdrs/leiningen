(ns leiningen.trampoline
  (:refer-clojure :exclude [trampoline])
  (:require [clojure.string :as string]
            [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [clojure.pprint :as pprint]))

(def ^:dynamic *trampoline?* false)

(defn win-batch? []
  (.endsWith (System/getProperty "leiningen.trampoline-file") ".bat"))

(defn write-trampoline [command]
  (when (System/getenv "DEBUG")
    (println "Trampoline-command:" command)
    (flush))
  (spit (System/getProperty "leiningen.trampoline-file")
        (string/join " " (if (win-batch?)
                           command
                           (conj (vec (butlast command))
                                 (with-out-str (prn (last command))))))))

(defn trampoline
  "Run a task without nesting the project's JVM inside Leiningen's.

Calculates what needs to run in the project's process for the provided
task and runs it after Leiningen's own JVM process has exited rather
than as a subprocess of Leiningen's project.

Use this to save memory or to work around things like stdin issues.
Not compatible with chaining."
  [project task-name & args]
  (let [command (promise)]
    (when (:eval-in-leiningen project)
      (println "Warning: trampoline has no effect with :eval-in-leiningen."))
    (binding [*trampoline?* true]
      (main/apply-task task-name (assoc project
                                   :eval-in :trampoline
                                   :trampoline-promise command) args))
    (if (realized? command)
      (write-trampoline @command)
      (main/abort task-name "did not run any project code for trampolining."))))

