@echo off

SETLOCAL

set SERVICE_NAME=Red5

echo Uninstalling Red5 service
prunsrv //DS//%SERVICE_NAME%

ENDLOCAL
