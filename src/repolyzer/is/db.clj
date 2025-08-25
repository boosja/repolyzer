(ns repolyzer.is.db
  (:require [datomic.api :as d]
            [repolyzer.is.git :as git]
            [repolyzer.parser :as parser]))

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
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit/committer
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :commit/co-authors
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

(def uri "datomic:mem://git-history")

(defn init-conn []
  (d/create-database uri)
  (let [conn (d/connect uri)]
    @(d/transact conn schema)
    conn))

(comment

  (set! *print-namespace-maps* false)

  (d/delete-database uri)

  (def uri "datomic:mem://git-history")
  (d/create-database uri)
  (def conn (d/connect uri))

  @(d/transact conn schema)

  ;; import git data
  (def repo "/Users/mathias/repos/boosja/chipper-chaps-chateau")
  (def commits (git/get-git-commits {:repo-path repo}))
  (def txes (parser/->txes commits))

  (spit "dev-resources/public/data.edn" (pr-str txes))
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
