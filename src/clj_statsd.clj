(ns clj-statsd
  "Send metrics to statsd."
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

(defn send-stat
  "Send a raw metric over the network."
  [^String content]
  (when-let [packet (try
                      (DatagramPacket.
                       ^"[B" (.getBytes content)
                       ^Integer (count content)
                       ^InetAddress (:host @cfg)
                       ^Integer (:port @cfg))
                      (catch Exception e
                        nil))]
    (send sockagt send-packet packet)))

(defn publish
  "Send a metric over the network, based on the provided sampling rate.
  This should be a fully formatted statsd metric line."
  [^String content rate]
  (let [prefix (:prefix @cfg)
        content (if prefix (str prefix content) content)]
    (cond
      (nil? @cfg) nil
      (>= rate 1.0) (send-stat content)
      (<= (.nextDouble ^Random (:random @cfg)) rate) (send-stat (format "%s|@%f" content rate)))))

(defn increment
  "Increment a counter at specified rate, defaults to a one increment
  with a 1.0 rate"
  ([k]        (increment k 1 1.0))
  ([k v]      (increment k v 1.0))
  ([k v rate] (publish (format "%s:%s|c" (name k) v) rate)))

(defn timing
  "Time an event at specified rate, defaults to 1.0 rate"
  ([k v]      (timing k v 1.0))
  ([k v rate] (publish (format "%s:%f|ms" (name k) (double v)) rate)))

(defn decrement
  "Decrement a counter at specified rate, defaults to a one decrement
  with a 1.0 rate"
  ([k]        (increment k -1 1.0))
  ([k v]      (increment k (* -1 v) 1.0))
  ([k v rate] (increment k (* -1 v) rate)))

(defn gauge
  "Send an arbitrary value."
  ([k v]      (gauge k v 1.0))
  ([k v rate] (publish (format "%s:%s|g" (name k) v) rate)))

(defn unique
  "Send an event, unique occurences of which per flush interval
   will be counted by the statsd server. We have no rate call
   signature here because that wouldn't make much sense."
  ([k v] (publish (format "%s:%d|s" (name k) v) 1.0)))

(defmacro with-sampled-timing
  "Time the execution of the provided code, with sampling."
  [k rate & body]
  `(let [start# (System/nanoTime)
         result# (do ~@body)]
    (timing ~k (double (/ (- (System/nanoTime) start#) 1000000)) ~rate)
    result#))

(defmacro with-timing
  "Time the execution of the provided code."
  [k & body]
  `(with-sampled-timing ~k 1.0 ~@body))
