@echo off
setlocal
cd /d "%~dp0"
if not exist dist\Asteroids.jar (
    call build.bat
    if errorlevel 1 goto failed
)
java -jar dist\Asteroids.jar
if errorlevel 1 goto failed
exit /b 0
:failed
echo Could not launch Asteroids. Check that Java is on your PATH.
pause
exit /b 1
