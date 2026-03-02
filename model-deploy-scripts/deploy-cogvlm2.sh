#!/bin/bash
# ============================================================
# CogVLM2 模型部署脚本（基于官方 openai_api_demo.py）
# 来源: https://github.com/zai-org/CogVLM2/tree/main/basic_demo
# 模型存放: ~/ai/trainer/models/cogvlm2/
# API 地址: http://<host>:7000/v1/chat/completions
# ============================================================

set -e

# -------------------- 配置 --------------------
MODEL_ID="THUDM/cogvlm2-llama3-chat-19B"
MODEL_DIR="$HOME/ai/trainer/models/cogvlm2"
MODEL_FILES_DIR="$HOME/ai/trainer/models/cogvlm2/model_files"
CONDA_ENV_NAME="cogvlm2"
PYTHON_VERSION="3.10"
DEMO_DIR="$HOME/ai/trainer/models/cogvlm2/basic_demo"
API_HOST="0.0.0.0"
API_PORT=8803
LOG_FILE="$HOME/ai/trainer/models/cogvlm2/server.log"
PID_FILE="$HOME/ai/trainer/models/cogvlm2/server.pid"
# 量化选项: 0=不量化(需要约40GB显存), 4=4bit(约13GB), 8=8bit(约20GB)
QUANT=4
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
    command -v conda      &> /dev/null || error "未找到 conda，请先安装 Miniconda/Anaconda"
    command -v git        &> /dev/null || error "未找到 git"
    command -v nvidia-smi &> /dev/null || error "未找到 nvidia-smi，请确认已安装 NVIDIA 驱动"
    info "Conda: $(conda --version)"
    info "GPU 信息:"
    nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
    echo ""
}

# -------------------- 创建目录 --------------------
setup_directories() {
    mkdir -p "$MODEL_DIR" "$MODEL_FILES_DIR"
    info "数据目录: $MODEL_DIR"
}

# -------------------- Conda 环境 --------------------
conda_run() {
    conda run --no-capture-output -n "$CONDA_ENV_NAME" "$@"
}

# install 时使用：环境不存在则创建，版本不符则重建
setup_conda_env() {
    if ! conda env list | grep -q "^${CONDA_ENV_NAME} "; then
        info "创建 Conda 环境: $CONDA_ENV_NAME (Python $PYTHON_VERSION)"
        conda create -n "$CONDA_ENV_NAME" python="$PYTHON_VERSION" -y
    fi

    local PY_VER
    PY_VER=$(conda_run python3 --version 2>&1)
    if ! echo "$PY_VER" | grep -q "$PYTHON_VERSION"; then
        warn "Python 版本不匹配（期望 $PYTHON_VERSION，实际 $PY_VER），重建环境..."
        conda env remove -n "$CONDA_ENV_NAME" -y
        conda create -n "$CONDA_ENV_NAME" python="$PYTHON_VERSION" -y
    fi
    info "Conda 环境就绪: $CONDA_ENV_NAME ($(conda_run python3 --version 2>&1))"
}

# start 时使用：只验证环境存在，不做安装
check_conda_env() {
    conda env list | grep -q "^${CONDA_ENV_NAME} " \
        || error "Conda 环境 '$CONDA_ENV_NAME' 不存在，请先执行: $0 install"
}

# 获取 conda 环境中的 Python 路径（供 systemd 使用）
get_env_python() {
    local CONDA_BASE
    CONDA_BASE=$(conda info --base)
    echo "$CONDA_BASE/envs/$CONDA_ENV_NAME/bin/python3"
}

