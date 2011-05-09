(ns clj-statsd
  (:import [java.util Random])
  (:import [java.net DatagramPacket DatagramSocket InetAddress]))

;; global defines
(def agt        (agent nil))
(def cfg        {})
(def setup-done false)

;; 
(defn setup
  "initialize configuration"
  [host port]
  (def cfg (assoc cfg
                  :random (Random.)
                  :host   (InetAddress/getByName host)
                  :port   port
                  :sock   (DatagramSocket.)))
  (send agt (fn [_] (cfg :sock)))
  (def setup-done true))

(defn publish
  "send a metric over the network"
  [content rate]
  (let [data  (.getBytes content)
        len   (.length (into [] data))
        pkt   (DatagramPacket. data len (cfg :host) (cfg :port))
        f     (fn [sock pkt] (try (.send sock pkt) (catch Exception e nil)) sock)
        spl?  (< rate 1.0)
        match (<= (.nextDouble (cfg :random)) rate)
        send? (or (not spl?) match)
        fmt   (if spl? (format "|@%f" rate) "")]
    (if send? (send agt f pkt))))

(defn increment
  ([k]        (increment k 1 1.0))
  ([k v]      (increment k v 1.0))
  ([k v rate] (publish (format "%s:%d|c" (name k) v) rate)))

(defn timing
  ([k v]      (timing k v 1.0))
  ([k v rate] (publish (format "%s:%d|ms" (name k) v) rate)))

(defn decrement
  ([k]        (increment k -1 1.0))
  ([k v]      (increment k (* -1 v) 1.0))
  ([k v rate] (increment k (* -1 v) rate)))
