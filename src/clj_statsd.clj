(ns clj-statsd
  "Send metrics to statsd."
  (:require [clojure.string :as str])
  (:import [java.util Random])
  (:import [java.net DatagramPacket DatagramSocket InetAddress]))

(def
  ^{:doc "Atom holding the socket configuration"}
  cfg
  (atom nil))

(def
  ^{:doc "Agent holding the datagram socket"}
  sockagt
  (agent nil))

(defn setup
  "Initialize configuration"
  [host port & opts]
  (send sockagt #(or % (DatagramSocket.)))
  (swap! cfg #(or % (merge {:random (Random.)
                            :host   (InetAddress/getByName host)
                            :port   (if (integer? port) port (Integer/parseInt port))}
                           (apply hash-map opts)))))

(defn- send-packet
  ""
  [^DatagramSocket socket ^DatagramPacket packet]
  (try
    (doto socket (.send packet))
    (catch Exception e
      socket)))

(defn format-tags
  [tags]
  (when (seq tags)
    (str "|#" (str/join "," (map name tags)))))

(defn format-stat
  [prefix content tags]
  (str prefix content (format-tags tags)))

(defn send-stat
  "Send a raw metric over the network."
  [prefix content tags]
  (let [fmt (format-stat prefix content tags)]
    (when-let [packet (try
                        (DatagramPacket.
                         ^"[B" (.getBytes fmt)
                         ^Integer (count fmt)
                         ^InetAddress (:host @cfg)
                         ^Integer (:port @cfg))
                        (catch Exception e
                          nil))]
      (send sockagt send-packet packet))))

(defn publish
  "Send a metric over the network, based on the provided sampling rate.
  This should be a fully formatted statsd metric line."

  [^String content rate tags]
  (cond
    (nil? @cfg)
    nil

    (>= rate 1.0)
    (send-stat (:prefix @cfg) content tags)

    (<= (.nextDouble ^Random (:random @cfg)) rate)
    (send-stat (:prefix @cfg) (format "%s|@%f" content rate) tags)

    :else
    nil))

(defn increment
  "Increment a counter at specified rate, defaults to a one increment
  with a 1.0 rate"
  ([k]             (increment k 1 1.0 []))
  ([k v]           (increment k v 1.0 []))
  ([k v rate]      (increment k v rate []))
  ([k v rate tags] (publish (format "%s:%s|c" (name k) v) rate tags)))

(defn timing
  "Time an event at specified rate, defaults to 1.0 rate"
  ([k v]           (timing k v 1.0))
  ([k v rate]      (timing k v rate []))
  ([k v rate tags] (publish (format "%s:%d|ms" (name k) v) rate tags)))

(defn decrement
  "Decrement a counter at specified rate, defaults to a one decrement
  with a 1.0 rate"
  ([k]             (increment k -1 1.0))
  ([k v]           (increment k (* -1 v) 1.0))
  ([k v rate]      (increment k (* -1 v) rate))
  ([k v rate tags] (increment k (* -1 v) rate tags)))

(defn gauge
  "Send an arbitrary value."
  ([k v]           (gauge k v 1.0 []))
  ([k v rate]      (gauge k v rate []))
  ([k v rate tags] (publish (format "%s:%s|g" (name k) v) rate tags)))

(defn unique
  "Send an event, unique occurences of which per flush interval
   will be counted by the statsd server. We have no rate call
   signature here because that wouldn't make much sense."
  ([k v]      (publish (format "%s:%d|s" (name k) v) 1.0 []))
  ([k v tags] (publish (format "%s:%d|s" (name k) v) 1.0 tags)))

(defmacro with-sampled-timing
  "Time the execution of the provided code, with sampling."
  [k rate & body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)]
    (timing ~k (- (System/currentTimeMillis) start#) ~rate)
    result#))

(defmacro with-timing
  "Time the execution of the provided code."
  [k & body]
  `(with-sampled-timing ~k 1.0 ~@body))
