#!/usr/bin/env bash
# Verify the RTMPS endpoint: TLS handshake serves our certificate, ffprobe sees video+audio
# over rtmps, and a short rtmps subscription decodes at least MIN_FRAMES video frames.
set -uo pipefail

: "${RTMPS_URL:?RTMPS_URL is required}"
RTMPS_HOST="${RTMPS_HOST:-red5}"
RTMPS_PORT="${RTMPS_PORT:-8443}"
MIN_FRAMES="${MIN_FRAMES:-30}"
OUT=/tmp/probe
mkdir -p "${OUT}"

fail() {
    echo "FAIL: $*"
    exit 1
}

echo "prober: checking TLS on ${RTMPS_HOST}:${RTMPS_PORT}"
if ! echo | openssl s_client -connect "${RTMPS_HOST}:${RTMPS_PORT}" -tls1_2 >"${OUT}/s_client.log" 2>&1; then
    cat "${OUT}/s_client.log"
    fail "TLS 1.2 handshake with ${RTMPS_HOST}:${RTMPS_PORT} failed"
fi
grep -q "CN *= *red5" "${OUT}/s_client.log" || { cat "${OUT}/s_client.log"; fail "server certificate is not the e2e certificate (CN=red5)"; }
echo "prober: TLS handshake ok, certificate CN=red5"

# give the publisher time to send its sequence headers
sleep 5

echo "prober: probing ${RTMPS_URL}"
probed=0
for attempt in $(seq 1 6); do
    if timeout 25 ffprobe -v error -rw_timeout 5000000 -analyzeduration 10000000 -probesize 5000000 \
            -show_streams -show_format -of json "${RTMPS_URL}" >"${OUT}/ffprobe.json" 2>"${OUT}/ffprobe.log"; then
        if grep -q '"codec_type": "video"' "${OUT}/ffprobe.json" && grep -q '"codec_type": "audio"' "${OUT}/ffprobe.json"; then
            probed=1
            break
        fi
    fi
    echo "prober: attempt ${attempt} did not yield video+audio, retrying"
    sleep 3
done
if [[ "${probed}" != 1 ]]; then
    cat "${OUT}/ffprobe.log" || true
    fail "ffprobe over rtmps did not report both video and audio streams"
fi
grep -o '"codec_name": "[a-z0-9]*"' "${OUT}/ffprobe.json" | sort -u
grep -q '"codec_name": "h264"' "${OUT}/ffprobe.json" || fail "expected h264 video"
grep -q '"codec_name": "aac"' "${OUT}/ffprobe.json" || fail "expected aac audio"

echo "prober: subscribing to ${RTMPS_URL} for 5s"
if ! timeout 40 ffmpeg -hide_banner -nostats -loglevel info -rw_timeout 5000000 -rtmp_live live \
        -i "${RTMPS_URL}" -t 5 -f null - >"${OUT}/subscribe.log" 2>&1; then
    cat "${OUT}/subscribe.log"
    fail "ffmpeg subscription over rtmps failed"
fi
frames="$(grep -o 'frame= *[0-9]*' "${OUT}/subscribe.log" | tail -n 1 | tr -dc '0-9')"
frames="${frames:-0}"
echo "prober: decoded ${frames} video frames"
if (( frames < MIN_FRAMES )); then
    cat "${OUT}/subscribe.log"
    fail "decoded ${frames} frames, expected at least ${MIN_FRAMES}"
fi

echo "PASS: RTMPS publish and subscribe verified (${frames} frames)"
exit 0
