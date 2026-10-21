(ns kaisya.edge.http
  "Private HTTP boundary for kaisya.itonami.app. A registered passkey has no
  company authority until its credential row is promoted to owner. All record
  reads and writes require a verified, unexpired HttpOnly session belonging to
  an owner. The company starts empty; no real Gftd data is preloaded."
  (:require [kaisya.edge.store :as store]
            [kaisya.operations :as operations]
            [webauthn.adapters.edge :as passkey]))

(def rp-id "itonami.app")
(def origin "https://kaisya.itonami.app")
(def organization-id "gftd-japan")
(def session-seconds 3600)
(def challenge-seconds 300)

(defn- now-seconds [] (quot (js/Date.now) 1000))
(defn- now-iso [] (.toISOString (js/Date.)))
(defn- db [env] (aget env "KAISYA_DB"))
(defn- changed? [result] (pos? (or (aget (aget result "meta") "changes") 0)))

(defn- json [value status & [extra-headers]]
  (let [headers (js/Headers.)]
    (.set headers "content-type" "application/json; charset=utf-8")
    (.set headers "cache-control" "no-store")
    (.set headers "x-content-type-options" "nosniff")
    (.set headers "x-frame-options" "DENY")
    (doseq [[k v] extra-headers] (.set headers k v))
    (js/Response. (js/JSON.stringify (clj->js value))
                  #js {:status status :headers headers})))

(defn- random-token []
  (let [bytes (js/crypto.getRandomValues (js/Uint8Array. 32))]
    (-> (js/btoa (apply str (map js/String.fromCharCode (array-seq bytes))))
        (.replace (js/RegExp. "\\+" "g") "-")
        (.replace (js/RegExp. "/" "g") "_")
        (.replace (js/RegExp. "=+$" "") ""))))

(defn- token-hash! [token]
  (-> (js/crypto.subtle.digest "SHA-256" (.encode (js/TextEncoder.) token))
      (.then (fn [buf]
               (apply str (map (fn [byte] (.padStart (.toString byte 16) 2 "0"))
                               (array-seq (js/Uint8Array. buf))))))))

(defn- issue-challenge! [env purpose]
  (let [challenge (random-token)]
    (-> (.prepare (db env)
                  "INSERT INTO kaisya_challenges (challenge, purpose, expires_at) VALUES (?, ?, ?)")
        (.bind challenge purpose (+ (now-seconds) challenge-seconds))
        (.run)
        (.then (fn [_] (json {:ok true :challenge challenge :rpId rp-id} 200))))))

(defn- consume-challenge! [env challenge purpose]
  (-> (.prepare (db env)
                (str "UPDATE kaisya_challenges SET consumed_at = ? "
                     "WHERE challenge = ? AND purpose = ? AND consumed_at IS NULL "
                     "AND expires_at > ?"))
      (.bind (now-seconds) challenge purpose (now-seconds))
      (.run)
      (.then changed?)))

(defn- parse-body! [request]
  (let [size (js/Number (or (.get (.-headers request) "content-length") "0"))]
    (if (> size 65536)
      (js/Promise.reject (js/Error. "request too large"))
      (-> (.text request)
          (.then (fn [body]
                   (if (> (count body) 65536)
                     (js/Promise.reject (js/Error. "request too large"))
                     (js/JSON.parse body))))))))

(defn- save-credential! [env result]
  (-> (.prepare (db env)
                (str "INSERT OR IGNORE INTO kaisya_credentials "
                     "(credential_id, public_key_b64, sign_count, owner, created_at) "
                     "VALUES (?, ?, ?, 0, ?)"))
      (.bind (:credential-id result) (:public-key-b64 result)
             (:sign-count result) (now-iso))
      (.run)
      (.then (fn [saved]
               (if (changed? saved)
                 (json {:ok true :credentialId (:credential-id result)
                        :status "pending-owner"} 201)
                 (json {:ok false :error "credential exists"} 409))))))

(defn- register! [env request]
  (-> (parse-body! request)
      (.then
       (fn [body]
         (let [challenge (aget body "challenge")]
           (if-not (string? challenge)
             (json {:ok false :error "challenge required"} 400)
             (-> (consume-challenge! env challenge "register")
                 (.then (fn [consumed?]
                          (if consumed?
                            (passkey/verify-registration!
                             {:rp-id rp-id :origin origin}
                             {:challenge challenge
                              :client-data-json-b64url (aget body "clientDataJsonB64url")
                              :attestation-object-b64url (aget body "attestationObjectB64url")})
                            (json {:ok false :error "challenge expired or used"} 400))))
                 (.then (fn [result]
                          (cond
                            (instance? js/Response result) result
                            (:ok result) (save-credential! env result)
                            :else (json {:ok false :error (:error result)}
                                        (or (:status result) 400))))))))))))

(defn- cookie-token [request]
  (let [header (or (.get (.-headers request) "cookie") "")
        match (.match header (js/RegExp. "(?:^|;\\s*)kaisya_session=([^;]+)"))]
    (when match (aget match 1))))

(defn- owner-session! [env request]
  (if-let [token (cookie-token request)]
    (-> (token-hash! token)
        (.then (fn [hash]
                 (-> (.prepare (db env)
                               (str "SELECT c.credential_id FROM kaisya_sessions s "
                                    "JOIN kaisya_credentials c ON c.credential_id = s.credential_id "
                                    "WHERE s.token_hash = ? AND s.expires_at > ? AND c.owner = 1"))
                     (.bind hash (now-seconds))
                     (.first))))
        (.then (fn [row] (when row (aget row "credential_id")))))
    (js/Promise.resolve nil)))

(defn- logout! [env request]
  (if-let [token (cookie-token request)]
    (-> (token-hash! token)
        (.then (fn [hash]
                 (-> (.prepare (db env)
                               "DELETE FROM kaisya_sessions WHERE token_hash = ?")
                     (.bind hash)
                     (.run))))
        (.then (fn [_]
                 (json {:ok true} 200
                       {"set-cookie" "kaisya_session=; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=0"}))))
    (js/Promise.resolve
     (json {:ok true} 200
           {"set-cookie" "kaisya_session=; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=0"}))))

(defn- issue-session! [env credential-id]
  (let [token (random-token)]
    (-> (token-hash! token)
        (.then (fn [hash]
                 (-> (.prepare (db env)
                               (str "INSERT INTO kaisya_sessions "
                                    "(token_hash, credential_id, expires_at, created_at) "
                                    "VALUES (?, ?, ?, ?)"))
                     (.bind hash credential-id
                            (+ (now-seconds) session-seconds) (now-iso))
                     (.run))))
        (.then (fn [_]
                 (json {:ok true :owner true} 200
                       {"set-cookie" (str "kaisya_session=" token
                                          "; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age="
                                          session-seconds)}))))))

(defn- finish-login! [env credential-id credential verified]
  (cond
    (instance? js/Response verified) verified
    (not (:ok verified))
    (json {:ok false :error (:error verified)} (or (:status verified) 401))
    (not (passkey/sign-count-ok? (aget credential "sign_count")
                                 (:sign-count verified)))
    (json {:ok false :error "authenticator counter did not advance"} 401)
    :else
    (-> (.prepare (db env)
                  (str "UPDATE kaisya_credentials SET sign_count = ? "
                       "WHERE credential_id = ? AND sign_count = ?"))
        (.bind (:sign-count verified) credential-id (aget credential "sign_count"))
        (.run)
        (.then (fn [updated]
                 (cond
                   (not (changed? updated))
                   (json {:ok false :error "concurrent login; retry"} 409)
                   (zero? (aget credential "owner"))
                   (json {:ok false :error "owner approval pending"
                          :credentialId credential-id} 403)
                   :else (issue-session! env credential-id)))))))

(defn- login! [env request]
  (-> (parse-body! request)
      (.then
       (fn [body]
         (let [credential-id (aget body "credentialIdB64url")
               challenge (aget body "challenge")]
           (if-not (and (string? credential-id) (string? challenge))
             (json {:ok false :error "credential and challenge required"} 400)
             (-> (.prepare (db env)
                           "SELECT public_key_b64, sign_count, owner FROM kaisya_credentials WHERE credential_id = ?")
                 (.bind credential-id)
                 (.first)
                 (.then
                  (fn [credential]
                    (if-not credential
                      (json {:ok false :error "unknown credential"} 401)
                      (-> (consume-challenge! env challenge "login")
                          (.then (fn [consumed?]
                                   (if consumed?
                                     (passkey/verify-authentication!
                                      {:rp-id rp-id :origin origin}
                                      {:challenge challenge
                                       :client-data-json-b64url (aget body "clientDataJsonB64url")
                                       :authenticator-data-b64url (aget body "authenticatorDataB64url")
                                       :signature-b64url (aget body "signatureB64url")
                                       :public-key-b64 (aget credential "public_key_b64")})
                                     (json {:ok false :error "challenge expired or used"} 400))))
                          (.then (fn [verified]
                                   (finish-login! env credential-id credential verified))))))))))))))

(defn- state! [env request]
  (-> (owner-session! env request)
      (.then (fn [owner]
               (if-not owner
                 (json {:ok false :error "owner sign-in required"} 401)
                 (-> (store/read! env organization-id)
                     (.then (fn [{:keys [version state]}]
                              (json {:ok true :version version :state state
                                     :trialBalance (operations/trial-balance state)} 200)))))))))

(defn- normalize-command [body owner]
  (let [kind (get {"journal/post" :journal/post
                   "journal/reverse" :journal/reverse
                   "journal/close" :journal/close
                   "time/punch" :time/punch
                   "register/append" :register/append}
                  (aget body "kind"))
        lines (mapv (fn [line]
                      {:account (aget line "account")
                       :side (get {"debit" :debit "credit" :credit} (aget line "side"))
                       :amount-yen (aget line "amountYen")})
                    (array-seq (or (aget body "lines") #js [])))]
    {:id (js/crypto.randomUUID)
     :kind kind :organization-id organization-id :actor owner :at (now-iso)
     :date (aget body "date") :lines lines :source-id (aget body "sourceId")
     :reverses-id (aget body "reversesId") :through (aget body "through")
     :worker-id (aget body "workerId")
     :direction (get {"in" :in "out" :out} (aget body "direction"))
     :instant-ms (aget body "instantMs")
     :register-type (get {"employee" :employee "asset" :asset
                          "obligation" :obligation "document" :document
                          "wage" :wage} (aget body "registerType"))
     :subject-id (aget body "subjectId") :evidence-id (aget body "evidenceId")
     :value (js->clj (aget body "value") :keywordize-keys true)}))

(defn- command! [env request]
  (-> (owner-session! env request)
      (.then (fn [owner]
               (if-not owner
                 (json {:ok false :error "owner sign-in required"} 401)
                 (-> (parse-body! request)
                     (.then (fn [body]
                              (let [command (normalize-command body owner)]
                                (-> (store/read! env organization-id)
                                    (.then (fn [{:keys [version state]}]
                                             (let [result (operations/apply-command state command)]
                                               (if-not (:ok result)
                                                 (json {:ok false :error (name (:reason result))} 400)
                                                 (-> (store/commit! env organization-id version
                                                                    (:state result) (:at command))
                                                     (.then (fn [saved?]
                                                              (if saved?
                                                                (json {:ok true :version (inc version)
                                                                       :eventId (:id command)} 201)
                                                                (json {:ok false :error "concurrent update; retry"} 409)))))))))))))))))))

(defn ^:export fetch-request [request env _ctx]
  (let [url (js/URL. (.-url request))
        path (.-pathname url)
        method (.-method request)
        host-header (or (.get (.-headers request) "host") "")
        host-name (first (.split host-header ":"))
        app-host? (or (= "kaisya.itonami.app" (.toLowerCase (.-hostname url)))
                      (= "kaisya.itonami.app" (.toLowerCase host-name)))
        api? (.startsWith path "/api/")]
    (cond
      (not app-host?) (.fetch (aget env "ASSETS") request)
      (not api?) (if (and (= method "GET") (#{"/" "/index.html"} path))
                   (do
                     (set! (.-pathname url) "/operations.html")
                     (.fetch (aget env "ASSETS") (js/Request. (.toString url) request)))
                   (.fetch (aget env "ASSETS") request))
      (and (= method "POST")
             (not= origin (.get (.-headers request) "origin")))
      (js/Promise.resolve (json {:ok false :error "invalid origin"} 403))
      (not (store/available? env))
      (js/Promise.resolve (json {:ok false :error "storage unavailable"} 503))
      :else (-> (case [method path]
            ["GET" "/api/auth/challenge"]
            (let [purpose (.get (.-searchParams url) "purpose")]
              (if (#{"register" "login"} purpose)
                (issue-challenge! env purpose)
                (js/Promise.resolve (json {:ok false :error "invalid purpose"} 400))))
            ["POST" "/api/auth/register"] (register! env request)
            ["POST" "/api/auth/login"] (login! env request)
            ["POST" "/api/auth/logout"] (logout! env request)
            ["GET" "/api/state"] (state! env request)
            ["POST" "/api/command"] (command! env request)
            (js/Promise.resolve (json {:ok false :error "not found"} 404)))
          (.catch (fn [_] (json {:ok false :error "request failed"} 500)))))))

(def worker #js {:fetch fetch-request})
