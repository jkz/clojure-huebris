(ns huebris.core
  (:require [clj-http.client :as client])
  (:require [overtone.at-at :as at-at]))

(def huerl "http://192.168.1.70/api/jessethegame")

(def task-pool (at-at/mk-pool))

(defn hue-groups-state
  [group, params]
  (do
    (client/put
      (str huerl "/groups/" group "/action")
      {:as :json
       :form-params params
       :content-type :json
       :throw-entire-message? true})
    (println group params)))

(defn turn-it-off-and-on-again
  []
  (do
    (hue-groups-state 0 {:on false})
    (at-at/after 2000 #(hue-groups-state 0 {:on true :hue (rand-int 65535) :sat 255 :transitiontime 1}) task-pool)))

(defn slow-pulse
  []
  (do
    (hue-groups-state 0 {:bri 127 :transitiontime 50})
    (at-at/after 10000 #(hue-groups-state 0 {:bri 255 :hue (rand-int 65535) :transitiontime 50}) task-pool)))

(defn bow-chicka-wow-wow
  []
  (do
    (hue-groups-state 0 {:on true :sat 255})
    (at-at/every 20000 #(slow-pulse) task-pool :desc "Sensually pulsating hues")))

(defn party
  []
  (at-at/every 4000 #(turn-it-off-and-on-again) task-pool :desc "Flicker lights"))

(defn stop
  []
  (at-at/stop-and-reset-pool! task-pool))

(defn -main [])
