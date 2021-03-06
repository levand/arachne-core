(ns arachne.core.config.impl.datomic
  (:require [clojure.walk :as w]
            [datomic.api :as d]
            [arachne.core.config.impl.common :as common]
            [arachne.core.config :as cfg]
            [arachne.core.util :as util]
            [arachne.error :as e :refer [error]])
  (:import [java.util UUID]))

(defn- init
  []
  (let [uri (str "datomic:mem://arachne-cfg-" (str (UUID/randomUUID)))
        _ (d/create-database uri)
        conn (d/connect uri)]
    (d/db conn)))

(defrecord DatomicConfig [db tempids]
  cfg/Configuration
  (init- [this _]
    (assoc this :db (init)))
  (update- [this txdata]
    (let [result (common/with db txdata d/with d/tempid d/resolve-tempid)]
      (assoc this :db (:db-after result)
                  :tempids (:arachne-tempids result))))
  (query- [this query other-sources]
    (apply d/q query (:db this) other-sources))
  (pull- [this expr lookup-or-eid]
    (when-not (d/entity (:db this) lookup-or-eid)
      (error ::cfg/nonexistent-pull-entity {:lookup lookup-or-eid
                                            :config this}))
    (d/pull (:db this) expr lookup-or-eid))
  (resolve-tempid- [this tempid]
    (get tempids tempid))
  Object
  (toString [this]
    (str "#DatomicConfig[" (hash-combine (hash db) (hash tempids)) "]")))

(defmethod print-method DatomicConfig
  [cfg writer]
  (.write writer (str cfg)))

(defn ctor
  "Construct and return an uninitialized instance of DatomicConfig"
  []
  (->DatomicConfig nil nil))
