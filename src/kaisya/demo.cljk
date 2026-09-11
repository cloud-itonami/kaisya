(ns kaisya.demo
  "A sample firm, in the shape `kaisya.contract` describes.

  It lives in `src` rather than `test` for the same reason `lawfirm.demo`
  does: the tests assert against this exact firm **and**
  `kaisya.render-console` renders the sample page from it, so the numbers on
  the demo page are the numbers the suite proves.

  ## Why this is not simply `lawfirm`'s demo output

  It could have been, and the temptation is real — but the two fixtures answer
  different questions. `lawfirm.demo` is one matter mid-flight, sized to
  demonstrate a gate. A portal's job is triage across matters, and a portal
  fixture with one matter cannot show whether 要対応 is ordered correctly,
  because there is nothing to order.

  So this is a three-matter firm with one of each failure the portal exists to
  surface: M-1 has a lapsed 期限 (unrecoverable), M-2 has a stale 送達先 and an
  answer waiting on review (both recoverable), M-3 is clean. What keeps the
  two from drifting is not a shared fixture but
  `contract-test/lawfirms-real-output-satisfies-the-contract`, which runs the
  actual practice projection and checks it against the same contract this
  fixture claims to satisfy."
  (:require [kaisya.contract :as contract]))

(def as-of "2026-07-30")

(def summary
  {:as-of as-of
   :matters
   [{:matter-id "M-1"
     :name "丙山建設との請負代金請求"
     :status :open
     :court "東京地方裁判所"
     :case-number "令和8年(ワ)第1234号"
     :bengoshi-id "B-1"
     :deadlines {:breached 1 :at-risk 0 :pending 2 :satisfied 3
                 :next-due "2026-07-18"}
     :qa {:total 3 :by-state {:answered 3} :answered 3
          :median-response-days 2 :longest-response-days 5}
     :transmissions {:by-channel {:fax 4 :post 2 :hand 1}
                     :unconfirmed 0 :stale-channels 0}}

    {:matter-id "M-2"
     :name "戊田商会 従業員の解雇無効確認"
     :status :open
     :court "東京地方裁判所 立川支部"
     :case-number "令和8年(ワ)第567号"
     :bengoshi-id "B-2"
     :deadlines {:breached 0 :at-risk 2 :pending 1 :satisfied 0
                 :next-due "2026-08-05"}
     :qa {:total 4 :by-state {:answered 2 :awaiting-review 1 :unanswered 1}
          :answered 2 :median-response-days 6 :longest-response-days 11}
     :transmissions {:by-channel {:fax 3 :email 2}
                     :unconfirmed 2 :stale-channels 1}}

    {:matter-id "M-3"
     :name "己川不動産 賃料増額請求への対応"
     :status :open
     :bengoshi-id "B-1"
     :deadlines {:breached 0 :at-risk 0 :pending 1 :satisfied 2
                 :next-due "2026-09-30"}
     :qa {:total 1 :by-state {:answered 1} :answered 1
          :median-response-days 1 :longest-response-days 1}
     :transmissions {:by-channel {:post 2} :unconfirmed 0 :stale-channels 0}}]

   :totals {:matters 3
            :breached 1
            :at-risk 2
            :qa-open 2
            :transmissions-unconfirmed 2
            :stale-channels 1}})

(def pending
  "The approval queue, as the host that runs the actor reports it."
  [{:thread-id "t-2026-07-30-01" :op :transmit-work-product :matter-id "M-2"
    :escalation-reason :counsel-decision :requested-on "2026-07-30"}
   {:thread-id "t-2026-07-30-02" :op :send-qa-answer :matter-id "M-2"
    :escalation-reason :counsel-decision :requested-on "2026-07-29"}
   {:thread-id "t-2026-07-29-07" :op :disburse-trust :matter-id "M-1"
    :escalation-reason :counsel-decision :requested-on "2026-07-29"}])

(def incomplete-summary
  "The same firm with a field the practice never supplied, so the gap panel is
  exercised by something rather than asserted about in the abstract."
  (update summary :totals dissoc :stale-channels))

(def setup
  "Host-owned onboarding projection. The sample challenge is static so the
  rendered review artifact remains byte-identical across runs."
  {:organization {:name "Etzhayyim" :slug "etzhayyim"}
   :steps [{:number 1 :label "Organization" :detail "会社名と owner"
            :status :complete}
           {:number 2 :label "ドメイン" :detail "DNS で所有権を確認"
            :status :current}
           {:number 3 :label "メンバー" :detail "招待と所属を管理"
            :status :blocked}
           {:number 4 :label "仕事道具" :detail "メール・文書・予定を接続"
            :status :blocked}]
   :domain-verification
   {:status :pending
    :domain "etzhayyim.com"
    :record-type "TXT"
    :record-name "_itonami-verification.etzhayyim.com"
    :record-value "itonami-domain-verification=sample-review-token"
    :expires-at "2026-08-12T00:00:00Z"}
   :services [{:name "メール" :description "会社の受信箱と送信元を組織に結び付けます。"
               :status :domain-required}
              {:name "Drive" :description "文書・表・フォームを会社の境界内で共有します。"
               :status :ready}
              {:name "予定" :description "組織の予定と空き時間を調整します。"
               :status :ready}]})

(def public-setup
  "The public host is an entry, not a second identity authority. It hands the
  domain to the resident app where the Passkey session and Organization ledger
  already live; the review fixture above keeps exercising the issued-record
  state independently."
  (-> setup
      (assoc :organization {:name "あなたの会社" :slug "未設定"}
             :handoff-url "http://localhost:1338/#settings"
             :steps [{:number 1 :label "Organization" :detail "会社名と owner"
                      :status :current}
                     {:number 2 :label "ドメイン" :detail "DNS で所有権を確認"
                      :status :blocked}
                     {:number 3 :label "メンバー" :detail "招待と所属を管理"
                      :status :blocked}
                     {:number 4 :label "仕事道具" :detail "メール・文書・予定を接続"
                      :status :blocked}])
      (assoc :domain-verification {:status :not-started :domain ""})))

(comment
  ;; The fixture must satisfy the contract it claims to demonstrate.
  (contract/valid? summary))
