(ns repolyzer.parser
  (:require [clojure.instant :as inst]
            [clojure.string :as str]))

(defn ->file [[added removed file]]
  (cond-> {:file/name file}

    (and (= "-" added) (= "-" removed))
    (assoc :file/binary? true)

    (and (some? (parse-long added)) (some? (parse-long removed)))
    (assoc :file/added (Integer/parseInt added)
           :file/removed (Integer/parseInt removed))))

(defn extract-co-authors [body]
  (when-let [matches (re-seq #"Co-authored-by:\s+([^<]+?)\s+<([^>]+)>" body)]
    (map (fn [author]
           {:db/id (str/lower-case (nth author 2))
            :person/name (nth author 1)
            :person/email (str/lower-case (nth author 2))})
         matches)))

(defn without-co-authors [body]
  (first (str/split body #"(\n+)?Co-authored-by:")))

(defn ->txes [commits]
  (let [people (atom {})
        pers-or-ref (fn [{:keys [:person/email :db/id] :as p}]
                      (if (contains? @people email)
                        id
                        (get (swap! people assoc email p) email)))]
    (->> commits
         (map (fn [[full-hash
                    author-name author-email author-date
                    committer-name committer-email committer-date
                    subject body numstats]]
                (let [author-email (str/lower-case author-email)]
                  (into {} (remove (comp nil? val)
                                   {:commit/full-hash full-hash
                                    :commit/subject subject
                                    :commit/body (-> body without-co-authors)
                                    :commit.author/date (inst/read-instant-date author-date)
                                    :commit.committer/date (inst/read-instant-date committer-date)
                                    :commit/author (pers-or-ref
                                                    {:db/id author-email
                                                     :person/email author-email
                                                     :person/name author-name})
                                    :commit/committer (pers-or-ref
                                                       {:db/id committer-email
                                                        :person/email committer-email
                                                        :person/name committer-name})
                                    :commit/co-authors (->> (extract-co-authors body)
                                                            (map pers-or-ref)
                                                            seq)
                                    :commit/filestats
                                    (mapv #(assoc % :db/id (str (hash (str full-hash (:file/name %)))))
                                          (map ->file numstats))}))))))))
