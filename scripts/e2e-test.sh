#!/usr/bin/env bash
set -euo pipefail

readonly repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly container_name="sbt-s3-resolver-e2e"
readonly minio_image="minio/minio@sha256:a1ea29fa28355559ef137d71fc570e508a214ec84ff8083e39bc5428980b015e"
readonly minio_client_image="minio/mc@sha256:a7fe349ef4bd8521fb8497f55c6042871b2ae640607cf99d9bede5e9bdf11727"

cleanup() {
  docker rm --force "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --detach \
  --name "$container_name" \
  --publish 9000:9000 \
  --env MINIO_ROOT_USER=minioadmin \
  --env MINIO_ROOT_PASSWORD=minioadmin \
  "$minio_image" server /data

for _ in {1..60}; do
  if curl --fail --silent http://127.0.0.1:9000/minio/health/live >/dev/null; then
    break
  fi
  sleep 1
done
curl --fail --silent http://127.0.0.1:9000/minio/health/live >/dev/null

docker run --rm \
  --network "container:$container_name" \
  --entrypoint /bin/sh \
  "$minio_client_image" \
  -c 'mc alias set local http://127.0.0.1:9000 minioadmin minioadmin && mc mb local/sbt-s3-resolver-e2e'

export AWS_ACCESS_KEY_ID=minioadmin
export AWS_SECRET_ACCESS_KEY=minioadmin
export S3_SERVICE_ENDPOINT=http://127.0.0.1:9000
export S3_SIGNING_REGION=us-east-1
export S3_PATH_STYLE_ACCESS=true
export S3_FORCE_GLOBAL_BUCKET_ACCESS=false

(cd "$repo_root" && sbt -batch publishLocal)
(cd "$repo_root/e2e/publisher" && sbt -batch publish)
(cd "$repo_root/e2e/consumer" && sbt -batch clean run)
