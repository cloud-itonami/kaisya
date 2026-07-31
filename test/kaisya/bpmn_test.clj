(ns kaisya.bpmn-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kaisya.bpmn :as bpmn]))

(deftest processes-are-current
  (testing "the checked-in EDN must equal a fresh extraction — a hand-edit or a
            BPMN change that was not regenerated fails here rather than showing
            a plausible, stale process list on the portal"
    (is (= (bpmn/extract) (bpmn/checked-in))
        "run `clojure -M:emit-processes`")))

(deftest the-legal-process-is-among-them
  (let [ps (bpmn/checked-in)
        legal (first (filter #(= "proc-legal-case" (:process/id %)) ps))]
    (is (some? legal))
    (is (= "法務案件管理" (:process/name legal)))
    (is (= "bpmn/etzhayyim-legal-case-management.bpmn" (:process/source legal)))
    (testing "with its steps, in document order"
      (is (= "法務案件発生" (:step/name (first (:process/steps legal)))))
      (is (= :start (:step/kind (first (:process/steps legal))))))
    (testing "and the CEO approval step is a user task, not a service task"
      (let [approval (first (filter #(= "task-legal-ceo-approval" (:step/id %))
                                    (:process/steps legal)))]
        (is (= :user-task (:step/kind approval)))))))

(deftest layout-and-flow-elements-are-not-steps
  (testing "sequenceFlow and the diagram elements are how it draws, not what the company does"
    (let [ids (set (map :step/id (mapcat :process/steps (bpmn/checked-in))))]
      (is (not (some #(str/starts-with? % "flow-") ids)))
      (is (not (some #(str/starts-with? % "shape-") ids))))))

(deftest every-step-has-a-name
  (testing "falling back to the id rather than rendering an empty row"
    (is (every? #(seq (str (:step/name %)))
                (mapcat :process/steps (bpmn/checked-in))))))
