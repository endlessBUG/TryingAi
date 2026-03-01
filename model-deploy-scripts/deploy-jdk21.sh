#!/bin/bash
# ============================================================
# JDK 21 deploy script
# OS: CentOS Stream 9 / RHEL 9
# ============================================================

set -e

# -------------------- config --------------------
JDK_VERSION="21"
INSTALL_DIR="/usr/local/jdk-${JDK_VERSION}"
TSINGHUA_URL="https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/linux/OpenJDK21U-jdk_x64_linux_hotspot_21.0.10_7.tar.gz"
ORACLE_URL="https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz"
PROFILE_FILE="/etc/profile.d/jdk.sh"

# -------------------- color --------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# -------------------- check existing --------------------
check_existing() {
    if command -v java &> /dev/null; then
        local ver
        ver=$(java -version 2>&1 | head -1)
        info "Found existing Java: $ver"
        if java -version 2>&1 | grep -q "version \"${JDK_VERSION}"; then
            info "JDK ${JDK_VERSION} already installed"
            java -version 2>&1
            exit 0
        fi
        warn "Existing Java is not JDK ${JDK_VERSION}, will install"
    fi
}

# -------------------- download --------------------
download_jdk() {
    local tmp_file="/tmp/jdk-${JDK_VERSION}.tar.gz"

    if [ -f "$tmp_file" ]; then
        info "Archive already exists: $tmp_file"
        return 0
    fi

    info "Downloading JDK ${JDK_VERSION}..."

    local urls=("$TSINGHUA_URL" "$ORACLE_URL")
    local names=("Tsinghua Mirror" "Oracle")
    local ok=false

    for i in "${!urls[@]}"; do
        info "Trying ${names[$i]}..."
        local retry=0
        while [ $retry -lt 3 ]; do
            if curl -fSL -C - --connect-timeout 30 --retry 3 --retry-delay 5 --progress-bar -o "$tmp_file" "${urls[$i]}"; then
                info "Downloaded from ${names[$i]}"
                ok=true
                break 2
            fi
            retry=$((retry + 1))
            warn "Attempt $retry failed, retrying..."
            sleep 3
        done
        warn "${names[$i]} failed, trying next..."
    done

    if [ "$ok" = false ]; then
        rm -f "$tmp_file"
        error "All mirrors failed. Manually download and place at $tmp_file"
    fi

    info "Download complete: $(du -h "$tmp_file" | awk '{print $1}')"
}

# -------------------- install --------------------
install_jdk() {
    local tmp_file="/tmp/jdk-${JDK_VERSION}.tar.gz"

    if [ -d "$INSTALL_DIR" ] && [ -f "$INSTALL_DIR/bin/java" ]; then
        info "JDK directory already exists: $INSTALL_DIR"
        return 0
    fi

    info "Extracting to $INSTALL_DIR..."
    mkdir -p "$INSTALL_DIR"
    tar -xzf "$tmp_file" -C "$INSTALL_DIR" --strip-components=1

    if [ ! -f "$INSTALL_DIR/bin/java" ]; then
        error "Extract failed, java binary not found"
    fi

    info "JDK extracted to $INSTALL_DIR"
}

# -------------------- configure env --------------------
configure_env() {
    info "Configuring environment variables..."

    cat > "$PROFILE_FILE" <<EOF
export JAVA_HOME=${INSTALL_DIR}
export PATH=\$JAVA_HOME/bin:\$PATH
EOF

    chmod 644 "$PROFILE_FILE"
    source "$PROFILE_FILE"

    # alternatives (so 'java' works system-wide)
    update-alternatives --install /usr/bin/java java "$INSTALL_DIR/bin/java" 100 2>/dev/null || true
    update-alternatives --install /usr/bin/javac javac "$INSTALL_DIR/bin/javac" 100 2>/dev/null || true
    update-alternatives --install /usr/bin/jar jar "$INSTALL_DIR/bin/jar" 100 2>/dev/null || true
    update-alternatives --set java "$INSTALL_DIR/bin/java" 2>/dev/null || true
    update-alternatives --set javac "$INSTALL_DIR/bin/javac" 2>/dev/null || true

    info "Environment configured: $PROFILE_FILE"
}

# -------------------- verify --------------------
verify_install() {
    info "Verifying installation..."

    export JAVA_HOME="$INSTALL_DIR"
    export PATH="$JAVA_HOME/bin:$PATH"

    if ! java -version 2>&1 | grep -q "version \"${JDK_VERSION}"; then
        error "Verification failed"
    fi

    info "Verification OK"
}

# -------------------- cleanup --------------------
cleanup() {
    local tmp_file="/tmp/jdk-${JDK_VERSION}.tar.gz"
    if [ -f "$tmp_file" ]; then
        rm -f "$tmp_file"
        info "Cleaned up temp file"
    fi
}

# -------------------- print summary --------------------
print_summary() {
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  JDK ${JDK_VERSION} deployed${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo -e "  JAVA_HOME: ${YELLOW}${INSTALL_DIR}${NC}"
    echo -e "  Profile:   ${PROFILE_FILE}"
    echo -e "  Version:"
    java -version 2>&1 | sed 's/^/    /'
    echo -e ""
    echo -e "  Run ${YELLOW}source /etc/profile.d/jdk.sh${NC} or re-login to apply"
    echo -e "${GREEN}========================================${NC}"
}

# -------------------- main --------------------
main() {
    echo ""
    info "===== JDK ${JDK_VERSION} deploy script ====="
    echo ""

    [ "$(id -u)" -ne 0 ] && error "Please run as root"

    check_existing
    download_jdk
    install_jdk
    configure_env
    verify_install
    cleanup
    print_summary
}

main "$@"
