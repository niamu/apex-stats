(ns apex-stats.core
  (:gen-class)
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [clj-http.lite.client :as client])
  (:import
   [java.io PushbackReader]))

(set! *warn-on-reflection* true)

(defn user-info
  [id]
  (some->> (client/get "https://r5-pc.stryder.respawn.com/user.php"
                       {:headers {"User-Agent" "Respawn HTTPS/1.0"
                                  "Accept" "application/json"}
                        :query-params {"qt" "user-getinfo"
                                       "getinfo" "1"
                                       "hardware" "PC"
                                       "uid" id
                                       "language" "english"
                                       "timezoneOffset" "1"
                                       "ugc" "1"
                                       "rep" "1"
                                       "searching" "0"
                                       "change" "7"
                                       "loadidx" "1"}})
           :body
           string/split-lines
           (drop 3)
           (apply str)
           (#(json/read-str % :key-fn keyword))))

(defn valid?
  [info]
  (-> info :cdata0 boolean))

(def ^:dynamic cdata->path
  {:cdata2                 [:legend]
   :cdata3                 [:skin]
   :cdata4                 [:frame]
   :cdata5                 [:pose]
   :cdata6                 [:badges 0 :label]
   :cdata7                 [:badges 0 :value]
   :cdata8                 [:badges 1 :label]
   :cdata9                 [:badges 1 :value]
   :cdata10                [:badges 2 :label]
   :cdata11                [:badges 2 :value]
   :cdata12                [:trackers 0 :label]
   :cdata13                [:trackers 0 :value]
   :cdata14                [:trackers 1 :label]
   :cdata15                [:trackers 1 :value]
   :cdata16                [:trackers 2 :label]
   :cdata17                [:trackers 2 :value]
   :cdata18                [:intro]
   :cdata23                [:account :level]
   :cdata24                [:account :progress]
   :cdata31                [:status :in-match?]
   :rankScore              [:rank :points]
   :online                 [:status :online?]
   :joinable               [:status :joinable?]
   :partyFull              [:status :party :full?]
   :partyInMatch           [:status :party :in-match?]
   :timeSinceServerChange  [:seconds-since-server-change]})

(defn rank
  [score]
  (->> {"Bronze" [0 1200]
        "Silver" [1200 2800]
        "Gold" [2800 4800]
        "Platinum" [4800 7200]
        "Diamond" [7200 10000]
        "Master" [10000 20000]
        "Apex Predator" [20000 100000]}
       (reduce (fn [accl [k [r1 r2]]]
                 (if (contains? (set (range r1 r2)) score)
                   (get (->> (partition 2 1 (range r1 (inc r2) (/ (- r2 r1) 4)))
                             (map-indexed (fn [idx x]
                                            {(contains? (set (apply range x))
                                                        score)
                                             (if (= "Apex Predator" k)
                                               k
                                               (str k " "
                                                    (get {1 "IV"
                                                          2 "III"
                                                          3 "II"
                                                          4 "I"}
                                                         (inc idx))))}))
                             (apply merge))
                        true)
                   accl))
               "")))

(defn parse
  [info]
  (let [lookup (->> (mapcat (fn [stat]
                              (vals (-> (io/resource (str (name stat) ".edn"))
                                        io/reader
                                        PushbackReader.
                                        edn/read)))
                            [:skins
                             :poses
                             :frames
                             :badges
                             :trackers
                             :intros])
                    (apply merge)
                    (merge (-> (io/resource "legends.edn")
                               io/reader
                               PushbackReader.
                               edn/read)))]
    (when (valid? info)
      (-> (reduce (fn [accl [k v]]
                    (let [nk (cdata->path k)]
                      (cond-> accl
                        nk (-> (dissoc k)
                               (assoc-in nk
                                         (if (string/ends-with? (name (last nk))
                                                                "?")
                                           (not (zero? v))
                                           (get lookup v v)))))))
                  (merge info
                         {:badges [{} {} {}]
                          :trackers [{} {} {}]})
                  info)
          (assoc-in [:rank :name] (-> info :rankScore rank))
          (update-in [:account :progress] #(str % "%"))
          (update-in [:account :level] inc)
          (update-in [:badges]
                     (fn [badges]
                       (mapv (fn [{:keys [label value]}]
                               {label (when label (dec value))})
                             badges)))
          (update-in [:trackers]
                     (fn [trackers]
                       (mapv (fn [{:keys [label value]}]
                               {label (when label
                                        (let [s (->> (str value)
                                                     (drop-last 2)
                                                     (apply str))]
                                          (if (empty? s)
                                            0
                                            (Long/parseLong s))))})
                             trackers)))))))

(defn -main
  "Search for an Apex Legends player's stats via their Origin username"
  [& [uid]]
  (if-let [result (some->> (user-info uid)
                           parse)]
    (pprint/pprint
     (apply dissoc result
            (set/difference (set (keys result))
                            (set (keys cdata->path))
                            (set (map first (vals cdata->path))))))
    (println "No Apex Legends data for that username was found.")))
