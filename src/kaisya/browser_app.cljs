(ns kaisya.browser-app)

(defn- by-id [id] (.getElementById js/document id))
(defn- value [id] (.-value (by-id id)))
(defn- show! [id message] (set! (.-textContent (by-id id)) message))
(defn- bytes->b64url [bytes]
  (-> (js/btoa (apply str (map js/String.fromCharCode (array-seq (js/Uint8Array. bytes)))))
      (.replace (js/RegExp. "\\+" "g") "-")
      (.replace (js/RegExp. "/" "g") "_")
      (.replace (js/RegExp. "=+$" "") "")))
(defn- b64url->bytes [s]
  (let [base (-> s (.replace (js/RegExp. "-" "g") "+")
                    (.replace (js/RegExp. "_" "g") "/"))
        decoded (js/atob (str base (apply str (repeat (mod (- 4 (mod (count base) 4)) 4) "="))))]
    (js/Uint8Array. (clj->js (mapv #(.charCodeAt decoded %) (range (count decoded)))))))
(defn- request! [url method body]
  (-> (js/fetch url (clj->js (cond-> {:method method :credentials "same-origin"}
                              body (assoc :headers {"content-type" "application/json"}
                                          :body (js/JSON.stringify (clj->js body))))))
      (.then (fn [response]
               (-> (.json response)
                   (.then (fn [data]
                            (if (.-ok response)
                              (js->clj data :keywordize-keys true)
                              (js/Promise.reject (js/Error. (or (aget data "error") "操作に失敗しました")))))))))))
(defn- message [error]
  (let [raw (or (.-message error) "")]
    (get {"owner sign-in required" "オーナーのパスキーでログインしてください"
          "owner approval pending" "登録したパスキーはオーナー承認待ちです"
          "invalid origin" "この画面から操作をやり直してください"
          "unbalanced-or-invalid-lines" "貸借科目と金額を確認してください"
          "duplicate-source" "この証憑・取引番号はすでに記帳されています"
          "period-closed" "この日付の会計期間は締め済みです"
          "invalid-date" "日付を確認してください"
          "invalid-register-record" "対象IDと根拠IDを入力してください"}
         raw
         (if (empty? raw) "操作に失敗しました" raw))))
(defn- append-items! [id items render-item]
  (let [list (by-id id)]
    (.replaceChildren list)
    (if (seq items)
      (doseq [item items]
        (let [li (.createElement js/document "li")]
          (set! (.-textContent li) (render-item item))
          (.appendChild list li)))
      (let [li (.createElement js/document "li")]
        (set! (.-textContent li) "記録はありません")
        (.appendChild list li)))))
(defn- render-state! [{:keys [state trialBalance]}]
  (set! (.-hidden (by-id "owner-workspace")) false)
  (show! "auth-status" "オーナーとしてログイン中")
  (show! "record-summary"
         (str "仕訳 " (count (:journal state)) "件 · 打刻 " (count (:punches state))
              "件 · 台帳 " (count (:register state)) "件"))
  (append-items! "balance-list" (sort-by first trialBalance)
                 (fn [[account balance]] (str account " · " balance "円")))
  (append-items! "journal-list" (:journal state)
                 (fn [row]
                   (str (:date row) " · " (:source-id row) " · "
                        (apply str (interpose " / " (map (fn [line]
                            (str (:account line) " " (name (:side line)) " " (:amount-yen line) "円"))
                          (:lines row)))))))
  (append-items! "time-list" (:punches state)
                 (fn [row] (str (:worker-id row) " · " (name (:direction row))
                                " · " (.toLocaleString (js/Date. (:instant-ms row)) "ja-JP"))))
  (append-items! "register-list" (:register state)
                 (fn [row] (str (name (:register-type row)) " · " (:subject-id row)
                                " · 根拠 " (:evidence-id row) " · " (:value row)))))
(defn- refresh! []
  (-> (request! "/api/state" "GET" nil)
      (.then render-state!)
      (.catch (fn [error]
                (set! (.-hidden (by-id "owner-workspace")) true)
                (show! "auth-status" (message error))))))
(defn- challenge! [purpose]
  (request! (str "/api/auth/challenge?purpose=" purpose) "GET" nil))
(defn- register-passkey! []
  (show! "auth-status" "パスキーを登録しています…")
  (-> (challenge! "register")
      (.then (fn [{:keys [challenge rpId]}]
               (let [user-id (js/crypto.getRandomValues (js/Uint8Array. 32))]
                 (-> (.create (.-credentials js/navigator)
                              #js {:publicKey
                                   #js {:challenge (b64url->bytes challenge)
                                        :rp #js {:id rpId :name "kaisya"}
                                        :user #js {:id user-id :name "owner" :displayName "Company owner"}
                                        :pubKeyCredParams #js [#js {:type "public-key" :alg -7}]
                                        :authenticatorSelection #js {:residentKey "required"
                                                                      :userVerification "required"}
                                        :timeout 300000 :attestation "none"}})
                     (.then (fn [credential]
                              (request! "/api/auth/register" "POST"
                                        {:challenge challenge
                                         :clientDataJsonB64url (bytes->b64url
                                                               (aget (aget credential "response") "clientDataJSON"))
                                         :attestationObjectB64url (bytes->b64url
                                                                   (aget (aget credential "response") "attestationObject"))})))))))
      (.then (fn [{:keys [credentialId]}]
               (show! "auth-status" "パスキーを登録しました。オーナー承認待ちです。")
               (show! "credential-id" (str "登録 ID: " credentialId))))
      (.catch (fn [error] (show! "auth-status" (message error))))))
(defn- login-passkey! []
  (show! "auth-status" "パスキーで確認しています…")
  (-> (challenge! "login")
      (.then (fn [{:keys [challenge rpId]}]
               (-> (.get (.-credentials js/navigator)
                         #js {:publicKey #js {:challenge (b64url->bytes challenge)
                                              :rpId rpId :userVerification "required"
                                              :timeout 300000}})
                   (.then (fn [credential]
                            (request! "/api/auth/login" "POST"
                                      {:challenge challenge
                                       :credentialIdB64url (bytes->b64url (aget credential "rawId"))
                                       :clientDataJsonB64url (bytes->b64url
                                                             (aget (aget credential "response") "clientDataJSON"))
                                       :authenticatorDataB64url (bytes->b64url
                                                                 (aget (aget credential "response") "authenticatorData"))
                                       :signatureB64url (bytes->b64url
                                                         (aget (aget credential "response") "signature"))}))))))
      (.then (fn [_] (refresh!)))
      (.catch (fn [error] (show! "auth-status" (message error))))))
