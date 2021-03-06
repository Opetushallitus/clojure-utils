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

(ns oph.common.util.util-test
  (:require [clojure.test :refer :all]
            [oph.common.util.util :refer :all]
            [valip.predicates :refer [present?]]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]))

(deftest pisteavaimet->puu-test
  (is (= (pisteavaimet->puu {:foo.bar.baz 123
                             :foo.bar.blah 456})
         {:foo {:bar {:baz 123
                      :blah 456}}})))

(deftest get-in-list-test
  (let [test-map {:foo 0
                  :bar [{:baz 1}
                        {:baz 2}
                        {:baz 3}]}]
    (is (= 0 (get-in-list test-map [:foo])))
    (is (= [1 2 3] (get-in-list test-map [:bar :baz])))
    (is (= :not-found (get-in-list test-map [:bloo] :not-found)))))

(deftest uusin-muokkausaika-test
  (let [test-map {:muokattu (time/date-time 2013 1 1)
                 :foo {:muokattu (time/date-time 2013 2 1)}
                 :bar [{:muokattu (time/date-time 2013 3 1)}
                       {:muokattu (time/date-time 2013 4 1)}]}]
    (is (= (time/date-time 2013 1 1) (uusin-muokkausaika [test-map] [:muokattu])))
    (is (= (time/date-time 2013 2 1) (uusin-muokkausaika [test-map] [:muokattu] [:foo :muokattu])))
    (is (= (time/date-time 2013 4 1) (uusin-muokkausaika [test-map] [:muokattu] [:foo :muokattu] [:bar :muokattu])))))

