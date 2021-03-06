(ns apex-stats.origin
  (:require
   [clojure.data.json :as json]
   [clojure.string :as string]
   [clj-http.client :as client]
   [clj-http.cookies :as cookies]
   [one-time.core :as otp]))

(def credentials
  {:email (System/getenv "ORIGIN_EMAIL")
   :password (System/getenv "ORIGIN_PASSWORD")
   :totp-secret (System/getenv "ORIGIN_TOTP_SECRET")})

(defn rand-str
  [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(def init-url
  (str "https://accounts.ea.com/connect"
       "/auth?response_type=code&client_id=ORIGIN_SPA_ID"
       "&display=originXWeb/login&locale=en_US&release_type=prod"
       "&redirect_uri=https://www.origin.com/views/login.html"))
(def access-token-url
  (str "https://accounts.ea.com/connect"
       "/auth?client_id=ORIGIN_JS_SDK&response_type=token"
       "&redirect_uri=nucleus:rest&prompt=none&release_type=prod"))
(def base-url "https://signin.ea.com")
(def cookie-store (atom nil))

(defn fid-init
  []
  (-> (client/get init-url {:cookie-store @cookie-store
                            :redirect-strategy :none})
      (get-in [:headers "location"])))

(defn init-session
  [location]
  (str base-url (-> (client/get location {:cookie-store @cookie-store
                                          :redirect-strategy :none})
                    (get-in [:headers "location"]))))

(defn authenticate
  [location]
  (let [location (-> (client/post location
                                  {:cookie-store @cookie-store
                                   :form-params
                                   {"email" (:email credentials)
                                    "password" (:password credentials)
                                    "_eventId" "submit"
                                    "cid" (rand-str 32)
                                    "showAgeUp" "true"
                                    "googleCaptchaResponse" ""
                                    "_rememberMe" "on"}
                                   :redirect-strategy :none})
                     (get-in [:headers "location"]))]
    (if (string/starts-with? location "/p/originX/login")
      (str base-url location)
      (throw (Exception. "Try Again.")))))

(defn totp
  [location]
  (let [location (-> (client/post location
                                  {:cookie-store @cookie-store
                                   :form-params {"codeType" "APP"
                                                 "_eventId" "submit"}
                                   :redirect-strategy :none})
                     (get-in [:headers "location"]))
        response (-> (client/post (str base-url location)
                                  {:cookie-store @cookie-store
                                   :form-params
                                   {"_trustThisDevice" "on"
                                    "oneTimeCode" (-> (:totp-secret credentials)
                                                      otp/get-totp-token)
                                    "_eventId" "submit"}}))]
    (when-not (= (:status response) 200)
      (throw (Exception. "TOTP Failed.")))))

(defn login
  []
  (reset! cookie-store (cookies/cookie-store))
  (let [location (fid-init)
        fid (get (re-find #"fid=(.*)" location) 1)
        _ (-> location
              (init-session)
              (authenticate)
              (totp))]
    (client/get (str init-url "&fid=" fid) {:cookie-store @cookie-store
                                            :redirect-strategy :none})))

(defn access-token
  []
  (or (some-> (client/get access-token-url
                          {:cookie-store @cookie-store
                           :redirect-strategy :none})
              :body
              (json/read-str)
              (get "access_token"))
      (do (login) (access-token))))

(defn me
  []
  (some-> (client/get "https://gateway.ea.com/proxy/identity/pids/me"
                      {:headers {"Authorization"
                                 (str "Bearer " (access-token))}})
          :body
          (json/read-str)
          (get "pid")))

(defn users-by-ids
  [ids]
  (some-> (client/get "https://api4.origin.com/atom/users"
                      {:headers {"AuthToken" (access-token)
                                 "Accept" "application/json"}
                       :query-params {"userIds" (if (coll? ids)
                                                  (string/join "," ids)
                                                  ids)}})
          :body
          (json/read-str :key-fn keyword)
          (get-in (cond-> [:users]
                    (string? ids) (conj 0)))
          (as-> users
              (map (fn [user]
                     {:uid (:userId user)
                      :name (:eaId user)})
                   users))))

(defn search-users
  [username]
  (some-> (client/get (str "https://api" (rand-nth [1 2 3 4])
                           ".origin.com/xsearch/users")
                      {:headers {"AuthToken" (access-token)}
                       :query-params {"userId" (get (me) "pidId")
                                      "searchTerm" username
                                      "start" 0}
                       :socket-timeout 1000
                       :connection-timeout 1000})
          :body
          (json/read-str)
          (get "infoList")
          (as-> users
              (->> users
                   (map (fn [{:strs [friendUserId]}] friendUserId))
                   (partition 5)
                   (mapcat users-by-ids)))))

(defn -main
  [& [username]]
  (some-> (search-users username)
          first
          :uid
          (Long/parseLong)
          println))