# -------------------- 下载官方 Demo --------------------
download_demo() {
    mkdir -p "$DEMO_DIR"
    if [ ! -f "$DEMO_DIR/openai_api_demo.py" ]; then
        info "下载官方 CogVLM2 basic_demo..."
        local BASE_URL="https://raw.githubusercontent.com/zai-org/CogVLM2/main/basic_demo"
        curl -sL "$BASE_URL/openai_api_demo.py" -o "$DEMO_DIR/openai_api_demo.py"
        curl -sL "$BASE_URL/requirements.txt"   -o "$DEMO_DIR/requirements.txt"
        info "官方 Demo 下载完成"
    else
        info "官方 Demo 已存在，跳过下载"
    fi
    # 每次都重新应用配置，确保与脚本变量一致
    sed -i "s|MODEL_PATH = .*|MODEL_PATH = '$MODEL_FILES_DIR'|" "$DEMO_DIR/openai_api_demo.py"
    sed -i "s|port=[0-9]\+|port=$API_PORT|g"                    "$DEMO_DIR/openai_api_demo.py"
    info "Demo 配置已更新: MODEL_PATH=$MODEL_FILES_DIR, port=$API_PORT"
}

# -------------------- 安装依赖 --------------------
install_dependencies() {
    info "[1/4] 升级 pip..."
    conda_run pip install --upgrade pip $PIP_MIRROR --root-user-action=ignore

    if conda_run python3 -c "import torch" 2>/dev/null; then
        local TORCH_VER
        TORCH_VER=$(conda_run python3 -c "import torch; print(torch.__version__)")
        info "[2/4] PyTorch 已安装 ($TORCH_VER)，跳过"
    else
        info "[2/4] 安装 PyTorch (CUDA 12.1)..."
        conda_run pip install torch torchvision \
            --index-url https://download.pytorch.org/whl/cu121 \
            --root-user-action=ignore
    fi

    info "[3/4] 安装 CogVLM2 基础依赖..."
    local TMP_REQ="/tmp/cogvlm2_requirements.txt"
    # 排除版本敏感包，单独处理
    grep -Ev "^(xformers|bitsandbytes|transformers)" "$DEMO_DIR/requirements.txt" > "$TMP_REQ"
    conda_run pip install -r "$TMP_REQ" $PIP_MIRROR --root-user-action=ignore
    # transformers 4.40.2：CogVLM2 兼容的最高版本（4.44+ 会向模型 __init__ 传入 dtype kwarg）
    conda_run pip install "transformers==4.40.2" $PIP_MIRROR --root-user-action=ignore
    # accelerate 0.30.1：与 transformers 4.40.2 + bitsandbytes 4-bit 量化兼容
    # 新版 accelerate 的 dispatch_model 会对 4-bit 模型调用 .to()，导致报错
    conda_run pip install "accelerate==0.30.1" $PIP_MIRROR --root-user-action=ignore

    info "[4/4] 安装量化和加速依赖..."
    install_quant_deps

    info "全部依赖安装完成"
}

install_quant_deps() {
    # xformers：从 PyTorch 官方源安装确保与 torch 版本匹配
    conda_run pip install xformers \
        --index-url https://download.pytorch.org/whl/cu121 \
        --root-user-action=ignore 2>/dev/null \
        && info "xformers 安装成功" \
        || warn "xformers 安装跳过（可选加速依赖）"

    if [ "$QUANT" -gt 0 ]; then
        conda_run pip install "bitsandbytes>=0.46.1" $PIP_MIRROR --root-user-action=ignore \
            || error "bitsandbytes 安装失败，QUANT=$QUANT 模式无法运行"
        info "bitsandbytes 安装成功"
    else
        conda_run pip install "bitsandbytes>=0.46.1" $PIP_MIRROR --root-user-action=ignore 2>/dev/null \
            && info "bitsandbytes 安装成功" \
            || warn "bitsandbytes 安装跳过（QUANT=0 时不需要）"
    fi
}

# -------------------- 下载模型 --------------------
download_model() {
    info "下载/续传模型 $MODEL_ID 到 $MODEL_FILES_DIR ..."
    info "（snapshot_download 自动跳过已下载文件，支持断点续传）"

    conda_run pip install huggingface_hub $PIP_MIRROR --root-user-action=ignore
    conda_run python3 - <<EOF
import os
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
from huggingface_hub import snapshot_download
snapshot_download(
    repo_id='$MODEL_ID',
    local_dir='$MODEL_FILES_DIR',
    resume_download=True
)
print('模型下载完成')
EOF
}

# -------------------- 启动服务（后台） --------------------
quant_arg() {
    [ "$QUANT" -gt 0 ] && echo "--quant $QUANT" || echo ""
}

