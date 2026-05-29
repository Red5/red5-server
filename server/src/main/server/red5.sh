#!/bin/bash

if [ -z "$RED5_HOME" ]; then 
  export RED5_HOME=`pwd`; 
fi

# Java 11
# Set JavaHome if it exists
if [ -f "${JAVA_HOME}/bin/java" ]; then
   JAVA_HOME=${JAVA_HOME}
else
   echo "Please set JAVA_HOME in your current path. Preferably use JDK 21 or newer."
fi

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
# You can set JVM additional options here if you want; defining JVM_OPTS in the environment
# overrides everything below.
#
# Container/CPU-limited note: thread pools (RTMP io threads, scheduler, GC workers, virtual-thread
# carriers) are sized from Runtime.availableProcessors(). The JVM only derives a reduced core count
# from a hard CPU *limit* (cgroup quota / cpuset); CPU *requests*/shares alone leave it reading the
# host core count and oversizing every pool. On a CPU-capped deployment either set a hard limit or
# pin the count explicitly, e.g. add: -XX:ActiveProcessorCount=2
#
# CFS throttling note: a hard CPU *quota* (docker --cpus / k8s fractional limit) freezes ALL threads
# when the per-period quota is exhausted, which shows up as streaming jitter. Pinning to whole cores
# instead (docker --cpuset-cpus / k8s static CPU manager with integer limits) removes quota
# throttling entirely. Measured here: 15 publishers + 30 subscribers on ~2 cores produced ~300ms of
# throttled (frozen) time per 30s under --cpus=2, and 0ms under --cpuset-cpus=0,1.
#
# GC selection (resource-aware). Measured under the same 2-core load (1g heap), CFS throttled time
# per 30s and worst stop-the-world pause:
#   ZGC (old default) 617ms / 0ms STW   G1 657ms / 9.8ms   Parallel 266ms / 6.8ms   Serial 156ms / 45ms
# On few cores the always-on concurrent collectors (ZGC, G1) steal from the app and throttle most;
# ParallelGC gives the best balance (low throttling, ~7ms max pause). On larger multi-core hosts with
# bigger heaps G1 is the better general-purpose choice (Parallel/Serial full-GC pauses scale with the
# heap). So pick by detected core count; nproc honors a hard cpu limit / cpuset (but not bare shares).
if [ -z "$JVM_OPTS" ]; then
    CORES=$(nproc 2>/dev/null || echo 4)
    if [ "$CORES" -le 4 ]; then
        # CPU-limited / small container: low-overhead throughput collector, least CFS throttling
        GC_OPTS="-XX:+UseParallelGC"
    else
        # larger multi-core host: general-purpose low-pause collector
        GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    fi
    JVM_OPTS="$GC_OPTS -Xms256m -Xmx1g -XX:InitialCodeCacheSize=8m -XX:ReservedCodeCacheSize=32m"
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
