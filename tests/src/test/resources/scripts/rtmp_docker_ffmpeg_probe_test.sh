#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
OUT_DIR="${ROOT_DIR}/tests/target/rtmp-docker-it/$(date +%Y%m%d_%H%M%S)"
mkdir -p "${OUT_DIR}"

PUBLISHERS="${PUBLISHERS:-5}"
PUBLISH_DURATION="${PUBLISH_DURATION:-25}"
RTMP_HOST="${RTMP_HOST:-127.0.0.1}"
RTMP_PORT="${RTMP_PORT:-1935}"
RTMP_APP="${RTMP_APP:-live}"
RTMP_BASE_URL="rtmp://${RTMP_HOST}:${RTMP_PORT}/${RTMP_APP}"

echo "Output directory: ${OUT_DIR}"
echo "Using ${PUBLISHERS} publishers against ${RTMP_BASE_URL}"

require_bin() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required binary: $1"
        exit 1
    fi
}

require_bin docker
require_bin ffmpeg
require_bin ffprobe

wait_for_rtmp_port() {
    local retries=30
    local i
    for ((i=1; i<=retries; i++)); do
        if timeout 2 bash -c ":</dev/tcp/${RTMP_HOST}/${RTMP_PORT}" \
            >"${OUT_DIR}/startup-portcheck-${i}.log" 2>&1; then
            return 0
        fi
        sleep 1
    done
    return 1
}

if ! wait_for_rtmp_port; then
    echo "RTMP endpoint did not become reachable at ${RTMP_BASE_URL}"
    exit 1
fi

declare -a PIDS=()
cleanup() {
    local pid
    for pid in "${PIDS[@]:-}"; do
        if kill -0 "${pid}" >/dev/null 2>&1; then
            kill "${pid}" || true
        fi
    done
}
trap cleanup EXIT

launch_publisher() {
    local idx="$1"
    local stream="stream${idx}"
    local log="${OUT_DIR}/publisher-${stream}.log"
    ffmpeg -hide_banner -loglevel info -re \
        -f lavfi -i "testsrc2=size=640x360:rate=30" \
        -f lavfi -i "sine=frequency=$((500 + (idx * 50))):sample_rate=48000" \
        -c:v libx264 -preset veryfast -g 60 -keyint_min 60 -pix_fmt yuv420p \
        -c:a aac -ar 48000 -b:a 96k \
        -f flv "${RTMP_BASE_URL}/${stream}" \
        >"${log}" 2>&1 &
    PIDS+=("$!")
}

for i in $(seq 1 "${PUBLISHERS}"); do
    launch_publisher "${i}"
done

sleep 8

for pid in "${PIDS[@]}"; do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
        echo "At least one publisher exited early"
        exit 1
    fi
done

probe_stream() {
    local stream="$1"
    local out="${OUT_DIR}/ffprobe-${stream}.json"
    timeout 20 ffprobe -v error -rw_timeout 5000000 \
        -analyzeduration 10000000 -probesize 5000000 \
        -show_streams -show_format -of json \
        "${RTMP_BASE_URL}/${stream}" >"${out}" 2>"${OUT_DIR}/ffprobe-${stream}.log"
    if ! rg -q "\"codec_type\": \"video\"" "${out}"; then
        echo "Missing video stream in ${stream}"
        return 1
    fi
    if ! rg -q "\"codec_type\": \"audio\"" "${out}"; then
        echo "Missing audio stream in ${stream}"
        return 1
    fi
    return 0
}

for i in $(seq 1 "${PUBLISHERS}"); do
    probe_stream "stream${i}"
done

sleep "${PUBLISH_DURATION}"

for pid in "${PIDS[@]}"; do
    if ! kill -0 "${pid}" >/dev/null 2>&1; then
        echo "At least one publisher terminated before duration ${PUBLISH_DURATION}s"
        exit 1
    fi
done

container_id="$(docker ps --filter ancestor=mondain/red5:latest --filter publish=${RTMP_PORT} --format '{{.ID}}' | head -n 1 || true)"
if [[ -z "${container_id}" ]]; then
    echo "Could not identify running Red5 container"
    exit 1
fi

docker logs "${container_id}" >"${OUT_DIR}/red5-container.log" 2>&1 || true

if rg -n -i "handshake.*(fail|error|exception)|exception.*handshake|rtmp.*handshake.*failed" "${OUT_DIR}/red5-container.log" \
    >"${OUT_DIR}/handshake-findings.log"; then
    echo "Handshake issues detected, see ${OUT_DIR}/handshake-findings.log"
    exit 1
fi

if rg -n -i "NetConnection\\.Connect\\.Rejected|connection rejected|client rejected" "${OUT_DIR}/red5-container.log" \
    >"${OUT_DIR}/connection-rejections.log"; then
    echo "Connection rejection(s) detected, see ${OUT_DIR}/connection-rejections.log"
    exit 1
fi

echo "PASS: ${PUBLISHERS} publishers connected, ffprobe subscriptions validated, no handshake/rejection issues found."
