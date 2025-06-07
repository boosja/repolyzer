(ns repolyzer.core
  (:require [compojure.core :refer [defroutes HEAD GET]]
            [compojure.route :as route]
            [org.httpkit.server :as server]
            [replicant.string :as r]))

(defn handler [request]
  {:status 200
   :body (r/render [:html
                    [:head
                     [:title "Repolyzer"]
                     [:script {:src "/js/main.js"}]]
                    [:body
                     [:h1 "Get ready to Repo your Lyzer!"]]])})

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
