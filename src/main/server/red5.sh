#!/bin/bash

if [ -z "$RED5_HOME" ]; then 
  export RED5_HOME=`pwd`; 
fi

# Java 11
export JAVA_HOME=/usr/lib/jvm/java-11-amazon-corretto

P=":" # The default classpath separator
OS=`uname`
case "$OS" in
  CYGWIN*|MINGW*) # Windows Cygwin or Windows MinGW
      P=";" # Since these are actually Windows, let Java know
      # Native path
      NATIVE="-Djava.library.path=$RED5_HOME/lib/amd64-Windows-msvc"
  ;;
  Linux*)
      LD_LIBRARY_PATH=$RED5_HOME/lib/amd64-Linux-gpp
      export LD_LIBRARY_PATH
      # Native path
      NATIVE="-Djava.library.path=$LD_LIBRARY_PATH"
  ;;
  Darwin*)
      DYLD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:$RED5_HOME/lib/x86_64-MacOSX-gpp
      export DYLD_LIBRARY_PATH
      # Native path
      NATIVE="-Djava.library.path=$DYLD_LIBRARY_PATH"
  ;;
  SunOS*)
      if [ -z "$JAVA_HOME" ]; then 
          export JAVA_HOME=/opt/local/java/sun6; 
      fi
  ;;
  *)
  # Do nothing
  ;;
esac

echo "Running on " $OS

# JAVA options
# ZGC collector https://wiki.openjdk.java.net/display/zgc
# You can set JVM additional options here if you want
if [ -z "$JVM_OPTS" ]; then 
    JVM_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xms256m -Xmx1g -Xverify:none -XX:+UseBiasedLocking -XX:InitialCodeCacheSize=8m -XX:MaxGCPauseMillis=500 -XX:ReservedCodeCacheSize=32m"
fi
# Set up security options
SECURITY_OPTS="-Djava.security.debug=failure"
# Set up tomcat options
TOMCAT_OPTS="-Dcatalina.home=$RED5_HOME -Dcatalina.useNaming=true"

export JAVA_OPTS="$SECURITY_OPTS $JAVA_OPTS $JVM_OPTS $TOMCAT_OPTS $NATIVE"

if [ -z "$RED5_MAINCLASS" ]; then
  export RED5_MAINCLASS=org.red5.server.Bootstrap
fi

if [ -z "$RED5_OPTS" ]; then
  export RED5_OPTS=9999
fi

JAVA="${JAVA_HOME}/bin/java"

export RED5_CLASSPATH="${RED5_HOME}/red5-service.jar${P}${RED5_HOME}/conf${P}${CLASSPATH}"

# start Red5
echo "Starting Red5"
exec "$JAVA" -Dred5.root="${RED5_HOME}" $JAVA_OPTS -cp "${RED5_CLASSPATH}" "$RED5_MAINCLASS" $RED5_OPTS
