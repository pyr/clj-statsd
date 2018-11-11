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

(defn- with-rate [fmt]
  (str fmt "|@%f"))

(def increment-fmt "%s:%s|c")
(def increment-fmt-rate (with-rate increment-fmt))
(def timing-fmt "%s:%d|ms")
(def timing-fmt-rate (with-rate timing-fmt))
(def gauge-fmt "%s:%s|g")
(def gauge-fmt-rate (with-rate gauge-fmt))
(def unique-fmt "%s:%d|s")
(def unique-fmt-rate (with-rate unique-fmt))

(defn publish
  "Send a metric over the network, based on the provided sampling rate.
  This should be a fully formatted statsd metric line."

  [^String fmt ^String fmt-rate k v rate tags]
  (cond
    (nil? @cfg)
    nil

    (>= rate 1.0)
    (send-stat (:prefix @cfg) (format fmt (name k) v) tags)

    (<= (.nextDouble ^Random (:random @cfg)) rate)
    (send-stat (:prefix @cfg) (format fmt-rate (name k) v rate) tags)

    :else
    nil))

(defn increment
  "Increment a counter at specified rate, defaults to a one increment
  with a 1.0 rate"
  ([k]             (increment k 1 1.0 []))
  ([k v]           (increment k v 1.0 []))
  ([k v rate]      (increment k v rate []))
  ([k v rate tags] (publish increment-fmt increment-fmt-rate k v rate tags)))

(defn round-millis
  "Given a numeric value of milliseconds, convert it to an integer value of
  milliseconds by rounding to the nearest millisecond if necessary."
  [v]
  (cond (integer? v) v
        (number? v) (Math/round (double v))
        :else 0))

(defn timing
  "Time an event at specified rate, defaults to 1.0 rate"
  ([k v]           (timing k v 1.0))
  ([k v rate]      (timing k v rate []))
  ([k v rate tags] (publish timing-fmt timing-fmt-rate k v rate tags)))

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
  ([k v rate tags] (publish gauge-fmt gauge-fmt-rate k v rate tags)))

(defn unique
  "Send an event, unique occurences of which per flush interval
   will be counted by the statsd server. We have no rate call
   signature here because that wouldn't make much sense."
  ([k v]      (publish unique-fmt unique-fmt-rate k v 1.0 []))
  ([k v tags] (publish unique-fmt unique-fmt-rate k v 1.0 tags)))

(defn with-timing-fn
  "Helper function for the timing macros. Time the execution of f, a function
   of no args, and then call timing with the other args."
  [f k rate tags]
  (let [start (System/nanoTime)]
    (try
     (f)
     (finally
      (timing k (/ (- (System/nanoTime) start) 1e6) rate tags)))))

(defmacro with-tagged-timing
  "Time the execution of the provided code, with sampling and tags."
  [k rate tags & body]
  `(with-timing-fn (fn [] ~@body) ~k ~rate ~tags))

(defmacro with-sampled-timing
  "Time the execution of the provided code, with sampling."
  [k rate & body]
  `(with-timing-fn (fn [] ~@body) ~k ~rate []))

(defmacro with-timing
  "Time the execution of the provided code."
  [k & body]
  `(with-timing-fn (fn [] ~@body) ~k 1.0 []))
