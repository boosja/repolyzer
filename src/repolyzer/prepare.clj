(ns repolyzer.prepare
  (:require [clojure.set :as set]
            [datomic.api :as d]))

(defn ->ld [inst]
  (java.time.LocalDate/ofInstant (.toInstant inst) (java.time.ZoneId/of "Europe/Oslo")))

(defn commits-per-month [db]
  (->> (d/q '[:find [?date ...]
              :where
              [?e :commit/full-hash]
              [?e :commit.author/date ?date]]
            db)
       (map (fn [d]
              (let [d (->ld d)]
                (str (.getYear d) "-" (.getMonthValue d)))))
       (frequencies)
       vec))

(defn sum-characters [stats subject body]
  (let [s-count (+ (or (:s-count stats) 0)
                   (count subject))
        b-count (+ (or (:b-count stats) 0)
                   (count body))]
   {:s-count s-count
    :b-count b-count
    :sum (+ (or (:sum stats) 0)
            (count subject)
            (count body))}))

(defn character-stats [db]
  (->> (d/q '[:find [?c ...]
              :where
              [?c :commit/full-hash ?hash]]
            db)
       (map #(d/entity db %))
       (reduce (fn [character-stats commit]
                 (let [stats (update character-stats
                                     (-> commit :commit/committer :person/name)
                                     #(sum-characters % (:commit/subject commit)
                                                      (:commit/body commit)))]
                   (if (:commit/co-authors commit)
                     (reduce (fn [st co-a]
                               (update st (:person/name co-a)
                                       #(sum-characters % (:commit/subject commit)
                                                        (:commit/body commit))))
                             stats
                             (:commit/co-authors commit))
                     stats)))
               {})))

(defn prepare [db]
  {:commits-per-month (commits-per-month db)
   :character-stats (character-stats db)})

(comment

  (def db (d/db (:conn @repolyzer.core/ctx)))

  (->> (d/q '[:find ?c ?name
              :where [?c :commit/committer ?name]]
            db)
       (every? (comp not nil? second)))




  )
