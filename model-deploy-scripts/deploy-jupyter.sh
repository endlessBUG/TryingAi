#!/bin/bash
# ============================================================
# Jupyter Notebook 部署脚本
# 访问地址: http://<host>:JUPYTER_PORT
# 支持跨域访问，后台运行
# ============================================================

set -e

# -------------------- 配置 --------------------
CONDA_ENV_NAME="jupyter"
PYTHON_VERSION="3.10"
JUPYTER_HOST="0.0.0.0"
JUPYTER_PORT=8805
NOTEBOOK_DIR="/root"
LOG_FILE="$HOME/tryingai/jupyter/server.log"
PID_FILE="$HOME/tryingai/jupyter/server.pid"
CONFIG_FILE="$HOME/tryingai/jupyter/jupyter_notebook_config.py"
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
    info "Conda: $(conda --version)"
}

# -------------------- 创建目录 --------------------
setup_directories() {
    mkdir -p "$(dirname "$LOG_FILE")"
    info "工作目录: $NOTEBOOK_DIR"
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

# 获取 conda 环境的可执行目录（供 systemd 使用）
get_env_bin() {
    local CONDA_BASE
    CONDA_BASE=$(conda info --base)
    echo "$CONDA_BASE/envs/$CONDA_ENV_NAME/bin"
}

# -------------------- 安装依赖 --------------------
install_dependencies() {
    info "升级 pip..."
    conda_run pip install --upgrade pip $PIP_MIRROR --root-user-action=ignore

    info "安装 Jupyter Notebook..."
    conda_run pip install notebook $PIP_MIRROR --root-user-action=ignore

    info "安装常用数据科学依赖..."
    conda_run pip install numpy pandas matplotlib scikit-learn ipywidgets \
        $PIP_MIRROR --root-user-action=ignore

    info "依赖安装完成"
}

# -------------------- 生成配置文件 --------------------
generate_config() {
    info "生成 Jupyter 配置文件: $CONFIG_FILE"
    mkdir -p "$(dirname "$CONFIG_FILE")"

    cat > "$CONFIG_FILE" <<EOF
# Jupyter Notebook 配置
c.ServerApp.ip = '$JUPYTER_HOST'
c.ServerApp.port = $JUPYTER_PORT
c.ServerApp.open_browser = False
c.ServerApp.root_dir = '$NOTEBOOK_DIR'
c.ServerApp.allow_root = True

# 跨域访问
c.ServerApp.allow_origin = '*'
c.ServerApp.allow_origin_pat = '.*'
c.ServerApp.allow_credentials = True

# 远程访问
c.ServerApp.allow_remote_access = True
c.ServerApp.disable_check_xsrf = True

# 禁用认证
c.IdentityProvider.token = ''
c.ServerApp.password = ''
EOF

    info "配置文件生成完成"
}

# -------------------- 启动服务（后台） --------------------
start_server() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        warn "Jupyter 已在运行 (PID: $(cat "$PID_FILE"))，请先停止"
        return
    fi

    [ -f "$CONFIG_FILE" ] || generate_config

    info "后台启动 Jupyter Notebook..."
    info "  监听地址: http://$JUPYTER_HOST:$JUPYTER_PORT"
    info "  工作目录: $NOTEBOOK_DIR"
    info "  日志文件: $LOG_FILE"

    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        jupyter notebook --config="$CONFIG_FILE" --allow-root \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已后台启动，PID: $PID"

    sleep 2
    show_access_info
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
    pkill -f "jupyter-notebook" 2>/dev/null && \
        info "服务已停止" || warn "未找到运行中的 Jupyter 服务"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "Jupyter Notebook 服务正在运行"
        info "  PID:  $(cat "$PID_FILE")"
        show_access_info
    else
        warn "Jupyter Notebook 服务未运行"
    fi
}

# -------------------- 打印访问信息 --------------------
show_access_info() {
    info "====== Jupyter 访问信息 ======"
    info "  地址: http://<本机IP>:$JUPYTER_PORT"
    info "=============================="
}

# -------------------- 开机自启（systemd） --------------------
enable_autostart() {
    check_conda_env
    local ENV_BIN
    ENV_BIN=$(get_env_bin)
    [ -f "$ENV_BIN/jupyter" ] || error "未找到 jupyter: $ENV_BIN/jupyter，请先执行 install"

    local SERVICE_FILE="/etc/systemd/system/jupyter.service"
    info "创建 systemd 服务: $SERVICE_FILE"

    cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Jupyter Notebook Service
After=network.target

[Service]
Type=simple
User=root
Environment=PATH=$ENV_BIN:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin
ExecStart=$ENV_BIN/jupyter notebook --config=$CONFIG_FILE --allow-root
Restart=on-failure
RestartSec=10
StandardOutput=append:$LOG_FILE
StandardError=append:$LOG_FILE

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable jupyter.service
    info "开机自启已启用"
    info "  手动启停: systemctl start/stop jupyter"
    info "  查看状态: systemctl status jupyter"
}

disable_autostart() {
    if systemctl is-enabled jupyter.service &>/dev/null; then
        systemctl disable jupyter.service
        rm -f /etc/systemd/system/jupyter.service
        systemctl daemon-reload
        info "开机自启已禁用"
    else
        warn "未找到 jupyter systemd 服务"
    fi
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
    echo "用法: $0 {install|start|stop|restart|status|logs|enable-autostart|disable-autostart}"
    echo ""
    echo "  install           - 安装 Jupyter 及常用依赖"
    echo "  start             - 后台启动 Jupyter（直接使用已有 conda 环境）"
    echo "  stop              - 停止服务"
    echo "  restart           - 重启服务"
    echo "  status            - 查看运行状态及访问地址"
    echo "  logs              - 实时查看日志（Ctrl+C 退出）"
    echo "  enable-autostart  - 注册 systemd 服务，开机自动启动"
    echo "  disable-autostart - 取消开机自动启动"
    echo ""
    echo "配置项（修改脚本顶部变量）:"
    echo "  JUPYTER_PORT=$JUPYTER_PORT   服务端口"
    echo "  NOTEBOOK_DIR=$NOTEBOOK_DIR"
}

case "${1:-}" in
    install)
        check_prerequisites
        setup_directories
        setup_conda_env
        install_dependencies
        generate_config
        info "安装完成！运行 '$0 start' 启动服务"
        ;;
    start)
        check_conda_env
        start_server
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
