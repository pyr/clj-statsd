(ns clj-statsd.test
  (:use [clj-statsd]
        [clojure.test]))

(use-fixtures :each (fn [f] (setup "localhost" 8125) (f)))

(defmacro should-send-expected-stat
  "Assert that the expected stat is passed to the send-stat method
   the expected number of times."
  [expected min-times max-times & body]
  `(let [counter# (atom 0)]
    (with-redefs
      [send-stat (fn [stat#]
                   (is (= ~expected stat#))
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
    (increment :gorets 1.1)))

(deftest should-send-decrement
  (should-send-expected-stat "gorets:-1|c" 3 3
    (decrement "gorets")
    (decrement :gorets)
    (decrement "gorets", 1))
  (should-send-expected-stat "gorets:-7|c" 1 1
    (decrement :gorets 7))
  (should-send-expected-stat "gorets:-1.1|c" 1 1
    (decrement :gorets 1.1)))

(deftest should-send-gauge
  (should-send-expected-stat "gaugor:333|g" 3 3
    (gauge "gaugor" 333)
    (gauge :gaugor 333)
    (gauge "gaugor" 333 1))
  (should-send-expected-stat "guagor:1.1|g" 1 1
    (gauge :guagor 1.1)))

(deftest should-send-unique
  (should-send-expected-stat "unique:765|s" 2 2
    (unique "unique" 765)
    (unique :unique 765)))

(deftest should-send-timing-with-default-rate
  (should-send-expected-stat "glork:320.000000|ms" 2 2
    (timing "glork" 320)
    (timing :glork 320)))

(deftest should-send-timing-with-provided-rate
  (should-send-expected-stat "glork:320.000000|ms|@0.990000" 1 10
    (dotimes [n 10] (timing "glork" 320 0.99))))

(deftest should-not-send-stat-without-cfg
  (with-redefs [cfg (atom nil)]
    (should-send-expected-stat "gorets:1|c" 0 0 (increment "gorets"))))

(deftest should-time-code
  (let [cnt (atom 0)]
    (with-redefs [timing
                  (fn [k v rate]
                    (is (= "test.time" k))
                    (is (>= v 200))
                    (is (= 1.0 rate))
                    (swap! cnt inc))]
      (with-timing "test.time"
        (Thread/sleep 200))
      (with-sampled-timing "test.time" 1.0
        (Thread/sleep 200))
      (is (= @cnt 2)))))

(deftest should-prefix
  (with-redefs [cfg (atom nil)]
    (setup "localhost" 8125 :prefix "test.stats.")
    (should-send-expected-stat "test.stats.gorets:1|c" 2 2
      (increment "gorets")
      (increment :gorets))))
