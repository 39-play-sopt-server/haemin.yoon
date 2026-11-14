#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: bootstrap-oci.sh <domain> <certbot-email> <deploy-user>" >&2
  exit 2
fi

domain="$1"
certbot_email="$2"
deploy_user="$3"

[[ "${domain}" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "invalid domain" >&2; exit 2; }
[[ "${certbot_email}" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] \
  || { echo "invalid certbot email" >&2; exit 2; }
[[ "${deploy_user}" =~ ^[a-z_][a-z0-9_-]*$ ]] || { echo "invalid deploy user" >&2; exit 2; }

if command -v cloud-init >/dev/null 2>&1; then
  cloud-init status --wait
fi

# The Always Free VM.Standard.E2.1.Micro shape has only 1 GB of RAM.
# Create swap once so package installation, Docker, PostgreSQL, and Spring Boot
# can run without requiring a manual server setup step.
if ! swapon --show=NAME --noheadings | grep -qx '/swapfile'; then
  if [[ ! -f /swapfile ]]; then
    fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=progress
    chmod 0600 /swapfile
    mkswap /swapfile
  fi
  swapon /swapfile
fi

if ! grep -qE '^/swapfile[[:space:]]' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

export DEBIAN_FRONTEND=noninteractive
if ! command -v docker >/dev/null 2>&1 \
  || ! docker compose version >/dev/null 2>&1 \
  || ! command -v certbot >/dev/null 2>&1 \
  || ! command -v fail2ban-client >/dev/null 2>&1; then
  apt-get update
  apt-get install -y ca-certificates curl certbot fail2ban gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  . /etc/os-release
  architecture="$(dpkg --print-architecture)"
  echo "deb [arch=${architecture} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

# OCI Security Lists and the instance firewall are separate layers. The Ubuntu
# platform image rejects unlisted inbound traffic, so HTTP/HTTPS must be allowed
# here before Certbot starts its standalone HTTP-01 challenge server.
cat > /usr/local/sbin/assignment-firewall <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

for port in 80 443; do
  if iptables -C INPUT -p tcp -m conntrack --ctstate NEW --dport "${port}" -j ACCEPT \
    >/dev/null 2>&1; then
    continue
  fi

  insert_before="$(
    iptables -L INPUT --line-numbers -n \
      | awk '($2 == "REJECT" || $2 == "DROP") && !found { print $1; found = 1 }'
  )"

  if [[ -n "${insert_before}" ]]; then
    iptables -I INPUT "${insert_before}" -p tcp -m conntrack --ctstate NEW \
      --dport "${port}" -j ACCEPT
  else
    iptables -A INPUT -p tcp -m conntrack --ctstate NEW --dport "${port}" -j ACCEPT
  fi
done
EOF
chmod 0755 /usr/local/sbin/assignment-firewall

cat > /etc/systemd/system/assignment-firewall.service <<'EOF'
[Unit]
Description=Allow assignment HTTP and HTTPS traffic
Wants=network-online.target
After=network-online.target
Before=docker.service certbot.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/assignment-firewall
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable assignment-firewall.service
systemctl restart assignment-firewall.service

install -d -m 0755 /etc/ssh/sshd_config.d
cat > /etc/ssh/sshd_config.d/99-assignment-hardening.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
sshd -t
systemctl reload ssh

systemctl enable --now docker fail2ban
usermod -aG docker "${deploy_user}"

deploy_home="$(getent passwd "${deploy_user}" | cut -d: -f6)"
if [[ -z "${deploy_home}" || ! -d "${deploy_home}" ]]; then
  echo "home directory not found for ${deploy_user}" >&2
  exit 1
fi

install -d -m 0750 -o "${deploy_user}" -g "${deploy_user}" \
  "${deploy_home}/assignment-play" \
  "${deploy_home}/assignment-play/incoming" \
  "${deploy_home}/assignment-play/releases" \
  "${deploy_home}/assignment-play/shared"

if [[ ! -f "/etc/letsencrypt/live/${domain}/fullchain.pem" ]]; then
  current_release="${deploy_home}/assignment-play/current"
  if [[ -f "${current_release}/compose.yml" ]]; then
    docker compose -f "${current_release}/compose.yml" \
      --env-file "${current_release}/.env" stop nginx
  fi

  if ! certbot certonly --standalone --non-interactive \
    --preferred-challenges http \
    --domain "${domain}" \
    --email "${certbot_email}" \
    --agree-tos \
    --no-eff-email; then
    if [[ -f "${current_release}/compose.yml" ]]; then
      docker compose -f "${current_release}/compose.yml" \
        --env-file "${current_release}/.env" up -d nginx
    fi
    exit 1
  fi
fi

install -d -m 0755 \
  /etc/letsencrypt/renewal-hooks/pre \
  /etc/letsencrypt/renewal-hooks/post

cat > /etc/letsencrypt/renewal-hooks/pre/10-stop-assignment-nginx <<EOF
#!/usr/bin/env bash
set -eu
current="${deploy_home}/assignment-play/current"
if [[ -f "\${current}/compose.yml" ]]; then
  docker compose -f "\${current}/compose.yml" --env-file "\${current}/.env" stop nginx
fi
EOF

cat > /etc/letsencrypt/renewal-hooks/post/10-start-assignment-nginx <<EOF
#!/usr/bin/env bash
set -eu
current="${deploy_home}/assignment-play/current"
if [[ -f "\${current}/compose.yml" ]]; then
  docker compose -f "\${current}/compose.yml" --env-file "\${current}/.env" up -d nginx
fi
EOF

chmod 0750 \
  /etc/letsencrypt/renewal-hooks/pre/10-stop-assignment-nginx \
  /etc/letsencrypt/renewal-hooks/post/10-start-assignment-nginx

if systemctl list-unit-files certbot.timer --no-legend | grep -q '^certbot.timer'; then
  systemctl enable --now certbot.timer
fi

echo "OCI host bootstrap completed for ${domain}"
