#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
IMAGE_TAG="${1:-red5-local:dev}"
BUILD_DIR="${ROOT_DIR}/tests/target/local-red5-image-build"
DOCKERFILE_PATH="${ROOT_DIR}/tests/src/test/resources/docker/Dockerfile.red5-it"

echo "Building Red5 server from current checkout..."
cd "${ROOT_DIR}"
mvn -Dmaven.test.skip=true clean package -P assemble

TARBALL="$(ls -1t "${ROOT_DIR}"/server/target/red5-server-*.tar.gz | head -n 1)"
if [[ -z "${TARBALL}" ]]; then
    echo "Could not find assembled Red5 tarball under server/target"
    exit 1
fi

rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}/red5"

echo "Using tarball: ${TARBALL}"
tar -xzf "${TARBALL}" -C "${BUILD_DIR}/red5" --strip-components=1

cp "${DOCKERFILE_PATH}" "${BUILD_DIR}/Dockerfile"

echo "Building Docker image: ${IMAGE_TAG}"
docker build -t "${IMAGE_TAG}" "${BUILD_DIR}"

echo "Done. Image available as ${IMAGE_TAG}"
echo "Run integration test with:"
echo "  mvn -pl tests -Pdocker-integration -Dred5.it.image=${IMAGE_TAG} -Dred5.it.autoPull=off verify"
