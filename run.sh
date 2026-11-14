#!/bin/sh
set -eu
cd "$(dirname "$0")"
[ -f dist/Asteroids.jar ] || sh build.sh
exec java -jar dist/Asteroids.jar
