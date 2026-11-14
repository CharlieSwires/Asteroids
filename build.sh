#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes dist
javac -encoding UTF-8 -source 8 -target 8 -d build/classes src/*.java
jar cfe dist/Asteroids.jar Asteroids -C build/classes .
