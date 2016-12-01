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

(defproject clojure-utils "0.1.0-SNAPSHOT"
  :description "OPH Clojure-utils"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-core "1.2.1"]
                 [org.clojure/tools.logging "0.2.6"]

                 [ch.qos.logback/logback-classic "1.0.13"]
                 [cheshire "5.4.0"]
                 [clj-http "1.0.1"]
                 [clj-time "0.9.0"]
                 [com.cemerick/valip "0.3.2"]
                 [org.flatland/useful "0.11.5"]
                 [compojure "1.3.3"]
                 [korma "0.4.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [prismatic/schema "0.4.0"]]
  :plugins [[test2junit "1.0.1"]
            [lein-typed "0.3.5"]]
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :test-selectors {:kaikki (constantly true)
                   :default  (complement (some-fn :integraatio :performance))
                   :performance :performance
                   :integraatio :integraatio}
  :jar-name "clojure-utils.jar")

