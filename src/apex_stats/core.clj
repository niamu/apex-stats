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
               (#(json/read-str % :key-fn keyword))))))

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
  (let [;; In case a tool wants to use qualified keywords in `cdata->path`
        unqualified-fn (fn [m] (reduce (fn [accl [k v]]
                                        (assoc accl
                                               (-> k name keyword)
                                               v))
                                      {}
                                      m))
        unqualified-cdata->path (unqualified-fn cdata->path)
        unqualified-info (unqualified-fn info)
        badge-keys-fn (juxt :cdata6
                            :cdata7
                            :cdata8
                            :cdata9
                            :cdata10
                            :cdata11)
        badges-kw (->> (map first (badge-keys-fn unqualified-cdata->path))
                       (remove nil?)
                       set
                       first)
        tracker-keys-fn (juxt :cdata12
                              :cdata13
                              :cdata14
                              :cdata15
                              :cdata16
                              :cdata17)
        trackers-kw (->> (map first (tracker-keys-fn unqualified-cdata->path))
                         (remove nil?)
                         set
                         first)
        rank-kw (first (:rankScore unqualified-cdata->path))
        account-kw (first (:cdata23 unqualified-cdata->path))
        parsed-result (reduce (fn [accl [k v]]
                                (let [nk (cdata->path k)]
                                  (cond-> accl
                                    nk (-> (dissoc k)
                                           (assoc-in nk
                                                     (if (string/ends-with?
                                                          (name (last nk))
                                                          "?")
                                                       (pos? v)
                                                       (get lookup v v)))))))
                              (merge info
                                     (when (->> (badge-keys-fn unqualified-info)
                                                (some #(not (nil? %))))
                                       {badges-kw [{} {} {}]})
                                     (when (->> (tracker-keys-fn
                                                 unqualified-info)
                                                (some #(not (nil? %))))
                                       {trackers-kw [{} {} {}]}))
                              info)]
    (cond-> parsed-result
      (:rankScore unqualified-info)
      (assoc-in [rank-kw :name] (-> unqualified-info :rankScore rank))

      (get-in parsed-result [account-kw :level])
      (update-in [account-kw :level] inc)

      (get-in parsed-result [badges-kw])
      (update-in [badges-kw]
                 (fn [badges]
                   (mapv (fn [{:keys [label value]}]
                           {label (when label (dec value))})
                         badges)))

      (get-in parsed-result [trackers-kw])
      (update-in [trackers-kw]
                 (fn [trackers]
                   (mapv (fn [{:keys [label value]}]
                           {label (/ (- value 2) 100)})
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
