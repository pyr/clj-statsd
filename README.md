clj-statsd is a client for the [statsd](https://github.com/etsy/statsd)
protocol for the [clojure](http://clojure.org) programming language.

[![Build
Status](https://secure.travis-ci.org/pyr/clj-statsd.png)](http://travis-ci.org/pyr/clj-statsd)

[![Clojars Project](https://img.shields.io/clojars/v/clj-statsd.svg)](https://clojars.org/clj-statsd)

An Example
----------

Here is a snippet showing the use of clj-statsd:


```clojure

    (ns testing
        (:require [clj-statsd :as s]))

    (s/setup "127.0.0.1" 8125)


    ;; set a shared prefix for all stats keys
    (s/setup "127.0.0.1" 8125 :prefix :my-app)


    (s/increment :some_counter)              ; simple increment
    (s/decrement "some_other_counter")       ; simple decrement
    (s/increment :some_counter 2)            ; double increment
    (s/increment :some_counter 2 0.1)        ; sampled double increment

    (s/timing :timing_value 300)             ; record 300ms for "timing_value"

    (s/with-timing :some_expensive_operation ; record time for a sequence
        (some-expensive-operation))          ; of operations

    (s/gauge :current_value 42)              ; record an arbitrary value

```

Buckets can be strings or keywords. For more information please refer to
[statsd](https://github.com/etsy/statsd)

Installing
----------

The easiest way to use clj-statsd in your own projects is via
[Leiningen](http://github.com/technomancy/leiningen). Add the following
dependency to your project.clj file:

    [clj-statsd "0.4.0"]

To build from source, run the following commands:

    lein deps
    lein jar
