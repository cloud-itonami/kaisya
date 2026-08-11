(ns kaisya.console-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [design-quality.audit :as dq]
            [kaisya.bpmn :as bpmn]
            [kaisya.console :as console]
            [kaisya.demo :as demo]
            [kaisya.render-console :as render]))

(defn- html [] (render/render))

;; ---------------------------------------------------------------------------
;; The screen shows what the summary says, and nothing it does not
;; ---------------------------------------------------------------------------

(deftest the-portal-renders-the-numbers-it-was-given
  (let [h (html)]
    (is (str/includes? h "会社ポータル"))
    (is (str/includes? h demo/as-of))
    (doseq [m (:matters demo/summary)]
      (is (str/includes? h (:matter-id m))))))

(deftest a-missing-field-renders-as-a-gap-not-as-zero
  (let [h (console/render demo/incomplete-summary
                          {:pending [] :processes (bpmn/checked-in)})]
    (testing "the gap panel appears"
      (is (str/includes? h "サマリの欠落"))
      (is (str/includes? h "stale-channels")))
    (testing "and the metric shows the marker rather than a confident 0"
      (is (str/includes? h "metric-value hig-title2\">—</div>")))
    (testing "while the complete summary shows the real number and no gap panel"
      (let [full (html)]
        (is (not (str/includes? full "サマリの欠落")))))))

(deftest the-gap-panel-is-above-the-numbers
  (testing "so nobody scrolls past it into figures missing their siblings"
    (let [h (console/render demo/incomplete-summary
                            {:pending [] :processes (bpmn/checked-in)})]
      (is (< (str/index-of h "サマリの欠落") (str/index-of h "事務所全体"))))))

(deftest action-required-comes-before-the-matter-list
  (let [h (html)]
    (is (< (str/index-of h "要対応") (str/index-of h "id=\"matters\"")))))

(deftest the-portal-does-not-offer-an-approval-control
  (testing "approving is an act by a 弁護士 in the practice, not a portal button"
    (let [h (html)]
      (is (str/includes? h "承認待ち"))
      (is (not (str/includes? h ":approve")))
      (is (str/includes? h "このポータルは状態を表示するだけ")))))

;; ---------------------------------------------------------------------------
;; Design-system conformance (skill kotoba-uiux)
;; ---------------------------------------------------------------------------

(deftest app-css-is-small-and-unlayered
  (is (< (count console/app-css) 200))
  (is (not (str/includes? console/app-css "@layer")))
  (is (not (str/includes? console/app-css "liquid-glass__"))
      "app CSS is unlayered and already wins — compound selectors are dead weight"))

(deftest the-only-hex-colours-are-in-the-theme-map
  (let [src (slurp "src/kaisya/console.cljc")
        hexes (set (re-seq #"#[0-9A-Fa-f]{6}" src))]
    (is (= #{"#2E5E4E" "#7FCBA8"} hexes)
        "status colours must come from the system palette, never from invented hex")))

(deftest the-page-is-a-complete-japanese-document
  (let [h (html)]
    (is (str/starts-with? h "<!doctype html>"))
    (is (str/includes? h "lang=\"ja\""))
    (is (str/includes? h "viewport"))))

(deftest setup-is-a-state-of-the-same-page
  (let [h (render/render-setup)]
    (is (str/includes? h "会社の仕事場をつくる"))
    (is (str/includes? h "_itonami-verification.etzhayyim.com"))
    (is (str/includes? h "Passkey"))
    (is (str/includes? h "DNS の書き込み権限を Cloud Itonami に渡す必要はありません"))
    (is (not (str/includes? h "期限徒過"))
        "setup and console are states, not two screens rendered at once")))

(deftest the-public-entry-hands-authority-to-the-resident-passkey-session
  (let [h (render/render-site)]
    (is (not (str/includes? h "kaisya.itonami.cloud"))
        "the artifact is mount-independent and does not hard-code its own host")
    (is (str/includes? h "http://localhost:1338/#settings"))
    (is (str/includes? h "name=\"setup-domain\""))
    (is (str/includes? h "公開入口"))
    (is (not (str/includes? h "Owner · Passkey")))
    (is (str/includes? h "この端末の Cloud Itonami で設定"))
    (is (not (str/includes? h "sample-review-token"))
        "the public page must not display a demonstration challenge as proof")))

;; ---------------------------------------------------------------------------
;; The audit gate — an unmeasured page is theater (ADR-2607132300)
;; ---------------------------------------------------------------------------

(def score-floor
  "Set from the measured score when this landed. Fix what the report names;
  never lower the floor to make a regression pass."
  100.0)

(deftest portal-meets-the-hig-wcag-floor
  (let [{:keys [overall findings]}
        (dq/audit {"kaisya-console" (html)
                   "kaisya-setup" (render/render-setup)
                   "kaisya-public" (render/render-site)}
                  {:extra-axes dq/extra-axes})]
    (is (>= overall score-floor)
        (str "score " overall " — " (pr-str (mapv :axis findings))))))

;; ---------------------------------------------------------------------------
;; The sample page is reviewable
;; ---------------------------------------------------------------------------

(deftest rendering-is-deterministic
  (testing "a sample that changes on every run cannot be reviewed in a diff"
    (is (= (html) (html)))))

(deftest the-checked-in-sample-matches-what-the-code-renders
  (is (= (html) (slurp render/default-out))
      "run `clojure -M:render-console` after changing the console")
  (is (= (render/render-setup) (slurp render/setup-out))
      "run `clojure -M:render-console` after changing setup")
  (is (= (render/render-site) (slurp render/site-out))
      "run `clojure -M:render-console` after changing the public site"))
