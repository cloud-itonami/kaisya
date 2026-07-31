(ns kaisya.console
  "会社ポータル — the company-side view of a practice the portal does not own.

  Pure `.cljc` hiccup on the kotoba-ui stack (skill `kotoba-uiux`,
  ADR-2607122200): `kotoba-ui.core` + `appkit.core` are the only UI requires,
  every colour and type size is a token, and layout comes from the shell
  scaffolds. The same view renders server-side through `->page` and mounts in
  a browser through shitsuke's reagent seam.

  ## What this screen is for

  The office console (`cloud-itonami/lawfirm`) answers *what do I do next on
  this matter*. This one answers *what is the firm carrying, and what is
  already wrong* — so 要対応 comes before the matter list, and the most
  irreversible failure comes first inside it. A 期限 that lapsed cannot be
  repaired; a fax number that went stale is a phone call.

  ## It computes nothing

  Every number here arrives in the summary
  (`lawfirm.projection/practice-summary`, computed from the functions the
  practice's governor gates on). `kaisya.contract` is the shape, and a field
  that is *missing* renders as a stated gap rather than as a confident zero —
  a portal that shows 「徒過 0」 because nobody supplied the field has invented
  an assurance."
  (:require [appkit.core :as app]
            [clojure.string :as str]
            [kaisya.contract :as contract]
            [kotoba-ui.core :as ui]))

(def theme
  "One map, per rule 5 of the agent guide. The accent is the only place a hex
  belongs in app code; `:auto` lets the viewer's system decide light or dark."
  {:accent "#2E5E4E" :accent-dark "#7FCBA8" :appearance :auto})

(def ^:private severity-token
  "System palette tokens, never invented hex."
  {:breached "var(--hig-palette-red)"
   :at-risk "var(--hig-palette-orange)"
   :ok "var(--hig-palette-green)"
   :unknown "var(--hig-palette-purple)"})

(defn- chip [severity label]
  [:span {:class "ks-status" :style {:color (get severity-token severity)}} label])

(defn- n-or-gap
  "A count, or the marker for a field the practice did not supply. `0` and
  `nil` must not look the same — one is a measurement, the other is silence."
  [x]
  (if (number? x) (str x) "—"))

;; ---------------------------------------------------------------------------
;; Gaps
;; ---------------------------------------------------------------------------

(defn gaps-view
  "Shown only when the summary is incomplete. Placed above everything so a
  reader never scrolls past it into numbers that are missing their siblings."
  [summary]
  (let [ps (contract/problems summary)]
    (when (seq ps)
      (ui/section
       {:title "サマリの欠落" :wide true :id "gaps"}
       (app/panel
        [(ui/data-table
          {:columns [{:key :where :label "箇所"}
                     {:key :missing :label "欠けているもの"}
                     {:key :detail :label ""}]
           :rows (vec (for [p ps]
                        {:where (str/join " / " (map str (:path p)))
                         :missing (str/join "・" (map name (:missing p)))
                         :detail (:detail p)}))})
         [:p {:class "hig-caption1 ks-muted"}
          "欠けている値は 0 として表示しません。渡されなかった数字を 0 と書くのは、"
          "記録が与えていない保証を画面が与えることになります。"]])))))

;; ---------------------------------------------------------------------------
;; 全体
;; ---------------------------------------------------------------------------

(defn totals-view [summary]
  (let [t (:totals summary)]
    (ui/section
     {:title "事務所全体" :wide true :id "totals"}
     (ui/grid
      {:min "170px"}
      (ui/metric {:label "事件" :value (n-or-gap (:matters t))})
      (ui/metric {:label "徒過" :value (n-or-gap (:breached t))
                  :status (when (pos? (or (:breached t) 0)) "回復不能")})
      (ui/metric {:label "期限間近" :value (n-or-gap (:at-risk t))})
      (ui/metric {:label "未処理の相談" :value (n-or-gap (:qa-open t))})
      (ui/metric {:label "要再確認の宛先" :value (n-or-gap (:stale-channels t))
                  :status (when (pos? (or (:stale-channels t) 0)) "送達前に確認")})
      (ui/metric {:label "結果未確認の送達"
                  :value (n-or-gap (:transmissions-unconfirmed t))})))))

;; ---------------------------------------------------------------------------
;; 要対応
;; ---------------------------------------------------------------------------

(defn action-view
  "Ordered by how irreversible the failure is, not by how many there are."
  [summary]
  (let [rows (contract/action-required summary)]
    (ui/section
     {:title "要対応" :wide true :id "action"}
     (app/panel
      [(ui/data-table
        {:caption "回復不能なものから順に"
         :columns [{:key :severity :label "区分"}
                   {:key :label :label "内容"}
                   {:key :matter :label "事件"}
                   {:key :count :label "件数"}
                   {:key :action :label ""}]
         :rows (vec (for [r rows]
                      {:severity (chip (:severity r)
                                       (if (= :breached (:severity r)) "回復不能" "要注意"))
                       :label (:label r)
                       :matter (:matter-id r)
                       :count (str (:count r))
                       :action (ui/button "事件を開く" {:act [:open-matter (:matter-id r)]})}))
         :empty (ui/empty-state
                 {:title "要対応はありません"
                  :body "期限の徒過・期限間近・送達先の再確認・回答の精査待ち・送達結果の未確認がいずれもありません。"})})
       [:p {:class "hig-caption1 ks-muted"}
        "期限の徒過は後から回復できません。送達先の再確認は電話1本で終わります。"
        "並び順はその差を表しています。"]]))))

;; ---------------------------------------------------------------------------
;; 事件一覧
;; ---------------------------------------------------------------------------

(defn matters-view [summary]
  (ui/section
   {:title "事件" :wide true :id "matters"}
   (app/panel
    [(ui/data-table
      {:columns [{:key :id :label "事件"}
                 {:key :name :label "件名"}
                 {:key :status :label "状態"}
                 {:key :next :label "次の期限"}
                 {:key :dl :label "徒過 / 間近"}
                 {:key :qa :label "未処理の相談"}]
       :rows (vec (for [m (:matters summary)]
                    {:id (:matter-id m)
                     :name (or (:name m) "—")
                     :status (ui/badge (if (:status m) (name (:status m)) "—"))
                     :next (or (get-in m [:deadlines :next-due]) "—")
                     :dl (let [b (get-in m [:deadlines :breached])]
                           (chip (if (pos? (or b 0)) :breached :ok)
                                 (str (n-or-gap b) " / "
                                      (n-or-gap (get-in m [:deadlines :at-risk])))))
                     :qa (str (contract/qa-open m))}))
       :empty (ui/empty-state {:title "事件がありません"})})])))

;; ---------------------------------------------------------------------------
;; 承認待ち
;; ---------------------------------------------------------------------------

(defn approvals-view
  "Operations parked for 弁護士 sign-off. Passed in rather than derived: the
  checkpoint store belongs to whoever runs the actor, and a portal that
  guessed the queue from a ledger would show approvals that were already
  granted."
  [pending]
  (ui/section
   {:title "承認待ち" :wide true :id "approvals"}
   (app/panel
    [(ui/data-table
      {:columns [{:key :op :label "操作"}
                 {:key :matter :label "事件"}
                 {:key :reason :label "理由"}
                 {:key :since :label "起案日"}]
       :rows (vec (for [p pending]
                    {:op (some-> (:op p) name)
                     :matter (:matter-id p)
                     :reason (case (:escalation-reason p)
                               :counsel-decision "弁護士の判断を要する操作"
                               :low-confidence "確信度が閾値未満"
                               "—")
                     :since (:requested-on p)}))
       :empty (ui/empty-state {:title "承認待ちはありません"})})
     [:p {:class "hig-caption1 ks-muted"}
      "承認は弁護士本人が事務所コンソールで行います。このポータルは状態を表示するだけで、"
      "承認の操作は持ちません。"]])))

;; ---------------------------------------------------------------------------
;; 業務プロセス
;; ---------------------------------------------------------------------------

(defn processes-view
  "The company's registered process definitions, generated from `bpmn/*.bpmn`
  by `kaisya.bpmn`. Named, not executed — the portal shows what the company
  says its work looks like; the practice actor is what actually gates it."
  [processes]
  (ui/section
   {:title "業務プロセス" :wide true :id "processes"}
   (ui/grid
    {:min "320px"}
    (for [p processes]
      (app/panel
       [[:h3 {:class "hig-headline"} (:process/name p)]
        [:p {:class "hig-caption1 ks-muted"} (:process/source p)]
        (ui/list-view
         (for [s (:process/steps p)]
           (ui/list-row [:span (:step/name s)]
                        {:trailing (ui/badge (name (:step/kind s)))})))])))
   [:p {:class "hig-caption1 ks-muted"}
    "この一覧は BPMN から生成しています（`clojure -M:emit-processes`）。"
    "手で書き写すと図を編集した瞬間に静かにずれます。"]))

;; ---------------------------------------------------------------------------
;; Page
;; ---------------------------------------------------------------------------

(def app-css
  "Unlayered, so it wins over `@layer kotoba.hig, kotoba.glass` without a
  single compound selector. Two rules — anything more would mean the shell is
  missing a scaffold and should be extended upstream instead."
  (str ".ks-muted{color:var(--hig-color-secondary-label)}"
       ".ks-status{font-weight:600}"))

(defn view
  "The whole portal. `opts`: `:pending` (approval queue), `:processes`
  (generated BPMN definitions)."
  [summary {:keys [pending processes] :as _opts}]
  (ui/app-shell
   {:nav (ui/nav-bar "会社ポータル"
                     {:trailing [(ui/badge (or (:as-of summary) "基準日なし"))]})}
   (gaps-view summary)
   (totals-view summary)
   (action-view summary)
   (matters-view summary)
   (approvals-view pending)
   (processes-view processes)))

(defn render
  "Complete HTML document."
  [summary opts]
  (ui/->page {:title "会社ポータル"
              :description "事務所が何を抱えていて、何が既に問題になっているかを示す会社側の画面。"
              :lang "ja"
              :theme theme
              :head [[:style app-css]]}
             (view summary opts)))
