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
  [uid]
  (let [domain "r5-crossplay.r5prod.stryder.respawn.com"
        response (client/get (str "https://" domain "/user.php")
                             {:headers {"User-Agent" "Respawn HTTPS/1.0"
                                        "Accept" "application/json"}
                              :query-params {:qt "user-getinfo"
                                             :getinfo 1
                                             :hardware "PC"
                                             :uid uid}
                              :throw-exceptions true})]
    (when (= (:status response) 200)
      (some->> response
               :body
               string/split-lines
               (drop 1)
               (apply str)
               (#(json/read-str % :key-fn (fn [k] (keyword "banner" k))))))))

(defn valid?
  [info]
  (-> info :banner/cdata0 boolean))

(def ^:dynamic cdata->path
  {:banner/uid                    [:banner/uid]
   :banner/name                   [:banner/name]
   :banner/cdata2                 [:banner/legend]
   :banner/cdata3                 [:banner/skin]
   :banner/cdata4                 [:banner/frame]
   :banner/cdata5                 [:banner/pose]
   :banner/cdata6                 [:banner/badges 0 :label]
   :banner/cdata7                 [:banner/badges 0 :value]
   :banner/cdata8                 [:banner/badges 1 :label]
   :banner/cdata9                 [:banner/badges 1 :value]
   :banner/cdata10                [:banner/badges 2 :label]
   :banner/cdata11                [:banner/badges 2 :value]
   :banner/cdata12                [:banner/trackers 0 :label]
   :banner/cdata13                [:banner/trackers 0 :value]
   :banner/cdata14                [:banner/trackers 1 :label]
   :banner/cdata15                [:banner/trackers 1 :value]
   :banner/cdata16                [:banner/trackers 2 :label]
   :banner/cdata17                [:banner/trackers 2 :value]
   :banner/cdata18                [:banner/intro]
   :banner/cdata23                [:banner/account :level]
   :banner/cdata24                [:banner/account :progress]
   :banner/cdata31                [:banner/status :in-match?]
   :banner/rankScore              [:banner/rank :points]
   :banner/online                 [:banner/status :online?]
   :banner/joinable               [:banner/status :joinable?]
   :banner/partyFull              [:banner/status :party :full?]
   :banner/partyInMatch           [:banner/status :party :in-match?]
   :banner/timeSinceServerChange  [:banner/seconds-since-server-change]})

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

(def lookup
  (->> (mapcat (fn [stat]
                 (vals (-> (io/resource stat)
                           io/reader
                           PushbackReader.
                           edn/read)))
               ["skins.edn"
                "poses.edn"
                "frames.edn"
                "badges.edn"
                "trackers.edn"
                "intros.edn"])
       (apply merge)
       (merge (-> (io/resource "legends.edn")
                  io/reader
                  PushbackReader.
                  edn/read))))

(defn parse
  [info]
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
                       {:banner/badges [{} {} {}]
                        :banner/trackers [{} {} {}]})
                info)
        (assoc-in [:banner/rank :name] (-> info :banner/rankScore rank))
        (update-in [:banner/account :progress] #(str % "%"))
        (update-in [:banner/account :level] #(some-> % inc))
        (update-in [:banner/badges]
                   (fn [badges]
                     (mapv (fn [{:keys [label value]}]
                             {label (when label (dec value))})
                           badges)))
        (update-in [:banner/trackers]
                   (fn [trackers]
                     (mapv (fn [{:keys [label value]}]
                             {label (when label (-> value
                                                    (- 2)
                                                    (/ 100)
                                                    int))})
                           trackers))))))

(defn -main
  "Search for an Apex Legends player's stats via their Origin UID"
  [& [uid]]
  (let [uid (or uid (slurp *in*))
        info (user-info uid)
        result (-> info parse)]
    (if (and result (valid? info))
      (pprint/pprint
       (apply dissoc result
              (set/difference (set (keys result))
                              (set (keys cdata->path))
                              (set (map first (vals cdata->path))))))
      (println "No Apex Legends data for that Origin UID was found."))))
