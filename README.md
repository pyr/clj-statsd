clj-statsd is a client for the [statsd](https://github.com/etsy/statsd)
protocol for the [clojure](http://clojure.org) programming language.

[![Clojars Project](https://img.shields.io/clojars/v/clj-statsd.svg)](https://clojars.org/clj-statsd)

An Example
----------

Here is a snippet showing the use of clj-statsd:

```clojure
(ns testing
    (:require [clj-statsd :as s]))

(s/setup "127.0.0.1" 8125)

;; Set a shared prefix for all stats keys
(s/setup "127.0.0.1" 8125 :prefix :my-app)

(s/increment :some_counter)             ;; simple increment
(s/decrement "some_other_counter")      ;; simple decrement
(s/increment :some_counter 2)           ;; double increment
(s/increment :some_counter 2 0.1)       ;; sampled double increment

(s/timing :timing_value 300)            ;; record 300ms for "timing_value"

(s/gauge :current_value 42)             ;; record an arbitrary value
(s/modify-gauge :current_value -2)      ;; offset a gauge


(s/with-timing :some_slow_code          ;; time (some-slow-code) and then
 (some-slow-code))                      ;; send the result using s/timing

(s/with-sampled-timing :slow_code 1.0   ;; Like s/with-timing but with
 (slow-code)                            ;; a sample rate.

(s/with-tagged-timing :slow 1.0 ["foo"] ;; Like s/with-timing but with
 (slow)                                 ;; a sample rate and tags.
```

Buckets can be strings or keywords. For more information please refer to
[statsd](https://github.com/etsy/statsd)

Shutdown
--------

Since clj-statsd uses agents, [(shutdown-agents)](https://clojuredocs.org/clojure.core/shutdown-agents) must be called when exiting the program.
