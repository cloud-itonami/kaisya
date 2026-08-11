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
(def setup-out "docs/samples/kaisya-setup.html")
(def site-out "public/index.html")

(defn render []
  (console/render demo/summary
                  {:pending demo/pending
                   :processes (bpmn/checked-in)}))

(defn render-setup []
  (console/render nil {:setup demo/setup}))

(defn render-site []
  (console/render nil {:setup demo/public-setup}))

(defn -main [& [out setup-path]]
  (let [out (or out default-out)
        setup-path (or setup-path setup-out)
        html (render)
        setup-html (render-setup)
        site-html (render-site)]
    (io/make-parents out)
    (spit out html)
    (io/make-parents setup-path)
    (spit setup-path setup-html)
    (io/make-parents site-out)
    (spit site-out site-html)
    (println (str "wrote " out " (" (count html) " bytes)"))
    (println (str "wrote " setup-path " (" (count setup-html) " bytes)"))
    (println (str "wrote " site-out " (" (count site-html) " bytes)"))))
