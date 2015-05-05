(ns oph.korma.common
  (:import java.sql.Date
           com.jolbox.bonecp.BoneCPDataSource
           org.joda.time.LocalDate)
  (:require  korma.db
             [korma.core :as sql]
             [korma.sql.engine :as eng]
             [clj-time.coerce :as time-coerce]
             [clj-time.core :as time]))

(defn korma-asetukset
  "Muuttaa asetustiedoston db-avaimen arvon Korman odottamaan muotoon."
  [db-asetukset]
  (clojure.set/rename-keys db-asetukset {:name :db}))

(defn bonecp-datasource
  "BoneCP based connection pool"
  [db-asetukset]
  (let [korma-postgres (korma.db/postgres (korma-asetukset db-asetukset))
        bonecp-ds (doto (com.jolbox.bonecp.BoneCPDataSource.)
                    (.setJdbcUrl (str "jdbc:" (:subprotocol korma-postgres) ":" (:subname korma-postgres)))
                    (.setUsername (:user korma-postgres))
                    (.setPassword (:password korma-postgres))
                    (.setConnectionTestStatement "select 42")
                    (.setConnectionTimeoutInMs 2000)
                    (.setDefaultAutoCommit false)
                    (.setMaxConnectionsPerPartition 10)
                    (.setMinConnectionsPerPartition 5)
                    (.setPartitionCount 1))]
    bonecp-ds))

(defn luo-db [db-asetukset]
  (korma.db/default-connection
    (korma.db/create-db {:make-pool? false
                         :delimiters ""
                         :datasource (bonecp-datasource db-asetukset)})))

(defn convert-instances-of [c f m]
  (clojure.walk/postwalk #(if (instance? c %) (f %) %) m))

(defn joda-datetime->sql-timestamp [m]
  (convert-instances-of org.joda.time.DateTime
                        time-coerce/to-sql-time
                        m))

(defn sql-timestamp->joda-datetime [m]
  (convert-instances-of java.sql.Timestamp
                        time-coerce/from-sql-time
                        m))

(defn ^:private to-local-date-default-tz
  [date]
  (let [dt (time-coerce/to-date-time date)]
    (time-coerce/to-local-date (time/to-time-zone dt (time/default-time-zone)))))

(defn sql-date->joda-date [m]
  (convert-instances-of java.sql.Date
                        to-local-date-default-tz
                        m))

(defn joda-date->sql-date [m]
  (convert-instances-of org.joda.time.LocalDate
                        time-coerce/to-sql-date
                        m))
(defmacro defentity
  "Wrapperi Korman defentitylle, lisää yleiset prepare/transform-funktiot."
  [ent & body]
  `(sql/defentity ~ent
     (sql/prepare joda-date->sql-date)
     (sql/prepare joda-datetime->sql-timestamp)
     (sql/transform sql-date->joda-date)
     (sql/transform sql-timestamp->joda-datetime)
     ~@body))

(defmacro select-unique-or-nil
  "Wraps korma.core/select, returns unique result or nil if none found. Throws if result is not unique."
  [entity & body]
  `(let [[first# & rest#] (sql/select ~entity ~@body)]
     (assert (empty? rest#) "Expected one result, got more")
     first#))

(defmacro select-unique
  "Wraps korma.core/select, returns unique result. Throws if result is not unique."
  [entity & body]
  `(let [result# (select-unique-or-nil ~entity ~@body)]
     (assert result# "Expected one result, got zero")
     result#))

(defmacro update-unique
  "Wraps korma.core/update, updates exactly one row. Throws if row count is not 1. Returns the number of updated rows (1)."
  [entity & body]
  `(let [count# (-> (sql/update* ~entity)
                  ~@body
                  (dissoc :results)
                  sql/exec)
         count# (if (sequential? count#) ;; JDBC:n vanha versio palauttaa vektorin, uusi pelkän luvun
                  (first count#)
                  count#)]
     (assert (= count# 1) (str "Expected one updated row, got " count#))
     count#))

(defn entity-alias [entity alias]
  (assoc entity :name alias
                :alias alias))

;; Korma ei salli useampaa kuin yhtä linkkiä samojen entityjen välillä.
;; Tämä makro tekee kopion entitystä uudelle nimelle.
(defmacro defalias [alias entity]
  `(def ~alias (entity-alias ~entity ~(name alias))))

(defn ilike
  "Korma-funktio Postgresql:n ilike-vertailulle"
  [k v]
  (eng/infix k "ILIKE" v))
