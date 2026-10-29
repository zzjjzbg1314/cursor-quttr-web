#!/usr/bin/env bash
# 仅在目标服务器执行；配置文件必须由root维护，不接受证书密码命令行参数。
set -Eeuo pipefail
umask 077

mode=${1:-}
domain=${2:-}
if [[ "$mode" != --prepare && "$mode" != --deploy ]] ||
   [[ ! "$domain" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]] || [[ "$domain" == *..* ]]; then
    echo "Usage: $0 --prepare|--deploy domain" >&2
    exit 2
fi
[[ $EUID -eq 0 ]] || { echo "Run as root" >&2; exit 1; }
config="/etc/quittr/tls/$domain.conf"
[[ -f "$config" && ! -L "$config" ]] || { echo "Missing regular config: $config" >&2; exit 1; }
[[ $(stat -c '%u:%a' "$config") == 0:600 ]] || { echo "Config must be root:600" >&2; exit 1; }
source "$config"
: "${LINEAGE:?}" "${KEYSTORE:?}" "${PASSWORD_FILE:?}" "${KEY_ALIAS:?}" "${SERVICE:?}"
VERIFY_PORT=${VERIFY_PORT:-443}
[[ "$VERIFY_PORT" =~ ^[0-9]+$ ]] && (( VERIFY_PORT > 0 && VERIFY_PORT <= 65535 )) || exit 2
[[ "$KEYSTORE" == /etc/quittr/tls/*.p12 ]] || exit 2
[[ "$SERVICE" =~ ^[a-zA-Z0-9_.@-]+\.service$ ]] || exit 2
[[ -z ${RENEWED_LINEAGE:-} || "$RENEWED_LINEAGE" == "$LINEAGE" ]] || exit 0
[[ $(stat -c '%u:%a' "$PASSWORD_FILE") == 0:600 ]] || { echo "Password file must be root:600" >&2; exit 1; }
exec 9>"/run/lock/quittr-tls-$domain.lock"
flock -n 9 || { echo "Deployment already running" >&2; exit 1; }

# 所有转换与验证均在替换线上文件之前完成。
openssl x509 -in "$LINEAGE/cert.pem" -noout -checkend 86400
openssl x509 -in "$LINEAGE/cert.pem" -noout -checkhost "$domain"
temp=$(mktemp "${KEYSTORE}.new.XXXXXX")
trap 'rm -f "$temp"' EXIT
openssl pkcs12 -export -in "$LINEAGE/cert.pem" \
    -inkey "$LINEAGE/privkey.pem" -certfile "$LINEAGE/chain.pem" \
    -name "$KEY_ALIAS" -out "$temp" -passout "file:$PASSWORD_FILE"
keytool -list -keystore "$temp" -storetype PKCS12 \
    -storepass:file "$PASSWORD_FILE" -alias "$KEY_ALIAS" >/dev/null
chmod 600 "$temp"

if [[ "$mode" == --prepare ]]; then
    [[ ! -e "$KEYSTORE" ]] || { echo "Prepare refuses to overwrite an existing keystore" >&2; exit 1; }
    mv "$temp" "$KEYSTORE"
    echo "Prepared $KEYSTORE; running Java was NOT changed or restarted"
    exit 0
fi

# 自动部署只适用于已人工验收过的systemd服务，禁止首次部署时误启动第二个Java进程。
[[ -e "$KEYSTORE" ]] || { echo "Run --prepare and complete initial migration first" >&2; exit 1; }
systemctl is-active --quiet "$SERVICE" || { echo "Service is not active; refusing deployment" >&2; exit 1; }
cp -p "$KEYSTORE" "${KEYSTORE}.previous"
mv "$temp" "$KEYSTORE"
expected=$(openssl x509 -in "$LINEAGE/cert.pem" -noout -fingerprint -sha256)
healthy=false
if systemctl restart "$SERVICE"; then
    # 对本机443做域名和信任链校验，再确认实际使用的新证书指纹。
    for attempt in {1..30}; do
        actual=$({ timeout 5 openssl s_client -connect "127.0.0.1:$VERIFY_PORT" \
            -servername "$domain" -verify_hostname "$domain" -verify_return_error </dev/null |
            openssl x509 -noout -fingerprint -sha256; } 2>/dev/null) || actual=''
        if [[ "$actual" == "$expected" ]] && systemctl is-active --quiet "$SERVICE"; then
            healthy=true
            break
        fi
        sleep 2
    done
fi
if [[ "$healthy" != true ]]; then
    cp -p "${KEYSTORE}.previous" "$KEYSTORE"
    systemctl restart "$SERVICE" || true
    logger -p daemon.err -t quittr-tls "Certificate deployment failed for $domain; rollback attempted"
    echo "FAILED: old keystore restored; verify service immediately" >&2
    exit 1
fi
logger -t quittr-tls "Certificate deployed for $domain"
echo "Deployment verified: $domain"
