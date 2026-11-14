#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/tests
javac -encoding UTF-8 -source 8 -target 8 -d build/tests src/*.java tests/*.java
cd build
java -Djava.awt.headless=true -cp tests AsteroidsTests
java -cp tests MixerPlaybackTests
