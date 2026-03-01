#!/bin/bash
# ============================================================
# Miniconda 安装脚本
# 安装路径: ~/miniconda3
# 适用系统: Linux x86_64 (CentOS 7 / Ubuntu)
# ============================================================

set -e

# -------------------- 配置 --------------------
INSTALL_DIR="$HOME/miniconda3"
# Miniconda 下载镜像（清华源），海外服务器可替换为官方地址
MINICONDA_URL="https://mirrors.tuna.tsinghua.edu.cn/anaconda/miniconda/Miniconda3-latest-Linux-x86_64.sh"
# 官方地址（备用）: https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
INSTALLER="/tmp/miniconda_installer.sh"

# -------------------- 颜色输出 --------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# -------------------- 前置检查 --------------------
check_prerequisites() {
    info "检查前置依赖..."
    command -v curl &> /dev/null || command -v wget &> /dev/null \
        || error "未找到 curl 或 wget，请先安装其中一个"
    command -v bash &> /dev/null || error "未找到 bash"

    local ARCH
    ARCH=$(uname -m)
    [ "$ARCH" = "x86_64" ] || error "当前脚本仅支持 x86_64，当前架构: $ARCH"
    info "系统架构: $ARCH ✓"
}

# -------------------- 下载安装包 --------------------
download_installer() {
    if [ -f "$INSTALLER" ]; then
        info "安装包已存在，跳过下载: $INSTALLER"
        return
    fi
    info "下载 Miniconda 安装包..."
    info "  来源: $MINICONDA_URL"
    if command -v curl &> /dev/null; then
        curl -L "$MINICONDA_URL" -o "$INSTALLER" --progress-bar
    else
        wget "$MINICONDA_URL" -O "$INSTALLER" --show-progress
    fi
    chmod +x "$INSTALLER"
    info "下载完成: $INSTALLER"
}

# -------------------- 安装 Miniconda --------------------
install_miniconda() {
    if [ -d "$INSTALL_DIR" ]; then
        warn "Miniconda 已安装于 $INSTALL_DIR，跳过安装"
        warn "如需重装请先删除该目录: rm -rf $INSTALL_DIR"
        return
    fi
    info "安装 Miniconda 到 $INSTALL_DIR ..."
    bash "$INSTALLER" -b -p "$INSTALL_DIR"
    info "安装完成"
}

# -------------------- 配置 PATH --------------------
configure_path() {
    local SHELL_RC
    SHELL_RC=$(detect_shell_rc)

    if grep -q "miniconda3/bin" "$SHELL_RC" 2>/dev/null; then
        info "PATH 已配置，跳过 ($SHELL_RC)"
    else
        info "写入 PATH 到 $SHELL_RC ..."
        cat >> "$SHELL_RC" <<EOF

# >>> Miniconda 初始化 >>>
export PATH="$INSTALL_DIR/bin:\$PATH"
# <<< Miniconda 初始化 <<<
EOF
        info "PATH 配置完成"
    fi

    # 当前 shell 立即生效
    export PATH="$INSTALL_DIR/bin:$PATH"
}

detect_shell_rc() {
    case "$SHELL" in
        */zsh)  echo "$HOME/.zshrc"  ;;
        */fish) echo "$HOME/.config/fish/config.fish" ;;
        *)      echo "$HOME/.bashrc" ;;
    esac
}

# -------------------- 配置 conda init --------------------
configure_conda_init() {
    info "初始化 conda (conda init)..."
    "$INSTALL_DIR/bin/conda" init bash 2>/dev/null || true
    if [ -f "$HOME/.zshrc" ]; then
        "$INSTALL_DIR/bin/conda" init zsh 2>/dev/null || true
    fi
    info "conda init 完成"
}

# -------------------- 配置国内镜像源 --------------------
configure_mirrors() {
    info "配置清华镜像源..."
    "$INSTALL_DIR/bin/conda" config --set show_channel_urls yes
    "$INSTALL_DIR/bin/conda" config --add channels defaults
    "$INSTALL_DIR/bin/conda" config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/main/
    "$INSTALL_DIR/bin/conda" config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/free/
    "$INSTALL_DIR/bin/conda" config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/conda-forge/
    info "镜像源配置完成"
}

# -------------------- 验证安装 --------------------
verify_install() {
    info "验证安装..."
    local CONDA_BIN="$INSTALL_DIR/bin/conda"
    [ -f "$CONDA_BIN" ] || error "conda 可执行文件未找到: $CONDA_BIN"
    info "Conda 版本: $("$CONDA_BIN" --version)"
    info "Python 版本: $("$INSTALL_DIR/bin/python3" --version)"
    info "安装路径: $INSTALL_DIR"
}

# -------------------- 卸载 --------------------
uninstall_miniconda() {
    if [ ! -d "$INSTALL_DIR" ]; then
        warn "未找到 Miniconda 安装目录: $INSTALL_DIR"
        return
    fi

    warn "即将删除 $INSTALL_DIR，5秒后继续（Ctrl+C 取消）..."
    sleep 5

    rm -rf "$INSTALL_DIR"
    info "已删除安装目录"

    local SHELL_RC
    SHELL_RC=$(detect_shell_rc)
    if grep -q "Miniconda 初始化" "$SHELL_RC" 2>/dev/null; then
        sed -i '/# >>> Miniconda 初始化 >>>/,/# <<< Miniconda 初始化 <<</d' "$SHELL_RC"
        info "已清理 $SHELL_RC 中的 PATH 配置"
    fi
    info "卸载完成，请重新打开终端"
}

# -------------------- 主入口 --------------------
usage() {
    echo "用法: $0 {install|verify|uninstall}"
    echo ""
    echo "  install    - 下载并安装 Miniconda，配置 PATH 和镜像源"
    echo "  verify     - 验证已安装的 Miniconda"
    echo "  uninstall  - 卸载 Miniconda"
    echo ""
    echo "配置项（修改脚本顶部变量）:"
    echo "  INSTALL_DIR=$INSTALL_DIR"
    echo "  MINICONDA_URL=<下载地址>"
}

case "${1:-}" in
    install)
        check_prerequisites
        download_installer
        install_miniconda
        configure_path
        configure_conda_init
        configure_mirrors
        verify_install
        rm -f "$INSTALLER"
        info "======================================"
        info "Miniconda 安装完成！"
        info "请执行以下命令使配置立即生效："
        info "  source $(detect_shell_rc)"
        info "======================================"
        ;;
    verify)
        verify_install
        ;;
    uninstall)
        uninstall_miniconda
        ;;
    *)
        usage
        ;;
esac
