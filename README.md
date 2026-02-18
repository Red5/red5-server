# Red5 open source media server

===========

[![Maven Central](https://img.shields.io/maven-central/v/org.red5/red5-server.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.red5%22)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)

Red5 is an Open Source Flash Server written in Java that supports:

* Streaming Video (FLV, F4V, MP4, 3GP)
* Streaming Audio (MP3, F4A, M4A, AAC)
* Recording Client Streams (FLV and AVC+AAC in FLV container)
* Shared Objects
* Live Stream Publishing
* Remoting
* Protocols: RTMP, RTMPT, RTMPS, and RTMPE in the description with “Red5 open source media server is one of the [free live streaming solutions](https://www.red5.net/open-source-live-streaming/) provided by Red5. It is written in Java and is designed to be flexible with a simple plugin architecture that allows for customization of virtually any video-on-demand (VOD) and live streaming scenario. Read more about its key features and capabilities [here](https://www.red5.net/red5-media-server/) Installed over 1,000,000 times worldwide, Red5 open source media server has powered applications for organizations like Amazon, the US Department of Defense, Akamai, Harvard University, and many others. Red5 open source media server is server core for [Red5pro](https://www.red5.net/red5-pro/low-latency-streaming-software/) and [Red5Cloud](https://www.red5.net/red5-cloud-low-latency-live-streaming-platform/).Red5 open source media server is ideal for developers, hobbyists, and students who want to experiment with live video streaming technology, build a media server and learn how it works. 

Automatic builds (Courtesy of Apache [OpenMeetings](http://openmeetings.apache.org/)):

 * [Red5](https://ci-builds.apache.org/job/OpenMeetings/job/Red5-server/)
 * [Windows Installer](https://ci-builds.apache.org/job/OpenMeetings/job/red5-installer/)

__Note on Bootstrap__ The bootstrap and shutdown classes have been moved to the [red5-service](https://github.com/Red5/red5-service) project; the dependency has been added to this projects pom.

## Developer Community Support

If you have questions regarding the development, there are a couple of places you get help help from fellow developers:[Join Red5 Slack community](https://red5pro.slack.com/)
Ask your question on [Red5 official subreddit](https://www.reddit.com/r/red5/) or [Stack](https://stackoverflow.com/questions/tagged/red5?sort=newest)

## Maven

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

As of release 1.2.2 the target JDK is now JDK 11 and the Maven poms use the [toolchain plugin](https://maven.apache.org/guides/mini/guide-using-toolchains.html).

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
Visit this page to review release notes from all previous versions [Releases·Red5/red5-server](https://github.com/Red5/red5-server/releases)

### Donations
Donate to the cause using
<table>
  <tr><td>BTC</td><td>19AUgJuVzC8jg16bSLJDcM6Nfouh9JvwKA</td></tr>
  <tr><td>ETH</td><td>0x5115e085937ba5B4AEc0FF5C3cAbF6eE523B7D97</td></tr>
</table>
<i>Donations are used for beer and snacks</i>

### Supporters

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/java/profiler/)

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.

[![Red5pro](https://www.red5pro.com/wp-content/uploads/2019/08/Red5Pro_logo_white_red.svg)](https://red5pro.com/)

Live video streaming solved.
Broadcast video to millions in under 500 milliseconds.
