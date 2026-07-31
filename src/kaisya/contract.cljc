(ns kaisya.contract
  "What a practice must hand the portal, and what happens when it does not.

  ## Why the portal takes data instead of a practice

  `kaisya` renders the company-side view of a practice it does not own. It
  could have depended on `cloud-itonami/lawfirm` and read the store directly;
  it does not, and the reason is not modularity aesthetics. The numbers on
  this portal have to be the numbers the practice's own gate enforces —
  `lawfirm.projection/practice-summary` computes them from the functions the
  governor uses, and anything that recomputed them here would eventually
  disagree with the office console and be believed anyway, because it is the
  screen a director looks at.

  So the portal renders a **projection somebody else computed** and owns
  nothing about what the numbers mean. Any practice actor that can emit this
  shape can be rendered here.

  ## Missing is not zero

  `problems` names what is absent rather than letting the page render a
  confident `0`. A portal that shows 「徒過 0」 because nobody supplied the
  field is worse than one that shows nothing: it is an assurance the record
  never gave. `lawfirm.console` refuses display-only computation for the same
  reason and `cloud-itonami-app` refuses to render an unknown balance as ¥0."
  (:require [clojure.string :as str]))

(def matter-keys
  "Per-matter keys the portal reads. `:name` and `:court` may be absent —
  a matter with no court is normal. The rest carry meaning the portal shows
  as a number, so their absence has to be visible."
  #{:matter-id :status :deadlines :qa :transmissions})

(def total-keys
  #{:matters :breached :at-risk :qa-open :transmissions-unconfirmed
    :stale-channels})

(defn- missing [m ks] (vec (sort (remove #(contains? m %) ks))))

(defn problems
  "Everything about `summary` the portal cannot honestly render, as
  `{:path :missing}` maps. Empty means the shape is complete — it says nothing
  about whether the numbers are *right*, which is the practice's business."
  [summary]
  (cond
    (nil? summary) [{:path [] :missing [:summary] :detail "サマリが渡されていない"}]
    (not (map? summary)) [{:path [] :missing [:summary] :detail "サマリが map ではない"}]
    :else
    (cond-> []
      (str/blank? (str (:as-of summary)))
      (conj {:path [:as-of] :missing [:as-of]
             :detail "基準日が無い。日付の無い数字は現在についての事実ではない"})

      (seq (missing (:totals summary) total-keys))
      (conj {:path [:totals] :missing (missing (:totals summary) total-keys)
             :detail "事務所全体の集計に欠落がある"})

      (not (sequential? (:matters summary)))
      (conj {:path [:matters] :missing [:matters] :detail "事件一覧が無い"})

      :always
      (into (for [m (when (sequential? (:matters summary)) (:matters summary))
                  :let [miss (missing m matter-keys)]
                  :when (seq miss)]
              {:path [:matters (:matter-id m)] :missing miss
               :detail "事件のサマリに欠落がある"})))))

(defn valid? [summary] (empty? (problems summary)))

;; ---------------------------------------------------------------------------
;; Reads — one definition each, shared by the views and the tests
;; ---------------------------------------------------------------------------

(defn action-required
  "The rows a portal exists to surface: what is already wrong, and what is
  about to be. Ordered by how irreversible the failure is — a 徒過 cannot be
  repaired, a stale fax number can be re-verified in a phone call.

  Each row is `{:kind :matter-id :count :label :severity}` with `:severity`
  in `#{:breached :at-risk}` so the view maps it to a palette token rather
  than deciding colour per call site."
  [summary]
  (let [ms (:matters summary)]
    (vec
     (concat
      (for [m ms :when (pos? (get-in m [:deadlines :breached] 0))]
        {:kind :deadline-breached :matter-id (:matter-id m)
         :count (get-in m [:deadlines :breached]) :severity :breached
         :label "期限徒過"})
      (for [m ms :when (pos? (get-in m [:transmissions :stale-channels] 0))]
        {:kind :stale-channel :matter-id (:matter-id m)
         :count (get-in m [:transmissions :stale-channels]) :severity :at-risk
         :label "送達先の再確認"})
      (for [m ms :when (pos? (get-in m [:qa :by-state :awaiting-review] 0))]
        {:kind :qa-awaiting-review :matter-id (:matter-id m)
         :count (get-in m [:qa :by-state :awaiting-review]) :severity :at-risk
         :label "回答の精査待ち"})
      (for [m ms :when (pos? (get-in m [:deadlines :at-risk] 0))]
        {:kind :deadline-at-risk :matter-id (:matter-id m)
         :count (get-in m [:deadlines :at-risk]) :severity :at-risk
         :label "期限間近"})
      (for [m ms :when (pos? (get-in m [:transmissions :unconfirmed] 0))]
        {:kind :transmission-unconfirmed :matter-id (:matter-id m)
         :count (get-in m [:transmissions :unconfirmed]) :severity :at-risk
         :label "送達結果の未確認"})))))

(defn qa-open
  "Questions on a matter that are not yet answered, whatever stage they are
  at. Read from the by-state map rather than from a total, so a state nobody
  taught this function about is not silently counted as done."
  [m]
  (reduce + 0 (map #(get-in m [:qa :by-state %] 0)
                   [:unanswered :awaiting-review :awaiting-send])))
