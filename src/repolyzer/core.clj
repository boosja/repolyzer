(ns repolyzer.core
  (:require [clojure.java.io :as io]
            [compojure.core :refer [defroutes HEAD GET]]
            [compojure.route :as route]
            [datomic.api :as d]
            [org.httpkit.server :as server]
            [replicant.string :as r]
            [repolyzer.is.db :as db]
            [repolyzer.prepare :as prepare]))

(set! *print-namespace-maps* false)

(defonce ctx (atom {}))

(defn read-resource [path]
  (read-string (slurp (io/resource path))))

(defn init-ctx []
  (let [conn (db/init-conn)
        txes (read-resource "data/matnyttig.edn")]
    @(d/transact conn txes)
    (swap! ctx assoc :conn (db/init-conn))
    )
  )

(defn get-data-str []
  ;; Temporary file (not committed)
  (read-string (slurp (io/resource "public/data.edn"))))

(defn handler [_request]
  (let [db (d/db (:conn @ctx))]
    {:status 200
     :body (r/render [:html
                      [:head
                       [:title "Repolyzer"]
                       [:meta {:charset "utf-8"}]
                       [:script {:id "data"
                                 :type "application/edn"
                                 :innerHTML (pr-str (prepare/prepare db))}]]
                      [:body
                       [:h1 "Get ready to Repo your Lyzer!"]
                       [:div#app
                        [:script {:src "/js/main.js"}]
                        [:script {:innerHTML "repolyzer.is.client.init()"}]]]])}))

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
    (init-ctx)
    (println (format "Repolyzer startet on port %s" (server/server-port server)))))

(comment

  ;; See what is on the classpath
  (clojure.string/split (System/getProperty "java.class.path") #":")

  )
