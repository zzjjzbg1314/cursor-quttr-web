#!/usr/bin/env bash
# 安装到Certbot的renewal-hooks/deploy目录，仅处理显式配置的节点。
set -Eeuo pipefail
[[ -n ${RENEWED_LINEAGE:-} ]] || { echo "Missing RENEWED_LINEAGE" >&2; exit 1; }
domain=${RENEWED_LINEAGE##*/}
[[ "$domain" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || exit 2
[[ -f "/etc/quittr/tls/$domain.conf" ]] || exit 0
exec /usr/local/sbin/quittr-deploy-certificate --deploy "$domain"
