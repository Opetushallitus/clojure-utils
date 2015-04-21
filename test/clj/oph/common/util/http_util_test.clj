;; Copyright (c) 2015 The Finnish National Board of Education - Opetushallitus
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

(ns oph.infra.http-util-test
  (:require [clojure.test :refer [deftest testing is are]]
            [oph.common.util.http-util :refer :all]))

(deftest file-download-response-test
  (testing "file-download-response"
    (testing "Palauttaa datan unicode-merkkijonona, jos koodausta ei ole määritelty"
      (is (= "åäö" (-> (file-download-response "åäö" "foo.txt" "text/plain")
                     :body))))

    (testing "Palauttaa datan määritellyssä koodauksessa"
      (is (= "åäö" (-> (file-download-response "åäö" "foo.txt" "text/plain"
                                                   {:charset "CP1252"})
                     :body
                     (slurp :encoding "CP1252")))))))
