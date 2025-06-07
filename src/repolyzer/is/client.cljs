(ns repolyzer.is.client)

(defn ^:dev/after-load init []
  (js/console.log "[Startet]"))

(defn ^:dev/before-load refresh []
  (js/console.log "[Updated]"))
