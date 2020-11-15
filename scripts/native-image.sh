#!/bin/sh

clojure -A:native-image --graalvm-home=/graalvm/ && \
    chown -R $(id -u):$(id -g) ./target
