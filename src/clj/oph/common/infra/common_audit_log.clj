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
    )
  (:import [java.net.URL])  ;; TODO: Onko käytössä?
  )

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

#_{:cookies {"_pk_id.8.c997" {:value "f3d739b61c765b2d.1494944473.1.1494944473.1494944473."},
           "XSRF-TOKEN"    {:value "60406fd7-0f54-1d40-5f62-22dbc2a5de7d"},
           "ring-session"  {:value "1903bd73-958a-4b88-8a52-e4f396d893c7"}},
 :remote-addr "192.168.50.1",
 :params {},
 :headers {"host" "192.168.50.1:8080",
           "user-agent" "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36",
           "cookie" "_pk_id.8.c997=f3d739b61c765b2d.1494944473.1.1494944473.1494944473.; XSRF-TOKEN=60406fd7-0f54-1d40-5f62-22dbc2a5de7d; ring-session=1903bd73-958a-4b88-8a52-e4f396d893c7",
           "uid" "T-1001",
           "referer" "http://192.168.50.1:8080/fi/",
           "connection" "keep-alive",
           "accept" "image/webp,image/apng,image/*,*/*;q=0.8",
           "accept-language" "en-US,en;q=0.8,fi;q=0.6",
           "accept-encoding" "gzip, deflate"},
 :async-channel #<AsyncChannel /192.168.50.1:8080<->/192.168.50.1:53058>,
 :server-port 8080,
 :content-length 0,
 :websocket? false,
 :session/key nil,
 :content-type nil,
 :character-encoding "utf8",
 :uri "/favicon.ico",
 :server-name "192.168.50.1",
 :query-string nil,
 :body nil,
 :multipart-params {},
 :scheme :http,
 :request-method :get,
 :session {}}

(defn req-metadata-saver-wrapper
  "Tallentaa requestista tietoa logitusta varten"
  [ring-handler]
  (fn [request]
    (binding [*req-meta* {
;                          :hostname   (:server-name request) ;; TODO: Saataisiinko :hostname tällä, sen sijaan että se kaivetaan palvelin.clj:ssä?
                          :user-oid     (get (:headers request) "uid" #_"oid")  ;; TODO: Onko tämä userin oid?
                          :user-agent   (get (:headers request) "user-agent")
                          :user-session (get (:cookies request) "ring-session")   ;; TODO: Onko ok?
                          ;; Speksi sanoo: "Edustapalvelimen asettaman X-Forwarded-For-otsakkeen perusteella"
                          ;; TODO: Mitä tähän?  :remote-addr, :server-name, (:headers->host), (:headers->referer)?
                          :user-ip      (let [referer (get (:headers request) "referer")]
                                          (-> asetukset referer java.net.URL. .getHost))
                          }
              ]
      (ring-handler request)
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
                               :user            {:oid       (:user-oid *req-meta*) #_(-> log-contents :user :oid)
                                                 :ip        (:user-ip *req-meta*) #_(-> log-contents :user :ip)
                                                 :session   (:user-session *req-meta*) #_(-> log-contents :user :session)
                                                 :userAgent (:user-agent *req-meta*) #_(-> log-contents :user :user-agent)}
                               }
                              (when (:delta log-contents)
                                {:delta (:delta log-contents)})
                              (when (:resource log-contents) #_(and (:resource log-contents) (:resourceOid log-contents) (:id log-contents))
                                {:target {(:resource log-contents) (:resourceOid log-contents)
                                          :id (:id log-contents)}})
                              (when (:message log-contents)
                                {:message (:message log-contents)})
                              )))

