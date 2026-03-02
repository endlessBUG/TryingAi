#!/bin/bash
# ============================================================
# Nginx deploy script
# Port: 8800
# OS: Linux x86_64 (CentOS 7/8 / Ubuntu 18+)
# ============================================================

set -e

# -------------------- config --------------------
NGINX_PORT=8800
WEB_ROOT="/root/ai/trainer/dist"
NGINX_CONF="/etc/nginx/conf.d/app.conf"
NGINX_USER="nginx"

# -------------------- color --------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# -------------------- detect OS --------------------
detect_os() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS_ID="$ID"
        OS_VERSION="$VERSION_ID"
    elif [ -f /etc/centos-release ]; then
        OS_ID="centos"
    else
        error "unknown OS"
    fi
    info "OS: ${OS_ID} ${OS_VERSION:-unknown}"
}

# -------------------- install nginx --------------------
install_nginx() {
    if command -v nginx &> /dev/null; then
        info "Nginx already installed: $(nginx -v 2>&1)"
        return 0
    fi

    info "Installing Nginx..."
    case "$OS_ID" in
        ubuntu|debian)
            apt-get update -y
            apt-get install -y nginx
            ;;
        centos|rhel|rocky|almalinux)
            yum install -y epel-release
            yum install -y nginx
            ;;
        *)
            error "Unsupported OS: $OS_ID"
            ;;
    esac
    info "Nginx installed"
}

# -------------------- setup web root --------------------
setup_web_root() {
    if [ ! -d "$WEB_ROOT" ]; then
        info "Creating web root: $WEB_ROOT"
        mkdir -p "$WEB_ROOT"
    fi

    if [ ! -f "$WEB_ROOT/index.html" ]; then
        info "Creating default index.html"
        cat > "$WEB_ROOT/index.html" <<'HTML'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Nginx OK</title>
    <style>
        body { font-family: system-ui, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background: #f5f5f5; }
        .card { background: #fff; padding: 40px 60px; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); text-align: center; }
        h1 { color: #333; margin-bottom: 8px; }
        p { color: #666; }
    </style>
</head>
<body>
    <div class="card">
        <h1>Nginx OK</h1>
        <p>Port 8800</p>
    </div>
</body>
</html>
HTML
    fi

    chown -R "$NGINX_USER":"$NGINX_USER" "$WEB_ROOT" 2>/dev/null || true
    info "Web root ready: $WEB_ROOT"
}

# -------------------- write nginx config --------------------
configure_nginx() {
    info "Writing nginx config: $NGINX_CONF"

    mkdir -p "$(dirname "$NGINX_CONF")"

    cat > "$NGINX_CONF" <<EOF
server {
    listen       ${NGINX_PORT};
    server_name  _;

    root   ${WEB_ROOT};
    index  index.html index.htm;

    gzip on;
    gzip_min_length 1k;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;

    # SSE log stream — must be before generic /api/ to take priority
    location ~ ^/api/training/tasks/.+/log/stream\$ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
        add_header X-Accel-Buffering no;
        chunked_transfer_encoding off;
    }

    # ^~ ensures /api/ takes priority over regex location blocks below
    location ^~ /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 300s;
        client_max_body_size 500m;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }

    location ~ /\. {
        deny all;
    }
}
EOF
    info "Nginx config written"
}

# -------------------- validate config --------------------
validate_config() {
    info "Validating nginx config..."
    nginx -t || error "Nginx config validation failed"
    info "Config OK"
}

# -------------------- check port --------------------
check_port() {
    if ss -tlnp 2>/dev/null | grep -q ":${NGINX_PORT} " || netstat -tlnp 2>/dev/null | grep -q ":${NGINX_PORT} "; then
        warn "Port ${NGINX_PORT} in use, killing old process..."
        fuser -k "${NGINX_PORT}/tcp" 2>/dev/null || true
        sleep 1
    fi
    info "Port ${NGINX_PORT} available"
}

# -------------------- setup firewall --------------------
setup_firewall() {
    if command -v firewall-cmd &> /dev/null; then
        info "Opening port ${NGINX_PORT} in firewalld..."
        firewall-cmd --permanent --add-port="${NGINX_PORT}/tcp" 2>/dev/null || true
        firewall-cmd --reload 2>/dev/null || true
    elif command -v ufw &> /dev/null; then
        info "Opening port ${NGINX_PORT} in ufw..."
        ufw allow "${NGINX_PORT}/tcp" 2>/dev/null || true
    else
        warn "No firewall detected, skipping"
    fi
}

# -------------------- start nginx --------------------
start_nginx() {
    info "Starting Nginx..."

    systemctl enable nginx 2>/dev/null || true
    systemctl restart nginx || error "Nginx start failed"

    sleep 1

    if systemctl is-active --quiet nginx; then
        info "Nginx started"
    else
        error "Nginx failed, check: journalctl -u nginx"
    fi
}

# -------------------- verify service --------------------
verify_service() {
    info "Verifying service..."
    local HTTP_CODE
    HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${NGINX_PORT}/" 2>/dev/null || echo "000")

    if [ "$HTTP_CODE" = "200" ]; then
        info "Service OK (HTTP ${HTTP_CODE})"
    else
        warn "Service returned HTTP ${HTTP_CODE}, please check config"
    fi
}

# -------------------- print summary --------------------
print_summary() {
    local IP
    IP=$(hostname -I 2>/dev/null | awk '{print $1}')
    [ -z "$IP" ] && IP="<server-ip>"

    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Nginx deployed${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo -e "  Port:      ${YELLOW}${NGINX_PORT}${NC}"
    echo -e "  Web root:  ${WEB_ROOT}"
    echo -e "  Config:    ${NGINX_CONF}"
    echo -e "  URL:       ${YELLOW}http://${IP}:${NGINX_PORT}${NC}"
    echo -e ""
    echo -e "  Commands:"
    echo -e "    restart: systemctl restart nginx"
    echo -e "    status:  systemctl status nginx"
    echo -e "    logs:    tail -f /var/log/nginx/access.log"
    echo -e "${GREEN}========================================${NC}"
}

# -------------------- enable/disable autostart --------------------
enable_autostart() {
    systemctl enable nginx
    info "Nginx 开机自启已启用"
}

disable_autostart() {
    systemctl disable nginx
    info "Nginx 开机自启已禁用"
}

# -------------------- main --------------------
main() {
    echo ""
    info "===== Nginx deploy (port: ${NGINX_PORT}) ====="
    echo ""

    detect_os
    install_nginx
    setup_web_root
    configure_nginx
    validate_config
    check_port
    setup_firewall
    start_nginx
    verify_service
    print_summary
}

case "${1:-}" in
    enable-autostart)
        enable_autostart
        ;;
    disable-autostart)
        disable_autostart
        ;;
    *)
        main "$@"
        ;;
esac