start_server() {
    mkdir -p "$MODEL_DIR"
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        warn "服务已在运行 (PID: $(cat "$PID_FILE"))，请先停止"
        return
    fi

    info "启动 CogVLM2 API 服务..."
    info "  量化模式: $([ $QUANT -eq 0 ] && echo '无(FP16/BF16)' || echo "${QUANT}bit")"
    info "  监听地址: http://$API_HOST:$API_PORT"
    info "  日志文件: $LOG_FILE"

    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        python3 "$DEMO_DIR/openai_api_demo.py" $(quant_arg) \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已后台启动，PID: $PID"
    info "模型加载中，请等待 1-3 分钟，查看进度: $0 logs"
}

# -------------------- 前台启动（调试） --------------------
start_foreground() {
    info "前台启动 CogVLM2（Ctrl+C 停止）..."
    info "  量化模式: $([ $QUANT -eq 0 ] && echo '无(FP16/BF16)' || echo "${QUANT}bit")"
    conda_run python3 "$DEMO_DIR/openai_api_demo.py" $(quant_arg)
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
    pkill -f "openai_api_demo.py" 2>/dev/null \
        && info "服务已停止" \
        || warn "未找到运行中的服务"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "CogVLM2 服务正在运行"
        info "  PID: $(cat "$PID_FILE")"
        info "  API: http://localhost:$API_PORT/v1/chat/completions"
        info "  日志: $LOG_FILE"
    else
        warn "CogVLM2 服务未运行"
    fi
}

# -------------------- 测试 API --------------------
test_api() {
    info "测试 API 连通性（本地生成测试图，无需外网）..."

    # 用 Python 生成一张 64x64 纯色测试图并转为 base64
    local TEST_B64
    TEST_B64=$(conda_run python3 - <<'EOF'
import base64, io
try:
    from PIL import Image
    img = Image.new("RGB", (64, 64), color=(100, 149, 237))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    print(base64.b64encode(buf.getvalue()).decode())
except ImportError:
    # PIL 不可用时退化为最小合法 JPEG base64
    import base64
    TINY_JPEG = (
        b'\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00'
        b'\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t'
        b'\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a'
        b'\x1f\x1e\x1d\x1a\x1c\x1c $.\' ",#\x1c\x1c(7),01444\x1f\'9=82<.342\x1e'
        b'C\t\t\t\r\x0b\r\x18\x10\x10\x18"\x1a\x1c\x1a""""""""""""""""""""""'
        b'"""""""""""""""""""""""""""""""""""\xff\xc0\x00\x0b\x08\x00\x01'
        b'\x00\x01\x01\x01\x11\x00\xff\xc4\x00\x1f\x00\x00\x01\x05\x01'
        b'\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02'
        b'\x03\x04\x05\x06\x07\x08\t\n\x0b\xff\xc4\x00\xb5\x10\x00\x02'
        b'\x01\x03\x03\x02\x04\x03\x05\x05\x04\x04\x00\x00\x01}\x01\x02'
        b'\x03\x00\x04\x11\x05\x12!1A\x06\x13Qa\x07"q\x142\x81\x91\xa1\x08'
        b'#B\xb1\xc1\x15R\xd1\xf0$3br\x82\t\n\x16\x17\x18\x19\x1a%&\'()*'
        b'456789:CDEFGHIJSTUVWXYZcdefghijstuvwxyz\x83\x84\x85\x86\x87\x88'
        b'\x89\x8a\x92\x93\x94\x95\x96\x97\x98\x99\x9a\xa2\xa3\xa4\xa5'
        b'\xa6\xa7\xa8\xa9\xaa\xb2\xb3\xb4\xb5\xb6\xb7\xb8\xb9\xba\xc2'
        b'\xc3\xc4\xc5\xc6\xc7\xc8\xc9\xca\xd2\xd3\xd4\xd5\xd6\xd7\xd8'
        b'\xd9\xda\xe1\xe2\xe3\xe4\xe5\xe6\xe7\xe8\xe9\xea\xf1\xf2\xf3'
        b'\xf4\xf5\xf6\xf7\xf8\xf9\xfa\xff\xda\x00\x08\x01\x01\x00\x00'
        b'?\x00\xfb\xd4P\x00\x00\x00\x1f\xff\xd9'
    )
    print(base64.b64encode(TINY_JPEG).decode())
EOF
)

    local RESPONSE HTTP_CODE BODY CONTENT
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:$API_PORT/v1/chat/completions" \
        -H "Content-Type: application/json" \
        -d "{
            \"model\": \"cogvlm2-19b\",
            \"messages\": [{
                \"role\": \"user\",
                \"content\": [
                    {\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/jpeg;base64,$TEST_B64\"}},
                    {\"type\": \"text\", \"text\": \"用一句话描述这张图片\"}
                ]
            }],
            \"max_tokens\": 100
        }")

    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" = "200" ]; then
        CONTENT=$(echo "$BODY" | python3 -c \
            "import sys,json; print(json.load(sys.stdin)['choices'][0]['message']['content'])" 2>/dev/null)
        info "API 测试通过，模型回复: $CONTENT"
    else
        error "API 测试失败 (HTTP $HTTP_CODE)，响应: $BODY"
    fi
}

