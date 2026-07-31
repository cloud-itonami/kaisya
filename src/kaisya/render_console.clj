(ns kaisya.render-console
  "Render the sample portal to `docs/samples/kaisya-console.html`.

  Deterministic: the fixture is static data and the processes come from the
  checked-in generated EDN, so two consecutive runs are byte-identical. No
  clock, no randomness — `render-console-test/rendering-is-deterministic`
  holds this, because a sample page that changes on every run cannot be
  reviewed in a diff."
  (:require [clojure.java.io :as io]
            [kaisya.bpmn :as bpmn]
            [kaisya.console :as console]
            [kaisya.demo :as demo]))

(def default-out "docs/samples/kaisya-console.html")

(defn render []
  (console/render demo/summary
                  {:pending demo/pending
                   :processes (bpmn/checked-in)}))

(defn -main [& [out]]
  (let [out (or out default-out)
        html (render)]
    (io/make-parents out)
    (spit out html)
    (println (str "wrote " out " (" (count html) " bytes)"))))
