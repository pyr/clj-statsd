(ns clj-statsd.statsd-test
  (:require
   [clojure.test :refer [are deftest is use-fixtures]]
   [clj-statsd   :refer [cfg decrement format-stat gauge increment
                         round-millis send-stat setup timing unique
                         with-sampled-timing with-tagged-timing with-timing]]))

(use-fixtures :each (fn [f] (setup "localhost" 8125) (f)))

(defmacro should-send-expected-stat
  "Assert that the expected stat is passed to the send-stat method
   the expected number of times."
  [expected min-times max-times & body]
  `(let [counter# (atom 0)]
     (with-redefs
      [send-stat (fn [prefix# stat# tags#]
                   (is (= ~expected (format-stat prefix# stat# tags#)))
                   (swap! counter# inc))]
       ~@body)
     (is (and (>= @counter# ~min-times) (<= @counter# ~max-times)) (str "send-stat called " @counter# " times"))))

(deftest should-send-increment
  (should-send-expected-stat "gorets:1|c" 3 3
                             (increment "gorets")
                             (increment :gorets)
                             (increment "gorets", 1))
  (should-send-expected-stat "gorets:7|c" 1 1
                             (increment :gorets 7))
  (should-send-expected-stat "gorets:1.1|c" 1 1
                             (increment :gorets 1.1))
  (should-send-expected-stat "gorets:1.1|c" 1 1
                             (increment :gorets 1.1 2))
  (should-send-expected-stat "gorets:1.1|c|#tag1,tag2" 1 1
                             (increment :gorets 1.1 2 ["tag1" "tag2"]))
  (should-send-expected-stat "gorets:1.1|c|#tag1,tag2" 1 1
                             (increment :gorets 1.1 2 [:tag1 :tag2])))

(deftest should-send-decrement
  (should-send-expected-stat "gorets:-1|c" 3 3
                             (decrement "gorets")
                             (decrement :gorets)
                             (decrement "gorets", 1))
  (should-send-expected-stat "gorets:-7|c" 1 1
                             (decrement :gorets 7))
  (should-send-expected-stat "gorets:-1.1|c" 1 1
                             (decrement :gorets 1.1))
  (should-send-expected-stat "gorets:-1.1|c" 1 1
                             (decrement :gorets 1.1 2))
  (should-send-expected-stat "gorets:-1.1|c|#tag1,tag2" 1 1
                             (decrement :gorets 1.1 2 ["tag1" "tag2"])))

(deftest should-send-gauge
  (should-send-expected-stat "gaugor:333|g" 3 3
                             (gauge "gaugor" 333)
                             (gauge :gaugor 333)
                             (gauge "gaugor" 333 1))
  (should-send-expected-stat "guagor:1.1|g" 1 1
                             (gauge :guagor 1.1))
  (should-send-expected-stat "guagor:1.1|g" 1 1
                             (gauge :guagor 1.1 2))
  (should-send-expected-stat "guagor:1.1|g|#tag1,tag2" 1 1
                             (gauge :guagor 1.1 2 ["tag1" "tag2"])))

(deftest should-send-unique
  (should-send-expected-stat "unique:765|s" 2 2
                             (unique "unique" 765)
                             (unique :unique 765)))

(deftest should-round-millis
  (are [input expected]
       (= expected (round-millis input))

       ; Good values
    0 0
    99 99
    100 100
    0.0 0
    0.4 0
    99.9 100
    100.0 100

       ; Weird-but-legal values
    1/3 0
    2/3 1
    -0.5 0
    -0.6 -1
    -99.9 -100

       ; Bad values
    nil 0
    "bad value" 0
    :bad-value 0))

(deftest should-send-timing-with-default-rate
  (should-send-expected-stat "glork:320|ms" 2 2
                             (timing "glork" 320)
                             (timing :glork 320))
  (should-send-expected-stat "glork:320|ms|#tag1,tag2" 2 2
                             (timing "glork" 320 1 ["tag1" "tag2"])
                             (timing :glork 320 1 ["tag1" "tag2"])))

(deftest should-send-timing-with-provided-rate
  (should-send-expected-stat "glork:320|ms|@0.990000" 1 10
                             (dotimes [_ 10] (timing "glork" 320 0.99))))

(deftest should-not-send-stat-without-cfg
  (with-redefs [cfg (atom nil)]
    (should-send-expected-stat "gorets:1|c" 0 0 (increment "gorets"))))

(deftest should-time-code
  (let [calls (atom [])]
    (with-redefs [timing (fn [& args]
                           (swap! calls conj args))]
      (with-timing "test.time"
        (Thread/sleep 200))
      (let [[k v rate tags] (last @calls)]
        (is (= "test.time" k))
        (is (>= v 200))
        (is (= 1.0 rate))
        (is (= [] tags)))
      (with-sampled-timing "test.time" 0.9
        (Thread/sleep 200))
      (let [[k v rate tags] (last @calls)]
        (is (= "test.time" k))
        (is (>= v 200))
        (is (= 0.9 rate))
        (is (= [] tags)))
      (with-tagged-timing "test.time" 0.9 ["tag1" "tag2"]
        (Thread/sleep 200))
      (let [[k v rate tags] (last @calls)]
        (is (= "test.time" k))
        (is (>= v 200))
        (is (= 0.9 rate))
        (is (= ["tag1" "tag2"] tags))))))

(deftest should-prefix
  (with-redefs [cfg (atom nil)]
    (setup "localhost" 8125 :prefix "test.stats.")
    (should-send-expected-stat "test.stats.gorets:1|c" 2 2
                               (increment "gorets")
                               (increment :gorets))))
