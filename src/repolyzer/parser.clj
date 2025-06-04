(ns repolyzer.parser
  (:require [clojure.instant :as inst]))

(defn ->file [[added removed file]]
  (cond-> {:file/name file}

    (and (= "-" added) (= "-" removed))
    (assoc :file/binary? true)

    (and (some? (parse-long added)) (some? (parse-long removed)))
    (assoc :file/added (Integer/parseInt added)
           :file/removed (Integer/parseInt removed))))

(defn ->txes [lines]
  (->> lines
       (map (fn [[full-hash
                  author-name author-email author-date
                  committer-name committer-email committer-date
                  subject body numstats]]
              (into {} (remove (comp nil? val)
                               {:commit/full-hash full-hash
                                :commit/subject subject
                                :commit/body body
                                :commit.author/date (inst/read-instant-date author-date)
                                :commit.committer/date (inst/read-instant-date committer-date)
                                :commit/author {:db/id author-email
                                                :person/email author-email
                                                :person/name author-name}
                                :commit/committer {:db/id committer-email
                                                   :person/email committer-email
                                                   :person/name committer-name}
                                :commit/filestats
                                (mapv #(assoc % :db/id (str (hash (str full-hash (:file/name %)))))
                                      (map ->file numstats))}))))))