# -------------------- 开机自启（systemd） --------------------
enable_autostart() {
    check_conda_env
    local ENV_PYTHON
    ENV_PYTHON=$(get_env_python)
    [ -f "$ENV_PYTHON" ] || error "未找到 Python: $ENV_PYTHON，请先执行 install"

    local SERVICE_FILE="/etc/systemd/system/cogvlm2.service"
    local QUANT_PARAM=""
    [ "$QUANT" -gt 0 ] && QUANT_PARAM="--quant $QUANT"

    info "创建 systemd 服务: $SERVICE_FILE"
    cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=CogVLM2 API Service
After=network.target

[Service]
Type=simple
User=root
Environment=PATH=$(dirname "$ENV_PYTHON"):/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin
ExecStart=$ENV_PYTHON $DEMO_DIR/openai_api_demo.py $QUANT_PARAM
Restart=on-failure
RestartSec=15
StandardOutput=append:$LOG_FILE
StandardError=append:$LOG_FILE

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable cogvlm2.service
    info "开机自启已启用"
    info "  手动控制: systemctl start/stop/status cogvlm2"
}

disable_autostart() {
    if systemctl is-enabled cogvlm2.service &>/dev/null; then
        systemctl disable cogvlm2.service
        rm -f /etc/systemd/system/cogvlm2.service
        systemctl daemon-reload
        info "开机自启已禁用"
    else
        warn "未找到 cogvlm2 systemd 服务"
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
    echo "用法: $0 {install|start|start-fg|stop|restart|status|test|logs|enable-autostart|disable-autostart}"
    echo ""
    echo "  install           - 安装依赖、下载 Demo 和模型（首次使用）"
    echo "  download-model    - 单独下载/续传模型文件"
    echo "  start             - 后台启动 API 服务（使用已有 conda 环境）"
    echo "  start-fg          - 前台启动（调试用，Ctrl+C 停止）"
    echo "  stop              - 停止 API 服务"
    echo "  restart           - 重启 API 服务"
    echo "  status            - 查看服务状态"
    echo "  test              - 测试 API 连通性"
    echo "  logs              - 实时查看日志（Ctrl+C 退出）"
    echo "  enable-autostart  - 注册 systemd 服务，开机自动启动"
    echo "  disable-autostart - 取消开机自动启动"
    echo ""
    echo "配置项（修改脚本顶部变量）:"
    echo "  QUANT=$QUANT      量化级别: 0=FP16(~40GB), 4=INT4(~13GB), 8=INT8(~20GB)"
    echo "  API_PORT=$API_PORT    服务端口"
}

case "${1:-}" in
    install)
        check_prerequisites
        setup_directories
        setup_conda_env
        download_demo
        install_dependencies
        download_model
        info "安装完成！运行 '$0 start' 启动服务"
        ;;
    download-model)
        check_conda_env
        download_model
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
    test)
        test_api
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
