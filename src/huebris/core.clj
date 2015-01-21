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

(defstruct astronomy :sunrise :sunset)

(def yahoourl "http://weather.yahooapis.com/forecastrss?u=c&w=")
(def huerl (env :hue-url))
(def woeid-london "44418")
(def task-pool (at-at/mk-pool))
(def am-pm-formatter (time-format/formatter "h:mm a"))

(defn get-weather-feed [woeid]
  (zip/xml-zip (xml/parse (str yahoourl woeid))))

(defn get-weather [woeid]
  (let [zipper (get-weather-feed woeid)
        z-astronomy (zf/xml1-> zipper :channel :yweather:astronomy)]
    {
    :astronomy (struct-map astronomy
                       :sunrise (zf/attr z-astronomy :sunrise)
                       :sunset (zf/attr z-astronomy :sunset))
     }))

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
