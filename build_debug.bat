@echo off
call gradlew.bat assembleDebug
if errorlevel 1 exit /b 1

git diff --check
if errorlevel 1 exit /b 1

exit /b 0