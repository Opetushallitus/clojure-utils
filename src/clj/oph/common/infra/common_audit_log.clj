;; Copyright (c) 2013 The Finnish National Board of Education - Opetushallitus
;;
;; This program is free software:  Licensed under the EUPL, Version 1.1 or - as
;; soon as they will be approved by the European Commission - subsequent versions
;; of the EUPL (the "Licence");
;;
;; You may not use this work except in compliance with the Licence.
;; You may obtain a copy of the Licence at: http://www.osor.eu/eupl/
;;
;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; European Union Public Licence for more details.

(ns oph.common.infra.common-audit-log
  "Yhteinen audit-lokituksen abstrahoiva rajapinta."
  (:require
    [schema.core :as s]
    [cheshire.core :as cheshire]
    [cheshire.generate :as json-gen]
    [clj-time.core :as time]
    [clj-time.local :as time-local]
    ))


;; Esimerkkejä:
;
;{"logSeq":"21785","bootTime":"2017-06-07T16:06:47.613+03","hostname":"konsepti","timestamp":"2017-06-27T09:06:49.002+03","serviceName":"tarjonta","applicationType":"virkailija","message":"Alive!"}
;{"logSeq":"21786","bootTime":"2017-06-07T16:06:47.613+03","hostname":"konsepti","timestamp":"2017-06-27T09:06:56.913+03","serviceName":"tarjonta","applicationType":"virkailija","resource":"HAKUKOHDE","resourceOid":"1.2.246.562.20.20344157233","id":"1.2.246.562.24.69880686397","operaatio":"PUBLISH"}
;{"logSeq":"21787","bootTime":"2017-06-07T16:06:47.613+03","hostname":"konsepti","timestamp":"2017-06-27T09:10:06.001+03","serviceName":"tarjonta","applicationType":"virkailija","resource":"KOULUTUS","delta":"[{\"op\":\"replace\",\"path\":\"/modified\",\"value\":1498543804238},{\"op\":\"replace\",\"path\":\"/version\",\"value\":1},{\"op\":\"replace\",\"path\":\"/tila\",\"value\":\"VALMIS\"}]","resourceOid":"1.2.246.562.17.22855189217","id":"1.2.246.562.24.89924410056","operaatio":"UPDATE"}

;; Ylläolevat avattuna:
;
;{"logSeq":"21785",
; "bootTime":"2017-06-07T16:06:47.613+03",
; "hostname":"konsepti",
; "timestamp":"2017-06-27T09:06:49.002+03",
; "serviceName":"tarjonta",
; "applicationType":"virkailija",
; "message":"Alive!"}
;
;{"logSeq":"21786",
; "bootTime":"2017-06-07T16:06:47.613+03",
; "hostname":"konsepti",
; "timestamp":"2017-06-27T09:06:56.913+03",
; "serviceName":"tarjonta",
; "applicationType":"virkailija",
; "resource":"HAKUKOHDE",
; "resourceOid":"1.2.246.562.20.20344157233",
; "id":"1.2.246.562.24.69880686397",
; "operaatio":"PUBLISH"}
;
;{"logSeq":"21787",
; "bootTime":"2017-06-07T16:06:47.613+03",
; "hostname":"konsepti",
; "timestamp":"2017-06-27T09:10:06.001+03",
; "serviceName":"tarjonta",
; "applicationType":"virkailija",
; "resource":"KOULUTUS",
; "delta":"[{\"op\":\"replace\",
;            \"path\":\"/modified\",
;            \"value\":1498543804238},
;           {\"op\":\"replace\",
;            \"path\":\"/version\",
;            \"value\":1},
;           {\"op\":\"replace\",
;            \"path\":\"/tila\",
;            \"value\":\"VALMIS\"}]",
; "resourceOid":"1.2.246.562.17.22855189217",
; "id": "1.2.246.562.24.89924410056",
; "operaatio":"UPDATE"}

(s/defschema Env-meta {:boot-time        s/Any   ;; TODO: Tarkasta datetime
                       :hostname         s/Str
                       :service-name     s/Str
                       :application-type (s/enum "oppija" "virkailija")})

(s/defschema Logientry {:operation (s/enum :kirjautuminen :lisays :paivitys :poisto)   ;; Huom: esimerkeissä avaimena "operaatio"
                        :user {:oid        s/Str
                               :ip         s/Str
                               :session    s/Str
                               :user-agent s/Str}
                        :resource s/Str
                        :resourceOid (s/maybe s/Str)
                        :id s/Str  ;; pääavain
                        (s/optional-key :delta) [{:op (s/enum :lisays :paivitys :poisto)
                                                  :path s/Str
                                                  :value s/Any}]
                        (s/optional-key :message) s/Str})

(def ^:private version  1)
(def ^:private type-log "log")  ;; Meillä ei tueta "alive"-logiviestejä
(def ^:private log-seq  (atom (bigint 0)))
(def environment-meta   (atom {:boot-time        nil
                               :hostname         nil
                               :service-name     nil
                               :application-type nil}))

(def operaatiot {:kirjautuminen "kirjautuminen"
                 :lisays "lisäys"
                 :paivitys "päivitys"
                 :poisto "poisto"})

(json-gen/add-encoder org.joda.time.DateTime
                      (fn [c json-generator]
                        (.writeString json-generator (.toString c))))
(json-gen/add-encoder org.joda.time.LocalDateTime                                    ;; TODO: Tarvitaanko tätä?
                      (fn [c json-generator]
                        (.writeString json-generator (.toString c))))
(json-gen/add-encoder org.joda.time.LocalDate
                      (fn [c json-generator]
                        (.writeString json-generator (.toString c "dd.MM.yyyy"))))    ;; TODO: Onko muoto "dd.MM.yyyy" ok, vai printataanko samoin kuin kuut yllä?

(defn log [log-contents]
  {:pre [#_(every? (comp not nil?) [boot-time
                                   hostname
                                   serviceName
                                   applicationType])
;         (every?
;           (fn [[k v]] (not (nil? v)))
;           @environment-meta)
;         (nil? (s/check Logientry log-contents))
         ]}
  ;; pidä nämä alussa
  (s/validate Env-meta  @environment-meta)
  (s/validate Logientry log-contents)

  (reset! log-seq (inc @log-seq))
  (let [json-contents (cheshire/generate-string (merge
                                                  {:version         version
                                                   :logSeq          @log-seq
                                                   :type            type-log
                                                   :bootTime        (:boot-time @environment-meta)
                                                   :hostname        (:hostname @environment-meta)
                                                   :timestamp       (time-local/local-now)              ;; TODO: Onko tämä ok?
                                                   :serviceName     (:service-name @environment-meta)
                                                   :applicationType (:application-type @environment-meta)
                                                   :operation       (get operaatiot (:operation log-contents))
                                                   :user            {:oid       (-> log-contents :user :oid)
                                                                     :ip        (-> log-contents :user :ip)
                                                                     :session   (-> log-contents :user :session)
                                                                     :userAgent (-> log-contents :user :user-agent)}
                                                   }
                                                  (when (:delta log-contents)
                                                    {:delta (:delta log-contents)})
                                                  ;; TODO: Tarkastetaanko tässä :id:n löytyminen?
                                                  (when (:resource log-contents) #_(and (:resource log-contents) (:resourceOid log-contents) (:id log-contents))
                                                    {:target {(:resource log-contents) (:resourceOid log-contents)
                                                              :id (:id log-contents)}})
                                                  (when (:message log-contents)
                                                    {:message (:message log-contents)})
                                                  ))]
    json-contents
    ))



