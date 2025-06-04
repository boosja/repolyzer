(ns repolyzer.is.git
  (:require [clojure.java.shell :as shell]
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