(deftest uusin-muokkausaika-tyhja
  (testing "tyhjä arvojoukko -> oletusarvo"
    (is (= (time/date-time 1970 1 1 0 0 1) (uusin-muokkausaika '() [:fu])))))

(deftest uusin-muokkausaika-eiloydy
  (testing "uusinta muokkausaikaa ei löydy arvojen joukosta"
    (let [test-map {:mookattu :b}]
      (is (nil? (uusin-muokkausaika [test-map] [:muokattu]))))))

(deftest sisaltaako-kentat-test
  (let [test-data [{:etunimi "Ahto"
                    :sukunimi "Simakuutio"}
                   {:etunimi "Teemu"
                    :sukunimi "Teekkari"}]]
    (are [termi kentat maara] (= (count (filter #(sisaltaako-kentat?  % kentat termi) test-data)) maara)
         "ahto" [:etunimi] 1
         "to sima" [:etunimi :sukunimi] 1
         "aku ankka" [:etunimi :sukunimi] 0)))

(deftest diff-maps-test
  (let [old {:key1 1
             :key2 2
             :key3 3}
        new {:key2 2
             :key3 4
             :key4 4}]
     (is (= (diff-maps new old)
            {:key1 [nil 1]
             :key2 nil
             :key3 [4 3]
             :key4 [4 nil]}))))

(deftest muutos-test
  (let [old {:key1 1
             :key2 2
             :key3 3}
        new {:key2 2
             :key3 4
             :key4 4}]
     (is (= (muutos old new)
            {:key1 [1 nil]
             :key3 [3 4]
             :key4 [nil 4]}))))

(deftest map-by-test
  (let [coll [{:key 1
               :value "a"}
              {:key 2
               :value "b"}
              {:key 1
               :value "c"}
              {:value "d"}]]
    (is (= (map-by :key coll)
           {1 {:key 1
               :value "c"}
            2 {:key 2
               :value "b"}}))))

(deftest map-values-test
  (testing "Suorittaa funktion mapin arvoille ja säilyttää avaimet"
    (is (= (map-values inc {:a 1, :b 2})
           {:a 2, :b 3})))
  (testing "Säilyttää mapin tyypin"
    (is (sorted? (map-values inc (sorted-map :a 1))))))

(deftest retrying-test
  (testing "Testaa transaktio retry-logiikan toiminnan"
    (let [log (atom [])]
      (with-redefs [log/log* (fn [_ level e _]
                               (swap! log conj [level (.getMessage e)]))]
        (testing "retrying"
          (testing "suorittaa annetun koodilohkon ja palauttaa sen arvon"
            (is (= (retrying Exception 10 :foo) :foo)))

          (testing "suorittaa annetun koodilohkon uudelleen, jos se heittää poikkeuksen"
            (let [n (atom 0)]
              (is (= (retrying Exception 10 (if (< (swap! n inc) 3)
                                              (throw (Exception.))
                                              @n))
                     3))))

          (testing "ei suorita koodilohkoa uudelleen, jos poikkeus ei ole annetun tyyppinen"
            (let [n (atom 0)]
              (is (thrown-with-msg? Error #"1"
                    (retrying Exception 10 (throw (Error. (str (swap! n inc)))))))))

          (testing "päästää poikkeuksen läpi annetun yritysmäärän jälkeen"
            (let [n (atom 0)]
              (is (thrown-with-msg? Exception #"10"
                    (retrying Exception 10 (throw (Exception. (str (swap! n inc)))))))))

          (testing "logittaa jokaisen uudelleenyritykseen johtaneen poikkeuksen"
            (reset! log [])
            (let [n (atom 0)]
              (try
                (retrying Exception 3 (throw (Exception. (str (swap! n inc)))))
                (catch Exception _)))
            (is (= @log [[:warn "1"] [:warn "2"]]))))))))

(deftest some-value-with-not-found-test
  (is (nil? (some-value-with :name "John"
                             [{:name "Alice", :age 25},
                              {:name "Bob", :age 43}]))))

(deftest some-value-with-found-test
  (is (= (some-value-with :name "John"
                          [{:name "Alice", :age 25},
                           {:name "John", :age 31},
                           {:name "Bob", :age 43},
                           {:name "John", :age 70}])
         {:name "John", :age 31})))

(deftest poista-tyhjat-test
  (are [parametrit odotettu-tulos]
       (= (poista-tyhjat parametrit) odotettu-tulos)
       {:a true} {:a true}
       {:a 1} {:a 1}
       {:a "string"} {:a "string"}
       {:a ""} {}
       {:a nil} {}))

(deftest update-in-if-exists-test
  (let [m {:key1 {:key2 1}}]
    (is (= (update-in-if-exists m [:key1 :key2] inc) {:key1 {:key2 2}}))
    (is (= (update-in-if-exists m [:key1 :key2 :key3] inc) m))
    (is (= (update-in-if-exists m [:key1 :key2] + 10) {:key1 {:key2 11}}))))

(deftest select-and-rename-keys-test
  (let [m {:key1 :val1
           :key2 :val2}]
    (are [keyseq result] (= (select-and-rename-keys m keyseq) result)
         [:key1]               {:key1 :val1}
         [:key1 [:key2 :key3]] {:key1 :val1, :key3 :val2}
         [:key3 [:key4 :key5]] {})))

(deftest merkitse-voimassaolevat-test
  (testing
    "merkitse voimassaolevat:"
    (let [voimassaolon-paivitysfunktio (fn [entity arvo] :paivitetty)]
      (testing
        "päivittää kentän alkioiden voimassa-kentät:"
        (are [kuvaus entity kentta]
             (is (every? #{:paivitetty}
                         (map :voimassa
                              (get-in
                                (merkitse-voimassaolevat entity kentta voimassaolon-paivitysfunktio)
                                [kentta]))) kuvaus)
             "kentän vektori on tyhjä" {:kentta [{}]} :kentta
             "kentän vektorissa on yksi arvo" {:kentta [{}]} :kentta
             "kentän vektorissa on useampi arvo" {:kentta [{}, {}]} :kentta))
      (testing
        "palauttaa päivitetyn kentän vektorina:"
        (is (vector?
              (get-in
                (merkitse-voimassaolevat {:kentta [{}]} :kentta voimassaolon-paivitysfunktio)
                [:kentta]))
            "kenttä on vektori")))))

(deftest max-date-test
  (testing "max-date palauttaa suurimman annetuista päivämääristä"
    (let [p1 (time/local-date 2016 1 1)
          p2 (time/local-date 2016 1 2)
          p3 (time/local-date 2016 1 3)]
      (is (= (max-date p1) p1))
      (is (= (max-date p1 p2) p2))
      (is (= (max-date p2 p3 p1) p3)))))

(deftest min-date-test
  (testing "min-date palauttaa pienimmän annetuista päivämääristä"
    (let [p1 (time/local-date 2016 1 1)
          p2 (time/local-date 2016 1 2)
          p3 (time/local-date 2016 1 3)]
      (is (= (min-date p1) p1))
      (is (= (min-date p1 p2) p1))
      (is (= (min-date p2 p3 p1) p1)))))

(deftest erottele-lista-test
  (let [coll [{:id 1, :nimi "Eka", :sub-id 1, :sub-nimi "sub-eka"}
              {:id 1, :nimi "Eka", :sub-id 2, :sub-nimi "sub-toka"}
              {:id 2, :nimi "Toka", :sub-id 3, :sub-nimi "sub-kolmas"}
              {:id 2, :nimi "Toka", :sub-id 4, :sub-nimi "sub-neljas"}]]
       (is (= [{:id 1, :nimi "Eka", :sub [{:sub-id 1, :sub-nimi "sub-eka"} {:sub-id 2, :sub-nimi "sub-toka"}]}
               {:id 2, :nimi "Toka", :sub [{:sub-id 3, :sub-nimi "sub-kolmas"} {:sub-id 4, :sub-nimi "sub-neljas"}]}]
              (erottele-lista :sub [:sub-id :sub-nimi] coll))))
  (let [coll [{:id1 1, :id2 1, :sub-id 1, :sub-nimi "sub-eka"}
              {:id1 1, :id2 2, :sub-id 2, :sub-nimi "sub-toka"}
              {:id1 2, :id2 1, :sub-id 3, :sub-nimi "sub-kolmas"}
              {:id1 2, :id2 2, :sub-id 4, :sub-nimi "sub-neljas"}]]
       (is (= [{:id1 1, :id2 1, :sub [{:sub-id 1, :sub-nimi "sub-eka"}]}
               {:id1 1, :id2 2, :sub [{:sub-id 2, :sub-nimi "sub-toka"}]}
               {:id1 2, :id2 1, :sub [{:sub-id 3, :sub-nimi "sub-kolmas"}]}
               {:id1 2, :id2 2, :sub [{:sub-id 4, :sub-nimi "sub-neljas"}]}]
              (erottele-lista :sub [:sub-id :sub-nimi] coll)))))
