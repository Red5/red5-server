# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Maven build. Key modules live at the repo root:
- `common`, `io`, `server`, `service`, `servlet`, `client`: core Java modules (each has `src/main/java`).
- `tests`: shared test module (`src/test/java`).
- `extras`, `apidocs`, `ci`: supporting assets and build tooling.
- Root docs like `README.md`, `RTMPS.md`, and `SSE-README.md` capture protocol-specific notes.

## Build, Test, and Development Commands
Use JDK 21 with Maven (no toolchains).
- `mvn -Dmaven.test.skip=true install` builds all modules and jars (fast, skips tests).
- `mvn -Dmaven.test.skip=true clean package -P assemble` produces distribution archives.
- `mvn -Dmilestone.version=1.0.7-M1 clean package -Pmilestone` builds a milestone tarball.
- `mvn -DskipTests=false test` runs tests for all modules (tests are skipped by default).

## Coding Style & Naming Conventions
- Java formatting follows `red5-eclipse-format.xml` via `formatter-maven-plugin`.
- Indentation: 4 spaces; line endings: LF.
- Packages use `org.red5.*`. Keep class names in UpperCamelCase and methods/fields in lowerCamelCase.
- Run `git diff --check` before committing to avoid whitespace issues.

## Testing Guidelines
- Tests are JUnit 4 based (see root `pom.xml`).
- Place unit tests under `src/test/java` and mirror package names.
- Prefer focused, reproducible tests; add coverage for bug fixes.

## Commit & Pull Request Guidelines
- Commit messages in history are short, imperative, and task-focused (e.g., "Fix RTMP timestamp handling", "Update version").
- Create topic branches off `master` and submit PRs from forks.
- PRs should describe the change, reference issues, and include tests where applicable.

## Security & Configuration Notes
- TLS/RTMPS notes live in `RTMPS.md`. Review before changing SSL or transport settings.
- If you touch protocol code, update any related docs or examples.
