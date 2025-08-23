(ns repolyzer.core
  (:require [clojure.java.io :as io]
            [compojure.core :refer [defroutes HEAD GET]]
            [compojure.route :as route]
            [org.httpkit.server :as server]
            [replicant.string :as r]))

(defn get-data-str []
  ;; Temporary file (not committed)
  (read-string (slurp (io/resource "public/data.edn"))))

(defn handler [request]
  {:status 200
   :body (r/render [:html
                    [:head
                     [:title "Repolyzer"]
                     [:meta {:charset "utf-8"}]
                     [:script {:id "data"
                               :type "application/edn"
                               :innerHTML (pr-str (get-data-str))}]]
                    [:body
                     [:h1 "Get ready to Repo your Lyzer!"]
                     [:div#app
                      [:script {:src "/js/main.js"}]
                      [:script {:innerHTML "repolyzer.is.client.init()"}]]]])})

(defroutes app-routes
  (HEAD "/" _ {:status 202})
  (GET "/" req (handler req))
  (route/resources "/")
  )

(defn start! [opts]
  (let [server (server/run-server #'app-routes
                                  (merge {:legacy-return-value? false
                                          :host "0.0.0.0"
                                          :port 7777}
                                         opts))]
    (println (format "Repolyzer startet on port %s"
                     (server/server-port server)))))

(comment

  ;; See what is on the classpath
  (clojure.string/split (System/getProperty "java.class.path") #":")

  )
