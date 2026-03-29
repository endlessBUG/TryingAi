#!/bin/bash
# ============================================================
# ComfyUI 部署脚本
# 来源: https://github.com/comfyanonymous/ComfyUI
# 访问地址: http://<host>:8801
# ============================================================

set -e

# -------------------- 配置 --------------------
CONDA_ENV_NAME="comfyui"
PYTHON_VERSION="3.13"
INSTALL_DIR="$HOME/ai/trainer/comfyui"
API_HOST="0.0.0.0"
API_PORT=8801
LOG_FILE="$HOME/ai/trainer/comfyui/server.log"
PID_FILE="$HOME/ai/trainer/comfyui/server.pid"
REPO_URL="https://github.com/comfyanonymous/ComfyUI.git"
# pip 国内镜像源（清华源），海外服务器可注释掉此行
PIP_MIRROR="-i https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple --trusted-host mirrors.tuna.tsinghua.edu.cn"

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
    command -v conda &> /dev/null || error "未找到 conda，请先安装 Miniconda/Anaconda"
    command -v git   &> /dev/null || error "未找到 git"
    command -v nvidia-smi &> /dev/null || error "未找到 nvidia-smi，请确认已安装 NVIDIA 驱动"
    info "Conda: $(conda --version)"
    info "GPU 信息:"
    nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
    echo ""
}

# -------------------- 创建目录 --------------------
setup_directories() {
    mkdir -p "$INSTALL_DIR"
    info "安装目录: $INSTALL_DIR"
}

# -------------------- Conda 环境 --------------------
conda_run() {
    conda run --no-capture-output -n "$CONDA_ENV_NAME" "$@"
}

# install 时使用：不存在则创建
setup_conda_env() {
    if ! conda env list | grep -q "^${CONDA_ENV_NAME} "; then
        info "创建 Conda 环境: $CONDA_ENV_NAME (Python $PYTHON_VERSION)"
        conda create -n "$CONDA_ENV_NAME" python="$PYTHON_VERSION" -y
    fi
    local PY_VER
    PY_VER=$(conda_run python3 --version 2>&1)
    info "Conda 环境就绪: $CONDA_ENV_NAME ($PY_VER)"
}

# start 时使用：只验证环境存在，不做任何安装
check_conda_env() {
    conda env list | grep -q "^${CONDA_ENV_NAME} " \
        || error "Conda 环境 '$CONDA_ENV_NAME' 不存在，请先执行: $0 install"
}

# 获取 conda 环境的 Python 可执行路径（供 systemd 使用）
get_env_python() {
    local CONDA_BASE
    CONDA_BASE=$(conda info --base)
    echo "$CONDA_BASE/envs/$CONDA_ENV_NAME/bin/python3"
}

# -------------------- 克隆代码 --------------------
clone_repo() {
    if [ -f "$INSTALL_DIR/main.py" ]; then
        info "ComfyUI 代码已存在，执行 git pull 更新..."
        git -C "$INSTALL_DIR" pull
        return
    fi
    info "克隆 ComfyUI 仓库..."
    info "  来源: $REPO_URL"
    git clone "$REPO_URL" "$INSTALL_DIR"
    info "克隆完成"
}

# -------------------- 安装依赖 --------------------
install_dependencies() {
    info "[1/3] 升级 pip..."
    conda_run pip install --upgrade pip $PIP_MIRROR --root-user-action=ignore

    if conda_run python3 -c "import torch" 2>/dev/null; then
        local TORCH_VER
        TORCH_VER=$(conda_run python3 -c "import torch; print(torch.__version__)")
        info "[2/3] PyTorch 已安装 ($TORCH_VER)，跳过"
    else
        info "[2/3] 安装 PyTorch (CUDA 12.4)..."
        conda_run pip install torch torchvision torchaudio \
            --index-url https://download.pytorch.org/whl/cu124 \
            --root-user-action=ignore
    fi

    info "[3/3] 安装 ComfyUI 依赖..."
    conda_run pip install -r "$INSTALL_DIR/requirements.txt" \
        $PIP_MIRROR --root-user-action=ignore

    info "依赖安装完成"
}

# -------------------- 启动服务（后台） --------------------
start_server() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        warn "ComfyUI 已在运行 (PID: $(cat "$PID_FILE"))，请先停止"
        return
    fi

    [ -f "$INSTALL_DIR/main.py" ] || error "未找到 main.py，请先执行 install"

    info "后台启动 ComfyUI..."
    info "  监听地址: http://$API_HOST:$API_PORT"
    info "  日志文件: $LOG_FILE"
    info "  内存优化: --lowvram (低显存模式)"

    # 设置 CUDA 内存优化环境变量 + --lowvram 参数
    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        env PYTORCH_CUDA_ALLOC_CONF="expandable_segments:True,max_split_size_mb:128" \
        python3 "$INSTALL_DIR/main.py" \
            --listen "$API_HOST" \
            --port "$API_PORT" \
            --lowvram \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已后台启动，PID: $PID"
    info "模型加载中，请稍候..."
    sleep 3
    show_access_info
}

