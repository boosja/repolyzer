(ns repolyzer.prepare
  (:require [clojure.set :as set]
            [datomic.api :as d]))

(defn entities [db entity-ids]
  (map #(d/entity db %) entity-ids))

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
       seq))

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

(defn gather-char-stats [db]
  (->> (d/q '[:find [?p ...]
              :where [?p :person/email]]
            db)
       (entities db)
       (keep (fn [p] [(:person/name p) (set/union (:commit/_committer p)
                                                  (:commit/_co-authors p))]))
       (reduce (fn [res [name commits]]
                 (assoc res name
                        (reduce
                         (fn [counts commit]
                           (-> counts
                               (update :antall-commits (fnil inc 0))
                               (update :subj-chars (fnil + 0) (count (:commit/subject commit)))
                               (update :body-chars (fnil + 0) (count (:commit/body commit)))))
                         {} commits)))
               {})
       (map (fn [st]
              (update st 1
                      #(assoc %
                              :subj-avg-chars (int (/ (:subj-chars %) (:antall-commits %)))
                              :body-avg-chars (int (/ (:body-chars %) (:antall-commits %)))
                              :char-sum (+ (:subj-chars %) (:body-chars %))))))
       (into {})))

(defn prepare-character-datasets [char-stats]
  (reduce (fn [datasets [name stats]]
            (reduce (fn [dset stat]
                      (update dset (key stat) conj [name (val stat)]))
                    datasets stats))
          {}
          char-stats))

(defn prepare [db]
  (-> {:commits-per-month (commits-per-month db)}
      (merge (prepare-character-datasets (gather-char-stats db)))))

(comment

  (def db (d/db (:conn @repolyzer.core/ctx)))

  (prepare db)

  (commits-per-month db)
  (prepare-character-datasets (gather-char-stats db))

  (->> (d/q '[:find ?c ?name
              :where [?c :commit/committer ?name]]
            db)
       (every? (comp not nil? second)))




  )
