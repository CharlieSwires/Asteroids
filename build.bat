@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
if not exist dist mkdir dist
javac -encoding UTF-8 -source 8 -target 8 -d build\classes src\*.java || exit /b 1
jar cfe dist\Asteroids.jar Asteroids -C build\classes .
