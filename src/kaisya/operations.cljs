(ns kaisya.operations
  "Company records for an empty kaisya tenant. This is a pure transition layer:
  storage and authentication belong to the host. Every accepted command adds
  one audit event; rejected commands leave the state unchanged. Amounts are
  integer yen. Posted journal entries and raw punches are never edited."
  (:require [kotoba.lang.text :as str]))

(defn empty-company [organization-id]
  {:organization-id organization-id
   :journal [] :punches [] :register [] :audit []
   :closed-through nil})

(defn- fail [reason] {:ok false :reason reason})
(defn- nonblank? [x] (and (string? x) (not (str/blank? x))))
(defn- date? [x]
  (and (string? x)
       (boolean (re-matches #"\d{4}-\d{2}-\d{2}" x))
       (let [parsed (js/Date. (str x "T00:00:00Z"))]
         (and (not (js/isNaN (.getTime parsed)))
              (= x (.slice (.toISOString parsed) 0 10))))))
(defn- unique-id? [state id]
  (not-any? #(= id (:id %)) (:audit state)))
(defn- valid-command? [state command]
  (and (map? command)
       (nonblank? (:id command))
       (nonblank? (:actor command))
       (nonblank? (:at command))
       (= (:organization-id state) (:organization-id command))
       (unique-id? state (:id command))))

(defn- append-audit [state command]
  (update state :audit conj
          (select-keys command [:id :kind :actor :at :organization-id])))

(defn- journal-lines-valid? [lines]
  (and (vector? lines) (<= 2 (count lines))
       (every? (fn [{:keys [account side amount-yen]}]
                 (and (nonblank? account)
                      (#{:debit :credit} side)
                      (js/Number.isSafeInteger amount-yen) (pos? amount-yen)))
               lines)
       (= (reduce + 0 (map :amount-yen (filter #(= :debit (:side %)) lines)))
          (reduce + 0 (map :amount-yen (filter #(= :credit (:side %)) lines))))))

(defn- post-journal [state {:keys [date lines source-id] :as command}]
  (cond
    (not (date? date)) (fail :invalid-date)
    (and (:closed-through state) (<= (compare date (:closed-through state)) 0))
    (fail :period-closed)
    (not (journal-lines-valid? lines)) (fail :unbalanced-or-invalid-lines)
    (not (nonblank? source-id)) (fail :source-required)
    (some #(= source-id (:source-id %)) (:journal state)) (fail :duplicate-source)
    :else {:ok true
           :state (-> state
                      (update :journal conj
                              (select-keys command [:id :date :lines :source-id
                                                    :reverses-id :actor :at]))
                      (append-audit command))}))

(defn- close-period [state {:keys [through] :as command}]
  (cond
    (not (date? through)) (fail :invalid-date)
    (and (:closed-through state) (<= (compare through (:closed-through state)) 0))
    (fail :period-already-closed)
    :else {:ok true :state (-> state
                              (assoc :closed-through through)
                              (append-audit command))}))

(defn- reverse-journal [state {:keys [reverses-id date] :as command}]
  (if-let [original (some #(when (= reverses-id (:id %)) %) (:journal state))]
    (if (some #(= reverses-id (:reverses-id %)) (:journal state))
      (fail :already-reversed)
      (post-journal state
                    (assoc command
                           :source-id (str "reversal:" reverses-id)
                           :date date
                           :lines (mapv (fn [line]
                                          (update line :side
                                                  {:debit :credit :credit :debit}))
                                        (:lines original)))))
    (fail :original-not-found)))

(defn- record-punch [state {:keys [worker-id direction instant-ms] :as command}]
  ;; Preserve even an unmatched clock-out. It is evidence of an anomaly, and
  ;; cannot be silently discarded to make the period appear clean.
  (if (and (nonblank? worker-id)
           (#{:in :out} direction)
           (js/Number.isSafeInteger instant-ms) (pos? instant-ms))
    {:ok true :state (-> state
                         (update :punches conj
                                 (select-keys command
                                              [:id :worker-id :direction :instant-ms :actor :at]))
                         (append-audit command))}
    (fail :invalid-punch)))

(defn- append-register [state {:keys [register-type subject-id evidence-id] :as command}]
  (if (and (#{:employee :asset :obligation :document :wage} register-type)
           (nonblank? subject-id) (nonblank? evidence-id))
    {:ok true :state (-> state
                         (update :register conj
                                 (select-keys command
                                              [:id :register-type :subject-id :evidence-id
                                               :value :actor :at]))
                         (append-audit command))}
    (fail :invalid-register-record)))

(defn apply-command [state command]
  (if-not (valid-command? state command)
    (fail :invalid-envelope)
    (case (:kind command)
      :journal/post (post-journal state command)
      :journal/reverse (reverse-journal state command)
      :journal/close (close-period state command)
      :time/punch (record-punch state command)
      :register/append (append-register state command)
      (fail :unknown-command))))

(defn trial-balance [state]
  (reduce (fn [balances {:keys [lines]}]
            (reduce (fn [m {:keys [account side amount-yen]}]
                      (update m account (fnil + 0)
                              (* amount-yen (if (= side :debit) 1 -1))))
                    balances lines))
          {} (:journal state)))

(defn worked-minutes [state worker-id]
  (let [punches (->> (:punches state)
                     (filter #(= worker-id (:worker-id %)))
                     (sort-by (juxt :instant-ms :id)))]
    (loop [remaining punches open nil total 0]
      (if-let [{:keys [direction instant-ms]} (first remaining)]
        (cond
          (and (= direction :in) open) {:ok false :reason :consecutive-clock-in}
          (and (= direction :out) (nil? open)) {:ok false :reason :unmatched-clock-out}
          (= direction :in) (recur (rest remaining) instant-ms total)
          (<= instant-ms open) {:ok false :reason :nonpositive-shift}
          :else (recur (rest remaining) nil (+ total (- instant-ms open))))
        (if open {:ok false :reason :unmatched-clock-in}
            {:ok true :duration-ms total :minutes (/ total 60000)})))))
