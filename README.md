red5-server - Red5 server core
===========

![TravisCI](https://travis-ci.org/Red5/red5-server.svg?branch=master) 
[![Maven Central](https://img.shields.io/maven-central/v/org.red5/red5-server.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.red5%22)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)

Red5 is an Open Source Flash Server written in Java that supports:

 * Streaming Video (FLV, F4V, MP4, 3GP)
 * Streaming Audio (MP3, F4A, M4A, AAC)
 * Recording Client Streams (FLV and AVC+AAC in FLV container)
 * Shared Objects
 * Live Stream Publishing
 * Remoting
 * Protocols: RTMP, RTMPT, RTMPS, and RTMPE

The Red5 users list may be found here: [red5interest](https://groups.google.com/forum/#!forum/red5interest)

Subreddit: [r/red5](http://www.reddit.com/r/red5)

Automatic builds (Courtesy of Apache [OpenMeetings](http://openmeetings.apache.org/)): 
 * [Red5](https://builds.apache.org/view/M-R/view/OpenMeetings/job/Red5-server/)
 * [Windows Installer](https://builds.apache.org/view/M-R/view/OpenMeetings/job/red5-installer/)

# [Releases](https://github.com/Red5/red5-server/releases/latest)
# [Previous releases](https://github.com/Red5/red5-server/blob/master/README.md#previous-releases)

<i>Note on Bootstrap</i>

The bootstrap and shutdown classes have been moved to the [red5-service](https://github.com/Red5/red5-service) project; the dependency has been added to this projects pom.

# StackOverflow
If you want answers from a broader audience, [Stack Overflow](http://stackoverflow.com/tags/red5/info) may be your best bet.

# Maven
Releases are available at [Sonatype - Releases](https://oss.sonatype.org/content/repositories/releases/org/red5/)

Snapshots are available at [Sonatype - Snapshots](https://oss.sonatype.org/content/repositories/snapshots/org/red5/)

Include the red5-parent in your __pom.xml__  in the __dependencyManagement__ section
```xml
<dependencyManagement>
    <dependencies>
      <dependency>
          <groupId>org.red5</groupId>
          <artifactId>red5-parent</artifactId>
          <version>${red5.version}</version>
          <type>pom</type>
      </dependency>
    </dependencies>
</dependencyManagement>  
```
in addition to any other Red5 projects in the __dependencies__ section
```xml
  <dependency>
      <groupId>org.red5</groupId>
      <artifactId>red5-server</artifactId>
      <version>${red5.version}</version>
      <type>jar</type>
  </dependency>
```

## Build from Source

To build the red5 jars, execute the following on the command line:
```sh
mvn -Dmaven.test.skip=true install
```
This will create the jars in the "target" directory of the workspace; this will also skip the unit tests.

To package everything up in an assembly (tarball/zip):
```sh
mvn -Dmaven.test.skip=true clean package -P assemble
```
To build a milestone tarball:
```sh
mvn -Dmilestone.version=1.0.7-M1 clean package -Pmilestone
```

# Eclipse

1. Create the eclipse project files, execute this within red5-server directory.
```sh
mvn eclipse:eclipse
```
2. Import the project into Eclipse.
3. Access the right-click menu and select "Configure" and then "Convert to Maven Project".
4. Now the project will build automatically, if you have the maven plugin installed.

[Screencast](http://screencast.com/t/2sgjMevf9)
  
Features supported via plugin (These are mostly deprecated):
 * [WebSocket (ws and wss)](https://github.com/Red5/red5-websocket)
 * [RTSP (From Axis-type cameras)](https://github.com/Red5/red5-rtsp-restreamer)
 * [HLS](https://github.com/Red5/red5-hls-plugin)
 
# Older Releases
The artifacts for the following releases are no longer available; if your project requires them, you'll have to build them from source. The listings are here only for historical purposes.


 * [Red5 1.1.1 Release](https://github.com/Red5/red5-server/releases/tag/v1.1.1-RELEASE) (29 May 2019)
 * [Red5 1.1.0 Release](https://github.com/Red5/red5-server/releases/tag/v1.1.0-RELEASE) (22 April 2019)
 * [Red5 1.0.10 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.10-RELEASE) (9 February 2019)
 * [Red5 1.0.9 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.9-RELEASE) (11 June 2017)
 * [Red5 1.0.8 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.8-RELEASE) (23 December 2016)
 * [Red5 1.0.7 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.7-RELEASE) (12 May 2016)
 * [Red5 1.0.6 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.6-RELEASE) (8 September 2015)
 * [Red5 1.0.5 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.5-RELEASE) (7 February 2015)
 * [Red5 1.0.4 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.4-RELEASE) (26 December 2014)
 * [Red5 1.0.3 Release](https://github.com/Red5/red5-server/releases/tag/v1.0.3-RELEASE) (5 August 2014)
 * Red5 1.0.2 Release (9 April 2014)
 * Red5 1.0.2 Snapshot (18 April 2013)
 * Red5 1.0.1 Final (14 January 2013)
 * Red5 1.0 Final (03 December 2012)
 * Red5 0.9.1 Final (21 February 2010)
 * Red5 0.9.0 Final (27 January 2010)
 * Red5 0.8.0 Final

### Donations
Donate to the cause using
<table>
  <tr><td>BTC</td><td>19AUgJuVzC8jg16bSLJDcM6Nfouh9JvwKA</td></tr>
  <tr><td>ETH</td><td>0x5115e085937ba5B4AEc0FF5C3cAbF6eE523B7D97</td></tr>
</table>
<i>Donations are used for beer and snacks</i>

