(defproject samples "0.1.0-SNAPSHOT"
  :description "Metamodel examples"
  :url "https://github.com/friemen/metam"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [metam/core "1.0.6"]
                 [instaparse "1.3.1"]
                 [net.sourceforge.plantuml/plantuml "7965"]]
  :repl-options  {:port 9090})
