(ns repolyzer.unpack
  (:require [babashka.process :as p]
            [clojure.java.io :as io]
            [repolyzer.git :as git]
            [repolyzer.parser :as parser]))

(defn echo [s]
  (p/shell "echo" s))

(defn ensure-file-path [file]
  (-> file .getParentFile .mkdirs))

(defn ^:export main [[repo-url]]
  (let [_ (echo (str "Cloning " repo-url "..."))
        dir (git/clone repo-url)
        #_#__ (echo (str "Succesfully cloned to " dir))
        _ (echo "Unpacking git history...")
        txes (->> (git/get-git-commits {:repo-path dir})
                  parser/->txes)
        file (io/file "resources/data/matnyttig.edn")]
    (ensure-file-path file)
    (echo (str "Writing to " file "..."))
    (spit file (pr-str txes))
    (echo "Done")))
