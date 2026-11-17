@echo off
cd /d "%~dp0"
java -jar dist\win\phonebook.jar %*
pause
