; With a little bit of help from Josh and
; http://kuriqoo.blogspot.co.uk/2011/03/clojure-in-practice-yahoo-weather.html

(ns huebris.core
  (:require [environ.core :refer [env]]
            [clj-http.client :as client]
            [overtone.at-at :as at-at]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clj-time.local :as time-local]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.contrib.zip-filter.xml :as zf]))

(declare sunset-lights-task)

(defstruct units :temp :distance :pressure :speed)
(defstruct wind :chill :speed)
(defstruct astronomy :sunrise :sunset)
(defstruct condition :text :temp :date)

(def yahoourl "http://weather.yahooapis.com/forecastrss?u=c&w=")
(def huerl (env :hue-url))
(def woeid-london "44418")
(def task-pool (at-at/mk-pool))
(def am-pm-formatter (time-format/formatter "h:mm a"))

(defn get-weather-feed [woeid]
  (zip/xml-zip (xml/parse (str yahoourl woeid))))

(defn get-weather [woeid]
  (let [zipper (get-weather-feed woeid)
        z-location (zf/xml1-> zipper :channel :yweather:location)
        z-units (zf/xml1-> zipper :channel :yweather:units)
        z-wind (zf/xml1-> zipper :channel :yweather:wind)
        z-astronomy (zf/xml1-> zipper :channel :yweather:astronomy)
        z-condition (zf/xml1-> zipper :channel :item :yweather:condition)]
    {
    :city (zf/attr z-location :city)
    :country (zf/attr z-location :country)
    :units (struct-map units
                       :temp (zf/attr z-units :temperature)
                       :distance (zf/attr z-units :distance)
                       :pressure (zf/attr z-units :pressure)
                       :speed (zf/attr z-units :speed))
    :wind (struct-map wind
                       :chill (zf/attr z-wind :chill)
                       :speed (zf/attr z-wind :speed))
    :astronomy (struct-map astronomy
                       :sunrise (zf/attr z-astronomy :sunrise)
                       :sunset (zf/attr z-astronomy :sunset))
    :condition (struct-map condition
                       :temp (zf/attr z-condition :temp)
                       :text (zf/attr z-condition :text)
                       :date (zf/attr z-condition :date))
     }))

(defn print-weather [info]
  (let [condition (:condition info)
        units (:units info)]
  (println "Weather at" (:date condition) ","
           (:city info) "," (:country info))
  (println (condition :text) ","
           (condition :temp)
           (units :temp))))

(defn print-weather-london
  []
  (print-weather (get-weather woeid-london)))

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

(defn bow-chicka-wow-wow []
  (do
    (hue-groups-state 0 {:on true :sat 255})
    (at-at/every 20000 #(slow-pulse) task-pool :desc "Sensually pulsating hues")))

(defn party []
  (at-at/every 4000 #(turn-it-off-and-on-again) task-pool :desc "Flicker lights"))

(defn stop []
  (at-at/stop-and-reset-pool! task-pool))

(defn next-datetime-for-time [am-pm-time]
  (let [next-datetime (time/today-at (time/hour am-pm-time) (time/minute am-pm-time))]
    (if
      (time/before? (time-local/local-now) next-datetime)
      next-datetime
      (time/plus next-datetime (time/days 1)))))

(defn seconds-till-time-of-day [time-of-day]
  (time/in-seconds (time/interval (time-local/local-now) (next-datetime-for-time time-of-day))))

(defn seconds-till-am-pm-time [am-pm-time]
  (seconds-till-time-of-day (time-format/parse am-pm-formatter am-pm-time)))

(defn seconds-till-sunset []
  (seconds-till-am-pm-time (:sunset (:astronomy (get-weather woeid-london)))))

(defn seconds-till-sunrise []
  (seconds-till-am-pm-time (:sunrise (:astronomy (get-weather woeid-london)))))

(defn schedule-lights-on-sunset []
  (let [ms (* 1000 (seconds-till-sunset))]
    (println "at-at/after " ms "lights on")
    (at-at/after ms #(sunset-lights-task) task-pool :desc "Lights on at sunset")))

(defn sunset-lights-task
  "Turn on the lights and schedule the next task"
  []
  (do
    (hue-groups-state 0 {:on true})
    (at-at/after 60000 #(schedule-lights-on-sunset))))


(defn -main []
  (schedule-lights-on-sunset))
