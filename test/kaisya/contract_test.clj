(ns kaisya.contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [kaisya.contract :as contract]
            [kaisya.demo :as demo]
            [lawfirm.demo :as lawfirm-demo]
            [lawfirm.projection :as projection]))

;; ---------------------------------------------------------------------------
;; The contract is checked against a real practice, not only against itself
;; ---------------------------------------------------------------------------

(deftest lawfirms-real-output-satisfies-the-contract
  (testing "a contract nobody runs against a real producer is a comment"
    (let [summary (projection/practice-summary (lawfirm-demo/fresh-store)
                                               lawfirm-demo/today)]
      (is (contract/valid? summary) (pr-str (contract/problems summary))))))

(deftest the-portals-derived-reads-work-on-the-real-output
  (let [summary (projection/practice-summary (lawfirm-demo/fresh-store)
                                             lawfirm-demo/today)
        rows (contract/action-required summary)]
    (testing "the practice's stale fax number reaches the portal as a row"
      (is (some #(= :stale-channel (:kind %)) rows)))
    (testing "and its unconfirmed 送達 does too"
      (is (some #(= :transmission-unconfirmed (:kind %)) rows)))
    (testing "the open-question count matches the practice's own metric"
      (is (= (get-in summary [:totals :qa-open])
             (reduce + 0 (map contract/qa-open (:matters summary))))))))

(deftest the-fixture-satisfies-the-contract-it-demonstrates
  (is (contract/valid? demo/summary) (pr-str (contract/problems demo/summary))))

;; ---------------------------------------------------------------------------
;; Missing is not zero
;; ---------------------------------------------------------------------------

(deftest a-missing-field-is-reported-rather-than-defaulted
  (let [ps (contract/problems demo/incomplete-summary)]
    (is (= 1 (count ps)))
    (is (= [:totals] (:path (first ps))))
    (is (= [:stale-channels] (:missing (first ps))))))

(deftest a-missing-as-of-is-a-problem
  (testing "a number with no date is not a fact about now"
    (is (seq (contract/problems (dissoc demo/summary :as-of))))))

(deftest nil-and-non-maps-are-reported-not-crashed-on
  (is (seq (contract/problems nil)))
  (is (seq (contract/problems "サマリではない")))
  (is (seq (contract/problems (dissoc demo/summary :matters)))))

(deftest a-matter-missing-its-numbers-is-named
  (let [broken (update demo/summary :matters
                       (fn [ms] (conj (vec (rest ms)) {:matter-id "M-X"})))
        ps (contract/problems broken)]
    (is (some #(= [:matters "M-X"] (:path %)) ps))
    (is (some #(= #{:deadlines :qa :status :transmissions} (set (:missing %))) ps))))

;; ---------------------------------------------------------------------------
;; 要対応 ordering
;; ---------------------------------------------------------------------------

(deftest the-unrecoverable-failure-comes-first
  (testing "a lapsed 期限 cannot be repaired; a stale fax number is a phone call"
    (let [rows (contract/action-required demo/summary)]
      (is (= :deadline-breached (:kind (first rows))))
      (is (= "M-1" (:matter-id (first rows))))
      (is (= :breached (:severity (first rows))))
      (is (every? #(= :at-risk (:severity %)) (rest rows))))))

(deftest a-clean-firm-produces-no-rows
  (let [clean (assoc demo/summary
                     :matters [(nth (:matters demo/summary) 2)]
                     :totals {:matters 1 :breached 0 :at-risk 0 :qa-open 0
                              :transmissions-unconfirmed 0 :stale-channels 0})]
    (is (empty? (contract/action-required clean)))))

(deftest qa-open-counts-every-unfinished-state
  (testing "read from by-state, so a state nobody taught it about is not counted as done"
    (is (= 0 (contract/qa-open {:qa {:by-state {:answered 3}}})))
    (is (= 3 (contract/qa-open {:qa {:by-state {:unanswered 1 :awaiting-review 1
                                                :awaiting-send 1 :answered 9}}})))
    (is (= 0 (contract/qa-open {})))))
