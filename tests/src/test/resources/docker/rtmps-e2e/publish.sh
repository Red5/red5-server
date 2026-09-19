#!/usr/bin/env bash
# Publish a synthetic H.264/AAC stream to Red5 over RTMPS for PUBLISH_DURATION seconds.
set -euo pipefail

: "${RTMPS_URL:?RTMPS_URL is required}"
PUBLISH_DURATION="${PUBLISH_DURATION:-30}"

echo "publisher: publishing to ${RTMPS_URL} for ${PUBLISH_DURATION}s"
exec ffmpeg -hide_banner -loglevel info -re \
    -f lavfi -i "testsrc2=size=640x360:rate=30" \
    -f lavfi -i "sine=frequency=440:sample_rate=48000" \
    -t "${PUBLISH_DURATION}" \
    -c:v libx264 -preset veryfast -tune zerolatency -g 60 -keyint_min 60 -pix_fmt yuv420p \
    -c:a aac -ar 48000 -b:a 96k \
    -f flv "${RTMPS_URL}"
