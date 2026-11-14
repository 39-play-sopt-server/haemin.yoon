#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: remote-deploy.sh <release-id> <app-image>" >&2
  exit 2
fi

release_id="$1"
app_image="$2"
deploy_root="${DEPLOY_ROOT:-${HOME}/assignment-play}"
incoming_dir="${deploy_root}/incoming"
release_dir="${deploy_root}/releases/${release_id}"
shared_env="${deploy_root}/shared/.env"
bundle_archive="${incoming_dir}/deployment-${release_id}.tar.gz"
image_archive="${incoming_dir}/image-${release_id}.tar.gz"
previous_release="$(readlink -f "${deploy_root}/current" 2>/dev/null || true)"

rollback() {
  if [[ -z "${previous_release}" || ! -f "${previous_release}/.app-image" ]]; then
    echo "no previous healthy release is available for automatic rollback" >&2
    return
  fi

  local previous_image
  previous_image="$(<"${previous_release}/.app-image")"
  echo "rolling back to ${previous_release} (${previous_image})" >&2
  cd "${previous_release}"
  APP_IMAGE="${previous_image}" docker compose --env-file .env up -d --no-build --remove-orphans
}

for required_file in "${shared_env}" "${bundle_archive}" "${image_archive}"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "required file does not exist: ${required_file}" >&2
    exit 1
  fi
done

mkdir -p "${release_dir}"
tar -xzf "${bundle_archive}" -C "${release_dir}"
ln -sfn "${shared_env}" "${release_dir}/.env"

gzip -dc "${image_archive}" | docker load

cd "${release_dir}"
if ! APP_IMAGE="${app_image}" docker compose --env-file .env up -d --no-build --remove-orphans; then
  rollback
  exit 1
fi

if ! docker compose --env-file .env exec -T nginx nginx -t; then
  rollback
  exit 1
fi

for attempt in {1..60}; do
  if docker compose --env-file .env exec -T nginx \
    wget -qO- http://app:8080/actuator/health | grep -q '"status":"UP"'; then
    printf '%s\n' "${app_image}" > "${release_dir}/.app-image"
    ln -sfn "${release_dir}" "${deploy_root}/current"
    rm -f "${bundle_archive}" "${image_archive}"
    echo "deployment completed: ${release_id}"
    exit 0
  fi
  sleep 2
done

echo "application health check failed" >&2
docker compose --env-file .env ps >&2
docker compose --env-file .env logs --tail=100 app nginx >&2
rollback
exit 1
