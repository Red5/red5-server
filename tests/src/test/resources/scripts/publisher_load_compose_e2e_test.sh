#!/usr/bin/env bash
# Publisher load end-to-end test using docker compose.
#
# Builds a Red5 image from the current checkout (or RED5_TARBALL), starts it with RTMP mapped to the host,
# then runs PublisherConnectLoadTest (N concurrent RTMP publishers using the Red5 client) against it.
# The JUnit result plus a scan of the Red5 log for handshake, encoding and rejection errors is the test result.
#
# Usage:
#   tests/src/test/resources/scripts/publisher_load_compose_e2e_test.sh
#   mvn -pl tests -Ppublisher-load-e2e verify
#
# Environment:
#   RED5_TARBALL      path to a red5-server-*.tar.gz (default: newest under server/target, built if absent)
#   RED5_RTMP_PORT    host port mapped to the container's 1935 (default 19350, avoids a local Red5 on 1935)
#   PUBLISHERS        concurrent publishers (default 10)
#   MESSAGES          messages per publisher (default 200)
#   LOAD_TIMEOUT      seconds to wait for all publishers (default 120)
#   KEEP_UP=1         leave the compose stack running after the test
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
SRC_DIR="${ROOT_DIR}/tests/src/test/resources/docker/publisher-load-e2e"
WORK_DIR="${ROOT_DIR}/tests/target/publisher-load-e2e"
BUILD_DIR="${WORK_DIR}/build"
OUT_DIR="${WORK_DIR}/$(date +%Y%m%d_%H%M%S)"
PROJECT="red5-publisher-load-e2e"
export RED5_RTMP_PORT="${RED5_RTMP_PORT:-19350}"
PUBLISHERS="${PUBLISHERS:-10}"
MESSAGES="${MESSAGES:-200}"
LOAD_TIMEOUT="${LOAD_TIMEOUT:-120}"

require_bin() {
    command -v "$1" >/dev/null 2>&1 || { echo "Missing required binary: $1"; exit 1; }
}
require_bin docker
require_bin mvn
docker compose version >/dev/null 2>&1 || { echo "docker compose v2 is required"; exit 1; }

mkdir -p "${OUT_DIR}"
echo "Output directory: ${OUT_DIR}"

TARBALL="${RED5_TARBALL:-}"
if [[ -z "${TARBALL}" ]]; then
    TARBALL="$(ls -1t "${ROOT_DIR}"/server/target/red5-server-*.tar.gz 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "${TARBALL}" || ! -f "${TARBALL}" ]]; then
    echo "No Red5 tarball found, assembling from the current checkout..."
    (cd "${ROOT_DIR}" && mvn -q -Dmaven.test.skip=true package -P assemble)
    TARBALL="$(ls -1t "${ROOT_DIR}"/server/target/red5-server-*.tar.gz | head -n 1)"
fi
echo "Using tarball: ${TARBALL}"

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/red5"
tar -xzf "${TARBALL}" -C "${BUILD_DIR}/red5" --strip-components=1
cp "${SRC_DIR}"/docker-compose.yml "${SRC_DIR}"/Dockerfile.red5 "${BUILD_DIR}/"

compose() {
    docker compose -p "${PROJECT}" -f "${BUILD_DIR}/docker-compose.yml" "$@"
}

collect() {
    compose logs --no-color red5 >"${OUT_DIR}/red5.log" 2>&1 || true
    cp "${ROOT_DIR}"/tests/target/surefire-reports/*PublisherConnectLoadTest* "${OUT_DIR}/" 2>/dev/null || true
}

cleanup() {
    collect
    if [[ "${KEEP_UP:-0}" == "1" ]]; then
        echo "KEEP_UP=1: leaving compose project ${PROJECT} running"
    else
        compose down -v --remove-orphans >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

compose down -v --remove-orphans >/dev/null 2>&1 || true

echo "Building the Red5 image and starting it..."
compose up --build -d --wait 2>&1 | tee "${OUT_DIR}/compose-up.log"

echo "Running PublisherConnectLoadTest with ${PUBLISHERS} publishers x ${MESSAGES} messages against 127.0.0.1:${RED5_RTMP_PORT}"
set +e
(cd "${ROOT_DIR}" && mvn -pl tests -Pintegration -DskipTests=false \
    -Dtest=PublisherConnectLoadTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dred5.host=127.0.0.1 -Dred5.port="${RED5_RTMP_PORT}" \
    -Dred5.load.publishers="${PUBLISHERS}" -Dred5.load.messages="${MESSAGES}" -Dred5.load.timeout="${LOAD_TIMEOUT}" \
    test) 2>&1 | tee "${OUT_DIR}/maven-test.log" | grep -E "Tests run:|FAIL|Publisher failures|Result:|BUILD"
STATUS=${PIPESTATUS[0]}
set -e
collect

REPORT="${ROOT_DIR}/tests/target/surefire-reports/org.red5.client.PublisherConnectLoadTest.txt"
if [[ "${STATUS}" == 0 ]] && ! grep -q "^Tests run: [1-9]" "${REPORT}" 2>/dev/null; then
    echo "PublisherConnectLoadTest was not executed (no surefire report with tests run); check the integration profile"
    STATUS=1
fi
if [[ "${STATUS}" != 0 ]]; then
    echo "---- red5 log tail ----"
    tail -n 60 "${OUT_DIR}/red5.log" || true
    echo "Publisher load e2e FAILED (maven exit ${STATUS}); logs in ${OUT_DIR}"
    exit "${STATUS}"
fi

# server-side assertions
if grep -n -i "ConcurrentModificationException\|RTMPProtocolEncoder - Error encoding\|OutOfMemoryError" "${OUT_DIR}/red5.log" >"${OUT_DIR}/server-findings.log"; then
    echo "Server errors found in the Red5 log:"
    cat "${OUT_DIR}/server-findings.log"
    exit 1
fi
if grep -n -i "handshake.*\(fail\|error\|exception\)\|NetConnection\.Connect\.Rejected" "${OUT_DIR}/red5.log" >"${OUT_DIR}/rejections.log"; then
    echo "Handshake failures or connection rejections found in the Red5 log:"
    cat "${OUT_DIR}/rejections.log"
    exit 1
fi

echo "PASS: publisher load e2e succeeded with ${PUBLISHERS} publishers; logs in ${OUT_DIR}"
