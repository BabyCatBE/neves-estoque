@echo off
cd /d "%~dp0"
call npm.cmd run local
if errorlevel 1 pause