# -------------------- 前台启动（调试） --------------------
start_foreground() {
    [ -f "$INSTALL_DIR/main.py" ] || error "未找到 main.py，请先执行 install"
    info "前台启动 ComfyUI（Ctrl+C 停止）..."
    info "  内存优化: --lowvram (低显存模式)"
    PYTORCH_CUDA_ALLOC_CONF="expandable_segments:True,max_split_size_mb:128" \
    conda_run python3 "$INSTALL_DIR/main.py" \
        --listen "$API_HOST" \
        --port "$API_PORT" \
        --lowvram
}

# -------------------- 停止服务 --------------------
stop_server() {
    if [ -f "$PID_FILE" ]; then
        local PID
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID"
            rm -f "$PID_FILE"
            info "服务已停止 (PID: $PID)"
            return
        fi
        rm -f "$PID_FILE"
    fi
    pkill -f "comfyui.*main.py" 2>/dev/null && \
        info "服务已停止" || warn "未找到运行中的 ComfyUI 服务"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "ComfyUI 服务正在运行"
        info "  PID: $(cat "$PID_FILE")"
        show_access_info
    else
        warn "ComfyUI 服务未运行"
    fi
}

# -------------------- 打印访问信息 --------------------
show_access_info() {
    info "====== ComfyUI 访问信息 ======"
    info "  Web UI: http://<本机IP>:$API_PORT"
    info "  API:    http://<本机IP>:$API_PORT/api"
    info "  模型目录: $INSTALL_DIR/models"
    info "=============================="
}

# -------------------- 开机自启（systemd） --------------------
enable_autostart() {
    check_conda_env
    local CONDA_BASE
    CONDA_BASE=$(conda info --base)

    local SERVICE_FILE="/etc/systemd/system/comfyui.service"
    info "创建 systemd 服务: $SERVICE_FILE"

    cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=ComfyUI Service
After=network.target
StartLimitIntervalSec=300
StartLimitBurst=3

[Service]
Type=simple
User=root
WorkingDirectory=$INSTALL_DIR
Environment=PATH=$CONDA_BASE/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin
Environment=PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True,max_split_size_mb:128
ExecStart=$CONDA_BASE/bin/conda run --no-capture-output -n $CONDA_ENV_NAME python3 $INSTALL_DIR/main.py --listen $API_HOST --port $API_PORT --lowvram
Restart=on-failure
RestartSec=10
StandardOutput=append:$LOG_FILE
StandardError=append:$LOG_FILE

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable comfyui.service
    info "开机自启已启用"
    info "  立即启动: systemctl start comfyui"
    info "  手动启停: systemctl start/stop comfyui"
    info "  查看状态: systemctl status comfyui"
}

disable_autostart() {
    if systemctl is-enabled comfyui.service &>/dev/null; then
        systemctl disable comfyui.service
        rm -f /etc/systemd/system/comfyui.service
        systemctl daemon-reload
        info "开机自启已禁用"
    else
        warn "未找到 comfyui systemd 服务"
    fi
}

# -------------------- 更新 --------------------
update_comfyui() {
    info "更新 ComfyUI 代码..."
    git -C "$INSTALL_DIR" pull
    info "更新依赖..."
    conda_run pip install -r "$INSTALL_DIR/requirements.txt" \
        $PIP_MIRROR --root-user-action=ignore
    info "更新完成，请重启服务: $0 restart"
}

# -------------------- 查看日志 --------------------
show_logs() {
    if [ ! -f "$LOG_FILE" ]; then
        warn "日志文件不存在: $LOG_FILE"
        return
    fi
    tail -f "$LOG_FILE" &
    local TAIL_PID=$!
    trap "kill $TAIL_PID 2>/dev/null; exit 0" INT TERM
    wait $TAIL_PID
}

# -------------------- 主入口 --------------------
usage() {
    echo "用法: $0 {install|start|start-fg|stop|restart|status|update|logs|enable-autostart|disable-autostart}"
    echo ""
    echo "  install           - 克隆代码、安装依赖（首次使用）"
    echo "  start             - 后台启动 ComfyUI（直接使用已有 conda 环境）"
    echo "  start-fg          - 前台启动（调试用，Ctrl+C 停止）"
    echo "  stop              - 停止服务"
    echo "  restart           - 重启服务"
    echo "  status            - 查看运行状态"
    echo "  update            - 更新 ComfyUI 代码和依赖"
    echo "  logs              - 实时查看日志（Ctrl+C 退出）"
    echo "  enable-autostart  - 注册 systemd 服务，开机自动启动"
    echo "  disable-autostart - 取消开机自动启动"
    echo ""
    echo "配置项（修改脚本顶部变量）:"
    echo "  API_PORT=$API_PORT      服务端口"
    echo "  INSTALL_DIR=$INSTALL_DIR"
}

case "${1:-}" in
    install)
        check_prerequisites
        setup_directories
        setup_conda_env
        clone_repo
        install_dependencies
        info "安装完成！"
        show_access_info
        info "运行 '$0 start' 启动服务"
        ;;
    start)
        check_conda_env
        start_server
        ;;
    start-fg)
        check_conda_env
        start_foreground
        ;;
    stop)
        stop_server
        ;;
    restart)
        stop_server
        sleep 2
        check_conda_env
        start_server
        ;;
    status)
        show_status
        ;;
    update)
        update_comfyui
        ;;
    logs)
        show_logs
        ;;
    enable-autostart)
        enable_autostart
        ;;
    disable-autostart)
        disable_autostart
        ;;
    *)
        usage
        ;;
esac
