(ns repolyzer.git
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def format-options [:commit/full-hash "%H"
                     :author/name "%an"
                     :author/email "%ae"
                     :author/date "%aI"
                     :committer/name "%cn"
                     :committer/email "%ce"
                     :committer/date "%cI"
                     :commit/subject "%s"
                     :commit/body "%b"])

(def default-options
  {:repo-path "\"\""
   :separator "|=≈=|"
   :options format-options
   :with-numstat false})

(defn get-git-log [opts]
  (let [{:keys [repo-path separator options with-numstat]} (merge default-options opts)
        format-str (str/join separator [(->> options
                                             (keep-indexed #(when (odd? %1) %2))
                                             (str/join separator))])]
    (-> (str/join " " (keep identity ["git" "-c" "core.quotepath=false"
                                      "-C" repo-path "log" (when with-numstat "--numstat")
                                      (str "--pretty=format:" format-str)]))
        (str/split #"\s")
        (->> (apply p/sh) :out))))

(defn partitionize [acc line]
  (if (re-matches #"^[a-f0-9]{40}.*" line)
    (conj acc [line])
    (update acc (dec (count acc))
            conj line)))

(defn process-commit [c]
  (let [[commit rest] (split-at 8 c)
        [body-lines numstat-lines] (split-with #(not (re-matches #"^[\d-]+\t[\d-]+\t.*" %)) rest)
        git-body (->> body-lines (str/join "\n") str/trim)
        numstats (->> (remove empty? numstat-lines)
                      (mapv #(str/split % #"\t")))]
    (-> (conj (vec commit)
              git-body
              numstats))))

(defn get-git-commits [{:keys [repo-path]}]
  (let [log (get-git-log {:repo-path repo-path
                          :separator "%n"
                          :with-numstat true})]
    (->> (str/split log #"\n")
         (reduce partitionize [])
         (mapv process-commit))))

(defn clone [repo-url]
  (let [temp-dir (fs/create-temp-dir)]
    (-> (p/process "git" "clone" "--no-checkout" repo-url temp-dir)
        p/check)
    temp-dir))

(comment

  (def temp-dir (fs/create-temp-dir))
  (def repo-url "git@github.com:Mattilsynet/matnyttig.git")

  (p/shell "git" "clone" "--no-checkout" repo-url temp-dir)

  (fs/delete-tree temp-dir)

  )
