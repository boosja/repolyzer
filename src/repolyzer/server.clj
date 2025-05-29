(ns repolyzer.server
  (:require [clojure.instant :as inst]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [datomic.api :as d]))

(def schema
  [{:db/ident :commit/full-hash
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit/subject
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit/body
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit/author
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident :commit/committer
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident :commit.author/date
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit.committer/date
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}

   {:db/ident :person/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :person/email
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit/filestats
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}

   {:db/ident :file/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :file/binary?
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one}

   {:db/ident :file/added
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}

   {:db/ident :file/removed
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}
   ])

(def format-options [:commit/full-hash "%H"
                     :author/name "%an"
                     :author/email "%ae"
                     :author/date "%aI"
                     :committer/name "%cn"
                     :committer/email "%ce"
                     :committer/date "%cI"
                     :commit/subject "%s"
                     :commit/body "%b"])

(defn get-git-log [opts]
  (let [{:keys [repo-path separator options with-numstat]}
        (merge {:repo-path "\"\""
                :separator "|=≈=|"
                :options format-options
                :with-numstat false}
               opts)
        format-str (str/join separator [(->> options
                                             (keep-indexed #(when (odd? %1) %2))
                                             (str/join separator))])]
    (-> (str/join " " (keep identity ["git" "-C" repo-path "log" (when with-numstat "--numstat")
                                      (str "--pretty=format:" format-str)]))
        (str/split #"\s")
        (->> (apply shell/sh) :out))))

(defn partitionize [acc line]
  (if (re-matches #"^[a-f0-9]{40}.*" line)
    (conj acc [line])
    (update acc (dec (count acc))
            conj line)))

(defn process-line [line]
  (let [[newline rest] (split-at 8 line)
        [body-lines numstat-lines] (split-with #(not (re-matches #"^[\d-]+\t[\d-]+\t.*" %)) rest)
        git-body (->> body-lines (str/join "\n") str/trim)
        numstats (->> (remove empty? numstat-lines)
                      (mapv #(str/split % #"\t")))]
    (-> (conj (vec newline)
              git-body
              numstats))))

(defn get-git-commits [{:keys [repo-path]}]
  (let [log (get-git-log {:repo-path repo-path
                          :separator "%n"
                          :with-numstat true})]
    (->> (str/split log #"\n")
         (reduce partitionize [])
         (mapv process-line))))

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

(comment

  (set! *print-namespace-maps* false)

  (def uri "datomic:mem://git-history")
  (d/create-database uri)
  (def conn (d/connect uri))

  @(d/transact conn schema)

  ;; import git data
  (def repo "")
  (def commits (get-git-commits {:repo-path repo}))
  (def txes (->txes commits))
  (d/transact conn txes)


  (def db (d/db conn))

  ;; See "first" 20 commits in db
  (->> (d/q '[:find [?e ...]
              :where
              [?e :commit/full-hash]]
            db)
       (map #(d/entity db %))
       (map #(into {} %))
       (take 20))

  (->> (d/q '[:find [?e ...]
              :where
              [?e :commit/author ?p]
              [?p :person/email ""]]
            db)
       (map #(d/entity db %))
       (map :commit/subject)
       #_(map #(into {} %)))

  ;; take into account co-author
  (->> (d/q '[:find ?epost ?hash
              :where
              [?c :commit/full-hash ?hash]
              [?c :commit/author ?p]
              [?p :person/email ?epost]]
            db)
       (reduce (fn [stats [epost _hash]]
                 (update stats epost (fnil inc 0)))
               {})
       #_#_#_(map #(d/entity db %))
       (map #(into {} %))
       (take 10)
       #_(group-by))

  ;; How many characters written in subject and body per author
  (->> (d/q '[:find ?name ?hash ?sub ?body
              :where
              [?c :commit/full-hash ?hash]
              [?c :commit/subject ?sub]
              [?c :commit/body ?body]
              [?c :commit/author ?p]
              [?p :person/name ?name]]
            db)
       (reduce (fn [stats [name _hash sub body]]
                 (update stats name (fn [{:keys [subject-characters body-characters]}]
                                      {:subject-characters
                                       (+ (or subject-characters 0) (count sub))
                                       :body-characters
                                       (+ (or body-characters 0) (count body))})))
               {}))


  )
