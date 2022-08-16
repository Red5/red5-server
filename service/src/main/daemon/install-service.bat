@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem ---------------------------------------------------------------------------
rem Windows Service Install script // jvm version
rem
rem ---------------------------------------------------------------------------

SETLOCAL

if NOT DEFINED RED5_HOME set RED5_HOME=%~dp0

echo Processor Architecture: "%PROCESSOR_ARCHITECTURE%"
if "%PROCESSOR_ARCHITECTURE%" == "AMD64" (
    set "EXECUTABLE=%RED5_HOME%\amd64\prunsrv.exe"
) else (
    set "EXECUTABLE=%RED5_HOME%\prunsrv.exe"
)
echo Using Daemon:           "%EXECUTABLE%"
set SERVICE_NAME=Red5
set "CLASSPATH=%RED5_HOME%\commons-daemon-1.0.15.jar;%RED5_HOME%\red5-service.jar;%RED5_HOME%\conf"
set "WORKING_PATH=%RED5_HOME%\"

rem Make sure prerequisite environment variables are set
if not "%JAVA_HOME%" == "" goto gotJdkHome
if not "%JRE_HOME%" == "" goto gotJreHome
echo Neither the JAVA_HOME nor the JRE_HOME environment variable is defined
echo Service will try to guess them from the registry.
goto okJavaHome
:gotJreHome
if not exist "%JRE_HOME%\bin\java.exe" goto noJavaHome
if not exist "%JRE_HOME%\bin\javaw.exe" goto noJavaHome
goto okJavaHome
:gotJdkHome
if not exist "%JAVA_HOME%\jre\bin\java.exe" goto noJavaHome
if not exist "%JAVA_HOME%\jre\bin\javaw.exe" goto noJavaHome
if not exist "%JAVA_HOME%\bin\javac.exe" goto noJavaHome
if not "%JRE_HOME%" == "" goto okJavaHome
set "JRE_HOME=%JAVA_HOME%\jre"
goto okJavaHome
:noJavaHome
echo The JAVA_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
echo NB: JAVA_HOME should point to a JDK not a JRE
goto end
:okJavaHome

rem Set the server jvm from JAVA_HOME
set "JVM=%JRE_HOME%\bin\server\jvm.dll"
if exist "%JVM%" goto foundJvm
rem Set the client jvm from JAVA_HOME
set "JVM=%JRE_HOME%\bin\client\jvm.dll"
if exist "%JVM%" goto foundJvm
set JVM=auto
:foundJvm
echo Using JVM:              "%JVM%"

echo Installing '%SERVICE_NAME%' service
"%EXECUTABLE%" //IS//%SERVICE_NAME% ^
    --Description "Red5 Media Server" ^
    --DisplayName "Red5 Media Server" ^
    --Install "%EXECUTABLE%" ^
    --LogPath "%RED5_HOME%\log" ^
    --StdOutput "%RED5_HOME%\log\red5-service.log" ^
    --StdError "%RED5_HOME%\log\red5-error.log" ^
    --Classpath "%CLASSPATH%" ^
    --Jvm "%JVM%" ^
    --StartMode jvm ^
    --StartPath "%WORKING_PATH%" ^
    --StartClass org.red5.daemon.EngineLauncher ^
    --StartMethod windowsService ^
    --StartParams start ^
    --StopMode jvm ^
    --StopPath "%WORKING_PATH%" ^
    --StopClass org.red5.daemon.EngineLauncher ^
    --StopMethod windowsService ^
    --StopParams 9999 ^
    --JvmOptions "-Xverify:none;-XX:+TieredCompilation;-XX:+UseBiasedLocking;-XX:+UseStringCache;-XX:+UseParNewGC;-XX:InitialCodeCacheSize=8m;-XX:ReservedCodeCacheSize=32m;-Dorg.terracotta.quartz.skipUpdateCheck=true;-Dlogback.ContextSelector=org.red5.logging.LoggingContextSelector;-Dcatalina.home=%RED5_HOME%;-Dcatalina.useNaming=true;-Djava.security.debug=failure;" ^
    --JvmMs 256 ^
    --JvmMx 768
if not errorlevel 1 goto installed
echo Failed installing '%SERVICE_NAME%' service
goto end
:installed
echo The service '%SERVICE_NAME%' has been installed.

:end
ENDLOCAL
