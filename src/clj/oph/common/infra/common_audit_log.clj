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
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [cheshire.core :as cheshire]
    [cheshire.generate :as json-gen]
    [clj-time.core :as time]
    [clj-time.local :as time-local]
    ))

(s/defschema Env-meta {:boot-time        s/Any   ;; TODO: Tarkasta jotenkin datetime
                       :hostname         s/Str
                       :service-name     s/Str
                       :application-type (s/enum "oppija" "virkailija")})

(s/defschema Logientry {:operation (s/enum :kirjautuminen :lisays :paivitys :poisto)
                        :user {:oid        s/Str
                               :ip         s/Str
                               :session    s/Str
                               :user-agent s/Str}
                        :resource s/Str              ;; taulun nimi
                        :resourceOid (s/maybe s/Str) ;; mahdollinen objektin id
                        :id s/Str                    ;; taulun pääavain
                        (s/optional-key :delta) [{:op (s/enum :lisays :paivitys :poisto)
                                                  :path s/Str
                                                  :value s/Any}]
                        (s/optional-key :message) s/Str})

(def ^:dynamic *req-meta*)

(def ^:private version  1)
(def ^:private type-log "log")  ;; Meillä ei tueta "alive"-logiviestejä
(def ^:private log-seq  (atom (bigint 0)))
(def ^:private environment-meta (atom {:boot-time        nil
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
(json-gen/add-encoder org.joda.time.LocalDateTime
                      (fn [c json-generator]
                        (.writeString json-generator (.toString c))))
(json-gen/add-encoder org.joda.time.LocalDate
                      (fn [c json-generator]
                        (.writeString json-generator (.toString c "dd.MM.yyyy"))))

(defn req-metadata-saver-wrapper
  "Tallentaa requestista tietoa logitusta varten"
  [ring-handler]
  (fn [request]
    ;;
    ;; TODO
    ;;
    (println "\n request:")
    (>pprint request)
    (println "\n")

    (let [user-oid     (get (:headers request) "oid")  ;; TODO: Onko tämä userin oid?
          user-agent   (get (:headers request) "user-agent")
          user-session ""   ;; TODO: Mitä tähän?
          ip           ""   ;; TODO: Mitä tähän?
          ]
      (ring-handler req)
      )

    ))

(defn konfiguroi-common-audit-lokitus [metadata]
  (log/info "Alustetaan common audit logituksen metadata arvoihin:" metadata)
  (reset! environment-meta metadata))

(defn ->audit-log-entry [log-contents]

  ;; pidä nämä alussa
  (s/validate Env-meta  @environment-meta)
  (s/validate Logientry log-contents)

  (swap! log-seq inc)
  (cheshire/generate-string (merge
                              {:version         version
                               :logSeq          @log-seq
                               :type            type-log
                               :bootTime        (:boot-time @environment-meta)
                               :hostname        (:hostname @environment-meta)
                               :timestamp       (time-local/local-now)
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
                              (when (:resource log-contents) #_(and (:resource log-contents) (:resourceOid log-contents) (:id log-contents))
                                {:target {(:resource log-contents) (:resourceOid log-contents)
                                          :id (:id log-contents)}})
                              (when (:message log-contents)
                                {:message (:message log-contents)})
                              )))

