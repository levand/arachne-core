(ns arachne.core.config.impl.common
  (:require [clojure.walk :as w]
            [arachne.core.config :as cfg]))


;; Yes. I know this uses mutation. No time to write a `reduce` version of
;; clojure.walk
(defn replace-tempids
  "Replace generic arachne.core.config.Tempid instances with
  implementation-specific tempids (generated by the provided function). Return a
  tuple of the resultant txdata, and a mapping of arachne IDs to the
  corresponding implementation-specific tempids."
  [partition data tempid-fn]
  (let [mapping (atom {})
        new-txdata (w/prewalk
                     (fn [val]
                       (if (instance? arachne.core.config.Tempid val)
                         (let [impl-id (if (.-id val)
                                         (tempid-fn partition (.-id val))
                                         (tempid-fn partition))]
                           (swap! mapping assoc val impl-id)
                           impl-id)
                         val))
                     data)]
    [new-txdata @mapping]))

(defn with
  "Add the given the given txdata, replacing Arachne tempids with
  implemenation-specific tempids. Adds an :arachne-tempids key to the map
  returned by `datomic.api/with`, containing a mapping of Arachne tempids to
  realized entity IDs."
  ([db txdata impl-with impl-tempid impl-resolve-tempid]
    (with db txdata impl-with impl-tempid impl-resolve-tempid :db.part/user))
  ([db txdata impl-with impl-tempid impl-resolve-tempid partition]
   (let [[txdata atid->dtid] (replace-tempids partition txdata impl-tempid)
         txresult (impl-with db txdata)]
     (assoc txresult
       :arachne-tempids
       (into {} (map (fn [[atid dtid]]
                       [atid (impl-resolve-tempid (:db-after txresult)
                                                  (:tempids txresult)
                                                  dtid)])
                     atid->dtid))))))
