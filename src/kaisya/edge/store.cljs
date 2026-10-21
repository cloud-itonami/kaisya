(ns kaisya.edge.store
  "D1 persistence for the private company record. The whole state, including
  its audit list, is replaced by a single version CAS. No KV cache of private
  finance or personnel data is kept in a shared namespace."
  (:require [cljs.reader :as reader]
            [kaisya.operations :as operations]))

(defn available? [env]
  (boolean (and env (aget env "KAISYA_DB"))))

(defn read! [env organization-id]
  (if-not (available? env)
    (js/Promise.reject (js/Error. "KAISYA_DB is not configured"))
    (-> (.prepare (aget env "KAISYA_DB")
                  "SELECT version, record_edn FROM kaisya_company_records WHERE organization_id = ?")
        (.bind organization-id)
        (.first)
        (.then (fn [row]
                 (if row
                   {:version (aget row "version")
                    :state (reader/read-string (aget row "record_edn"))}
                   {:version 0 :state (operations/empty-company organization-id)}))))))

(defn commit! [env organization-id seen-version state at]
  (let [db (aget env "KAISYA_DB")
        payload (pr-str state)]
    (if (zero? seen-version)
      (-> (.prepare db
                    (str "INSERT OR IGNORE INTO kaisya_company_records "
                         "(organization_id, version, record_edn, updated_at) VALUES (?, 1, ?, ?)"))
          (.bind organization-id payload at)
          (.run)
          (.then (fn [result]
                   (pos? (or (aget (aget result "meta") "changes") 0)))))
      (-> (.prepare db
                    (str "UPDATE kaisya_company_records "
                         "SET version = ?, record_edn = ?, updated_at = ? "
                         "WHERE organization_id = ? AND version = ?"))
          (.bind (inc seen-version) payload at organization-id seen-version)
          (.run)
          (.then (fn [result]
                   (pos? (or (aget (aget result "meta") "changes") 0))))))))
