@echo off

SETLOCAL

if NOT DEFINED RED5_HOME set RED5_HOME=%~dp0

if NOT DEFINED JAVA_HOME goto err

REM JAVA options
REM You can set JVM additional options here if you want
if NOT DEFINED JVM_OPTS set JVM_OPTS=-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xms256m -Xmx1g -XX:InitialCodeCacheSize=8m -XX:MaxGCPauseMillis=500 -XX:ReservedCodeCacheSize=32m
REM Set up logging options
set LOGGING_OPTS=-Dlogback.ContextSelector=org.red5.logging.LoggingContextSelector -Dcatalina.useNaming=true
REM Set up security options
REM set SECURITY_OPTS=-Djava.security.debug=failure -Djava.security.manager -Djava.security.policy="%RED5_HOME%/conf/red5.policy"
set SECURITY_OPTS=-Djava.security.debug=failure
REM Set up tomcat options
set TOMCAT_OPTS=-Dcatalina.home=%RED5_HOME%
REM Native path
set NATIVE=-Djava.library.path="%RED5_HOME%\lib\amd64-Windows-msvc"
REM Combined java options
set JAVA_OPTS=%LOGGING_OPTS% %SECURITY_OPTS% %JAVA_OPTS% %JVM_OPTS% %TOMCAT_OPTS% %NATIVE%

set RED5_CLASSPATH=%RED5_HOME%\red5-service.jar;%RED5_HOME%\conf;%CLASSPATH%

if NOT DEFINED RED5_MAINCLASS set RED5_MAINCLASS=org.red5.server.Bootstrap

if NOT DEFINED RED5_OPTS set RED5_OPTS=9999

goto launchRed5

:launchRed5
echo Starting Red5
"%JAVA_HOME%\bin\java" %JAVA_OPTS% -cp "%RED5_CLASSPATH%" -noverify %RED5_MAINCLASS% %RED5_OPTS%
goto finally

:err
echo JAVA_HOME environment variable not set! Take a look at the readme.
pause

:finally
ENDLOCAL
