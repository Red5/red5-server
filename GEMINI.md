# Red5 Server

## Project Overview

This is the Red5 Server, an open-source, multi-threaded, and highly-performant media server written in Java. It supports streaming video and audio, recording client streams, shared objects, and live stream publishing. The project is built with Maven and consists of several modules, including `io`, `common`, `server`, `client`, `service`, and `tests`.

## Building and Running

To build the project, run the following command in the root directory:

```sh
mvn -Dmaven.test.skip=true install
```

This will build the project and skip the unit tests. The resulting JAR files will be located in the `target` directory of each module.

To create a packaged assembly (tarball/zip), run:

```sh
mvn -Dmaven.test.skip=true clean package -P assemble
```

## Development Conventions

The project uses the [red5-eclipse-format.xml](red5-eclipse-format.xml) for code formatting. The project also uses the [formatter-maven-plugin](https://code.revelc.net/formatter-maven-plugin/) to enforce code style.

The project uses JUnit for testing. The tests are located in the `src/test/java` directory of each module.
