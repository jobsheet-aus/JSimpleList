@echo off
call gradlew.bat assembleDebug
if errorlevel 1 exit /b %errorlevel%

git diff --check
if errorlevel 1 exit /b %errorlevel%