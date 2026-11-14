@echo off
setlocal
cd /d "%~dp0"
if not exist build\tests mkdir build\tests
javac -encoding UTF-8 -source 8 -target 8 -d build\tests src\*.java tests\*.java || exit /b 1
cd build
java -Djava.awt.headless=true -cp tests AsteroidsTests || exit /b 1
java -cp tests MixerPlaybackTests
