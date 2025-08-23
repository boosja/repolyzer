(ns repolyzer.is.client
  (:require ["d3" :as d3]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [datascript.core :as ds]
            [replicant.alias :refer [defalias]]
            [replicant.dom :as r]
            [replicant.string :as replicant]))

(def schema {:person/email {:db/cardinality :db.cardinality/one
                            :db/unique :db.unique/identity}
             :commit/full-hash {:db/cardinality :db.cardinality/one
                                :db/unique :db.unique/identity}

             :commit/author {:db/cardinality :db.cardinality/many
                             :db/type :db.type/ref}
             :commit/committer {:db/cardinality :db.cardinality/many
                                :db/type :db.type/ref}
             :commit/co-authors {:db/cardinality :db.cardinality/many
                                 :db/type :db.type/ref}
             :commit/filestats {:db/cardinality :db.cardinality/many
                                :db/type :db.type/ref}})

(defonce conn (ds/create-conn schema))

(defonce commits-per-month '([#inst "2024-02-01T00:00:00.000-00:00" 1]
                             [#inst "2024-03-01T00:00:00.000-00:00" 26]
                             [#inst "2024-04-01T00:00:00.000-00:00" 136]
                             [#inst "2024-05-01T00:00:00.000-00:00" 125]
                             [#inst "2024-06-01T00:00:00.000-00:00" 37]
                             [#inst "2024-07-01T00:00:00.000-00:00" 57]
                             [#inst "2024-08-01T00:00:00.000-00:00" 141]
                             [#inst "2024-09-01T00:00:00.000-00:00" 197]
                             [#inst "2024-10-01T00:00:00.000-00:00" 350]
                             [#inst "2024-11-01T00:00:00.000-00:00" 314]
                             [#inst "2024-12-01T00:00:00.000-00:00" 199]
                             [#inst "2025-01-01T00:00:00.000-00:00" 425]
                             [#inst "2025-02-01T00:00:00.000-00:00" 503]
                             [#inst "2025-03-01T00:00:00.000-00:00" 487]
                             [#inst "2025-04-01T00:00:00.000-00:00" 430]
                             [#inst "2025-05-01T00:00:00.000-00:00" 322]
                             [#inst "2025-06-01T00:00:00.000-00:00" 31]))

(defonce cpm (map #(let [date (js/Date. (str (first %)))]
                     (array-map :date date
                                :value (second %)
                                :label (str (.getFullYear date) "-"
                                            (+ 1 (.getMonth date)))))
                  commits-per-month))

(defonce store (atom {:init 0}))

(defn make-d3 [node]
  (let [margin {:top 40 :right 30 :bottom 70 :left 60}
        width (- 1000 (:right margin) (:left margin))
        height (- 500 (:top margin) (:bottom margin))
        svg (doto (.append (d3/select node) "svg")
              (.attr "viewBox" (str "0 0 "
                                    (+ width (:right margin) (:left margin))
                                    " "
                                    (+ height (:top margin) (:bottom margin))))
              #_#_(.attr "width" (+ width (:right margin) (:left margin)))
              (.attr "height" (+ height (:top margin) (:bottom margin))))
        g (doto (.append svg "g")
            (.attr "transform" (str "translate(" (:left margin) ", " (:top margin) ")")))
        x-scale (doto (d3/scaleBand)
                  (.domain (mapv :label cpm))
                  (.range [0 width])
                  (.padding 0.1))
        y-scale (doto (d3/scaleLinear)
                  (.domain [0 (apply max (map :value cpm))])
                  (.nice)
                  (.range [height 0]))]
    (-> (.selectAll g ".bar")
        (.data cpm)
        (.enter)
        (.append "rect")
        (.attr "class" "bar")
        (.attr "fill" "#445bed")
        (.attr "x" #(x-scale (:label %)))
        (.attr "width" (.bandwidth x-scale))
        (.attr "y" #(y-scale (:value %)))
        (.attr "height" #(- height (y-scale (:value %)))))

    ;; x-axis
    (-> (.append g "g")
        (.attr "class" "axis")
        (.attr "transform" (str "translate(0, " height ")"))
        (.call (d3/axisBottom x-scale))
        (.selectAll "text")
        (.style "text-anchor" "end")
        (.attr "dx" "-.8em")
        (.attr "dy" ".15em")
        (.attr "transform" "rotate(-45)"))

    ;; y-axis
    (-> (.append g "g")
        (.attr "class" "axis")
        (.call (d3/axisLeft y-scale)))

    ;; title
    (-> (.append g "text")
        (.attr "class" "title")
        (.attr "x" (- (/ width 2) 100))
        (.attr "y" -10)
        (.text "Commits per month"))
    (.-node svg)))

(defn dispatch [e actions]
  (when (= :replicant.trigger/life-cycle
           (:replicant/trigger e))
    (println "The node" (:replicant/node e))
    (println "The data" actions)
    (make-d3 (:replicant/node e))))

(defalias histogram []
  [:div.histogram
   {:replicant/on-render
    [::d3-histogram [:what :is :this?]]}])

(defn app []
  [:main
   [:h2 "wowoasa"]
   [::histogram]])

(defn transact-data! []
  (when-let [s (some-> "data"
                       js/document.getElementById
                       .-innerText
                       str/trim
                       not-empty)]
    (ds/transact! conn (edn/read-string {} s))
    (prn "Package has been delivered!")))

(defn ^:dev/after-load start []
  (js/console.log "[START]")
  (r/set-dispatch! dispatch)
  (js/setTimeout #(transact-data!) 0)
  (add-watch store :app (fn [_ _ _ _]
                          (r/render (js/document.getElementById "app")
                                    (app))))
  (swap! store update :init inc))

(defn ^:export init []
  (start))

(defn ^:dev/before-load refresh []
  (js/console.log "[STOP]"))

(comment

  (replicant/render (app))

  )