(defn- submit! [result-id command]
  (show! result-id "保存しています…")
  (-> (request! "/api/command" "POST" command)
      (.then (fn [_] (show! result-id "保存しました") (refresh!)))
      (.catch (fn [error] (show! result-id (message error))))))
(defn- bind-form! [id handler]
  (.addEventListener (by-id id) "submit"
                     (fn [event] (.preventDefault event) (handler))))
(defn ^:export start []
  (.addEventListener (by-id "register-passkey") "click" register-passkey!)
  (.addEventListener (by-id "login-passkey") "click" login-passkey!)
  (.addEventListener (by-id "refresh-records") "click" refresh!)
  (.addEventListener (by-id "logout") "click"
                     (fn []
                       (-> (request! "/api/auth/logout" "POST" {})
                           (.then (fn [_]
                                    (set! (.-hidden (by-id "owner-workspace")) true)
                                    (show! "auth-status" "ログアウトしました")))
                           (.catch (fn [error]
                                     (show! "auth-status" (message error)))))))
  (bind-form! "journal-form"
              #(let [amount (js/Number (value "journal-amount"))]
                 (submit! "journal-result"
                          {:kind "journal/post" :date (value "journal-date")
                           :sourceId (value "journal-source")
                           :lines [{:account (value "journal-debit") :side "debit" :amountYen amount}
                                   {:account (value "journal-credit") :side "credit" :amountYen amount}]})))
  (bind-form! "time-form"
              #(submit! "time-result"
                        {:kind "time/punch" :workerId (value "time-worker")
                         :direction (value "time-direction") :instantMs (js/Date.now)}))
  (bind-form! "register-form"
              #(submit! "register-result"
                        {:kind "register/append" :registerType (value "register-type")
                         :subjectId (value "register-subject")
                         :evidenceId (value "register-evidence")
                         :value (value "register-value")}))
  (refresh!))

(start)
