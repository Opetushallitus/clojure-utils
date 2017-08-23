(ns oph.common.infra.common-audit-log-test
  (:require [clojure.test :refer [deftest testing is are]]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.local :as time-local]
            [oph.common.infra.common-audit-log :refer :all]))

(def ^:private boot-time (time/local-date-time 1980 9 20 1 2 3 123))   ; (time-local/local-now)
(def test-request-meta {:user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36"
                        :session    "955d43a3-c02d-4ab8-a61f-141f29c44a84"
                        :ip         "192.168.50.1"})
(defn test-environment-meta [app-name]
  {:pre [(string? app-name)]}
  {:boot-time        boot-time
   :hostname         "host"
   :service-name     app-name
   :application-type "virkailija"})

(deftest auditlogitus-test

  (testing "environment metaa ei ole annettu"
;    (is (thrown? AssertionError (->audit-log-entry {})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema" (->audit-log-entry {})))
    )

  (testing "environment meta on epävalidi"
    (konfiguroi-common-audit-lokitus {:service-name "aitu"})
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema" (->audit-log-entry {})))
    )

  (testing "meta annettu mutta logisisältö puuttuu"
    (konfiguroi-common-audit-lokitus test-environment-meta)
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Value does not match schema" (->audit-log-entry {})))
    )

  (testing "kaikki kentät annettu"
    (binding [*request-meta* test-request-meta]
      (konfiguroi-common-audit-lokitus test-environment-meta)
      (let [resp (->audit-log-entry {:operation   :paivitys
                                     :user {:oid  "henkiloOid"}
                                     :resource    "järjestämissopimus"
                                     :resourceOid "sopimusOid"
                                     :id          "paa-avain"
                                     :delta       [{:op "päivitys"
                                                    :path "alkupvm"
                                                    :value (time/local-date 2009 8 1)}
                                                   {:op "päivitys"
                                                    :path "loppupvm"
                                                    :value (time/local-date 2009 7 31)}]
                                     :message     "Tämä on viesti."})
            ]
        ;; Ohitetaan muuttuvan "timestamp"-arvon tarkastelu ja logseq arvon juokseva numerointi
        (is (and
              (.contains resp
                "\"operation\":\"päivitys\",\"type\":\"log\",\"hostname\":\"host\",\"applicationType\":\"virkailija\"")
              (.contains resp
                "\"delta\":[{\"op\":\"päivitys\",\"path\":\"alkupvm\",\"value\":\"01.08.2009\"},{\"op\":\"päivitys\",\"path\":\"loppupvm\",\"value\":\"31.07.2009\"}]")
              (.contains resp
                "\"target\":{\"järjestämissopimus\":\"sopimusOid\",\"id\":\"paa-avain\"},\"serviceName\":\"aitu\",\"version\":1")
              (.contains resp
                "\"user\":{\"oid\":\"henkiloOid\",\"ip\":\"192.168.50.1\",\"session\":\"955d43a3-c02d-4ab8-a61f-141f29c44a84\",\"userAgent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36\"}")
              (.contains resp
                "\"message\":\"Tämä on viesti.\"")
              (.contains resp (json/generate-string boot-time))))
        )))
  )
