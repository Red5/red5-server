#!/usr/bin/env bash
# RTMPS end-to-end test using docker compose.
#
# Builds a Red5 image from the current checkout (or RED5_TARBALL) with the RTMPS transport
# enabled and a self-signed certificate, then runs an ffmpeg publisher and an ffprobe/ffmpeg
# subscriber against it over rtmps://. The prober container's exit code is the test result.
#
# Usage:
#   tests/src/test/resources/scripts/rtmps_compose_e2e_test.sh
#   mvn -pl tests -Prtmps-e2e verify
#
# Environment:
#   RED5_TARBALL      path to a red5-server-*.tar.gz (default: newest under server/target, built if absent)
#   PUBLISH_DURATION  seconds the publisher streams (default 30)
#   MIN_FRAMES        minimum decoded frames the subscriber must see (default 30)
#   KEEP_UP=1         leave the compose stack running after the test for inspection
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
SRC_DIR="${ROOT_DIR}/tests/src/test/resources/docker/rtmps-e2e"
WORK_DIR="${ROOT_DIR}/tests/target/rtmps-e2e"
BUILD_DIR="${WORK_DIR}/build"
OUT_DIR="${WORK_DIR}/$(date +%Y%m%d_%H%M%S)"
PROJECT="red5-rtmps-e2e"
export PUBLISH_DURATION="${PUBLISH_DURATION:-30}"
export MIN_FRAMES="${MIN_FRAMES:-30}"

require_bin() {
    command -v "$1" >/dev/null 2>&1 || { echo "Missing required binary: $1"; exit 1; }
}
require_bin docker
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
cp "${SRC_DIR}"/docker-compose.yml "${SRC_DIR}"/Dockerfile.red5 "${SRC_DIR}"/Dockerfile.ffmpeg \
   "${SRC_DIR}"/publish.sh "${SRC_DIR}"/probe.sh "${BUILD_DIR}/"

compose() {
    docker compose -p "${PROJECT}" -f "${BUILD_DIR}/docker-compose.yml" "$@"
}

collect() {
    compose logs --no-color red5 >"${OUT_DIR}/red5.log" 2>&1 || true
    compose logs --no-color publisher >"${OUT_DIR}/publisher.log" 2>&1 || true
    compose logs --no-color prober >"${OUT_DIR}/prober.log" 2>&1 || true
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

# make sure a previous aborted run does not linger
compose down -v --remove-orphans >/dev/null 2>&1 || true

echo "Building images and running the RTMPS e2e stack..."
set +e
compose up --build --abort-on-container-exit --exit-code-from prober 2>&1 | tee "${OUT_DIR}/compose-up.log"
STATUS=${PIPESTATUS[0]}
set -e
collect

if [[ "${STATUS}" != 0 ]]; then
    echo "---- red5 log tail ----"
    tail -n 60 "${OUT_DIR}/red5.log" || true
    echo "RTMPS e2e FAILED (prober exit ${STATUS}); logs in ${OUT_DIR}"
    exit "${STATUS}"
fi

# server-side assertions: TLS must not have errored and no connection may have been rejected
if grep -n -i "Exception getting SSL context\|Failed to load keystore\|Failed to load truststore\|SSLHandshakeException" "${OUT_DIR}/red5.log" >"${OUT_DIR}/tls-findings.log"; then
    echo "TLS errors found in the Red5 log:"
    cat "${OUT_DIR}/tls-findings.log"
    exit 1
fi
if grep -n -i "NetConnection\.Connect\.Rejected" "${OUT_DIR}/red5.log" >"${OUT_DIR}/rejections.log"; then
    echo "Connection rejection(s) found in the Red5 log:"
    cat "${OUT_DIR}/rejections.log"
    exit 1
fi

echo "PASS: RTMPS compose e2e succeeded; logs in ${OUT_DIR}"
