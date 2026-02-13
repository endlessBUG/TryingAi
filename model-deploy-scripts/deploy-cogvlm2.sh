#!/bin/bash
# ============================================================
# CogVLM2 模型部署脚本（基于官方 openai_api_demo.py）
# 来源: https://github.com/zai-org/CogVLM2/tree/main/basic_demo
# 模型存放: ~/tryingai/models/cogvlm2/
# API 地址: http://<host>:8000/v1/chat/completions
# ============================================================

set -e

# -------------------- 配置 --------------------
MODEL_ID="THUDM/cogvlm2-llama3-chat-19B"
MODEL_DIR="$HOME/tryingai/models/cogvlm2"
MODEL_FILES_DIR="$HOME/tryingai/models/cogvlm2/model_files"
CONDA_ENV_NAME="cogvlm2"
PYTHON_VERSION="3.10"
DEMO_DIR="$HOME/tryingai/models/cogvlm2/basic_demo"
API_HOST="0.0.0.0"
API_PORT=7000
LOG_FILE="$HOME/tryingai/models/cogvlm2/server.log"
PID_FILE="$HOME/tryingai/models/cogvlm2/server.pid"
# 量化选项: 0=不量化(需要约40GB显存), 4=4bit(约13GB), 8=8bit(约20GB)
QUANT=4

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
    command -v git &> /dev/null || error "未找到 git"
    command -v nvidia-smi &> /dev/null || error "未找到 nvidia-smi，请确认已安装 NVIDIA 驱动"
    info "Conda: $(conda --version)"
    info "GPU 信息:"
    nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
    echo ""
}

# -------------------- 创建目录 --------------------
setup_directories() {
    info "创建目录: $MODEL_DIR"
    mkdir -p "$MODEL_DIR"
    mkdir -p "$MODEL_FILES_DIR"
}

# -------------------- Conda 虚拟环境 --------------------
# 所有需要在 conda 环境中执行的命令统一用 conda_run 包装
conda_run() {
    conda run --no-capture-output -n "$CONDA_ENV_NAME" "$@"
}

setup_conda_env() {
    if ! conda env list | grep -q "^${CONDA_ENV_NAME} "; then
        info "创建 Conda 环境: $CONDA_ENV_NAME (Python $PYTHON_VERSION)"
        conda create -n "$CONDA_ENV_NAME" python="$PYTHON_VERSION" -y
    fi

    # 验证环境中的 Python 版本
    local PY_VER
    PY_VER=$(conda_run python3 --version 2>&1)
    info "已就绪 Conda 环境: $CONDA_ENV_NAME ($PY_VER)"

    if ! echo "$PY_VER" | grep -q "$PYTHON_VERSION"; then
        warn "Python 版本不匹配，期望 $PYTHON_VERSION，实际 $PY_VER"
        warn "重新创建环境..."
        conda env remove -n "$CONDA_ENV_NAME" -y
        conda create -n "$CONDA_ENV_NAME" python="$PYTHON_VERSION" -y
        info "环境重建完成"
    fi
}

# -------------------- 下载官方 Demo --------------------
download_demo() {
    if [ -f "$DEMO_DIR/openai_api_demo.py" ]; then
        info "官方 Demo 已存在，跳过下载"
        return
    fi
    info "下载官方 CogVLM2 basic_demo..."
    mkdir -p "$DEMO_DIR"

    local BASE_URL="https://raw.githubusercontent.com/zai-org/CogVLM2/main/basic_demo"
    curl -sL "$BASE_URL/openai_api_demo.py" -o "$DEMO_DIR/openai_api_demo.py"
    curl -sL "$BASE_URL/requirements.txt" -o "$DEMO_DIR/requirements.txt"

    # 将 MODEL_PATH 替换为本地路径
    sed -i "s|MODEL_PATH = .*|MODEL_PATH = '$MODEL_FILES_DIR'|" "$DEMO_DIR/openai_api_demo.py"
    # 将端口替换为配置端口
    sed -i "s|port=8000|port=$API_PORT|" "$DEMO_DIR/openai_api_demo.py"

    info "官方 Demo 下载完成"
}

# -------------------- 安装依赖 --------------------
install_dependencies() {
    info "[1/3] 升级 pip..."
    conda_run pip install --upgrade pip --root-user-action=ignore

    if conda_run python3 -c "import torch" 2>/dev/null; then
        local TORCH_VER
        TORCH_VER=$(conda_run python3 -c "import torch; print(torch.__version__)")
        info "[2/3] PyTorch 已安装 ($TORCH_VER), 跳过"
    else
        info "[2/3] 安装 PyTorch (CUDA 12.1)..."
        conda_run pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121 --root-user-action=ignore
    fi

    info "[3/3] 安装 CogVLM2 依赖 (transformers, fastapi, etc.)..."
    # 排除 xformers 和 bitsandbytes，单独处理
    local TMP_REQ="/tmp/cogvlm2_requirements.txt"
    grep -Ev "^(xformers|bitsandbytes)" "$DEMO_DIR/requirements.txt" > "$TMP_REQ"
    conda_run pip install -r "$TMP_REQ" --root-user-action=ignore

    info "[可选] 尝试安装 xformers..."
    conda_run pip install xformers --index-url https://download.pytorch.org/whl/cu121 --root-user-action=ignore 2>/dev/null \
        && info "xformers 安装成功" \
        || warn "xformers 安装跳过（可选加速依赖，不影响使用）"

    info "[可选] 尝试安装 bitsandbytes（INT4/INT8 量化）..."
    conda_run pip install bitsandbytes --root-user-action=ignore 2>/dev/null \
        && info "bitsandbytes 安装成功" \
        || warn "bitsandbytes 安装跳过（量化依赖，QUANT=0 时不需要）"

    info "全部依赖安装完成"
}

# -------------------- 下载模型 --------------------
download_model() {
    if [ -f "$MODEL_FILES_DIR/config.json" ]; then
        info "模型已存在，跳过下载"
        return
    fi

    info "下载模型 $MODEL_ID 到 $MODEL_FILES_DIR ..."
    info "（首次下载约 38GB，请耐心等待）"

    conda_run pip install huggingface_hub --root-user-action=ignore
    conda_run python3 -c "
from huggingface_hub import snapshot_download
snapshot_download(
    repo_id='$MODEL_ID',
    local_dir='$MODEL_FILES_DIR',
    resume_download=True
)
print('模型下载完成')
"
}

# -------------------- 启动服务（后台） --------------------
start_server() {
    mkdir -p "$MODEL_DIR"
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        warn "服务已在运行 (PID: $(cat "$PID_FILE"))，请先停止"
        return
    fi

    info "启动 CogVLM2 API 服务..."
    info "  模型路径: $MODEL_FILES_DIR"
    info "  量化模式: $([ $QUANT -eq 0 ] && echo '无(FP16/BF16)' || echo "${QUANT}bit")"
    info "  监听地址: http://$API_HOST:$API_PORT"
    info "  日志文件: $LOG_FILE"
    echo ""
    info "====== 后端提示词生成器配置 ======"
    info "  类型:     本地模型"
    info "  服务地址: http://<本机IP>:$API_PORT"
    info "  模型名称: cogvlm2-19b"
    info "================================="
    echo ""

    local QUANT_ARG=""
    if [ "$QUANT" -gt 0 ]; then
        QUANT_ARG="--quant $QUANT"
    fi

    # 通过 conda run 确保子进程使用正确的 conda 环境
    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        python3 "$DEMO_DIR/openai_api_demo.py" $QUANT_ARG \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已后台启动，PID: $PID"
    info "模型加载中，请等待 1-3 分钟..."
    info "查看日志: $0 logs"
}

# -------------------- 前台启动（调试用） --------------------
start_foreground() {
    info "前台启动 CogVLM2 API 服务（Ctrl+C 停止）..."
    info "  量化模式: $([ $QUANT -eq 0 ] && echo '无(FP16/BF16)' || echo "${QUANT}bit")"

    local QUANT_ARG=""
    if [ "$QUANT" -gt 0 ]; then
        QUANT_ARG="--quant $QUANT"
    fi

    conda_run python3 "$DEMO_DIR/openai_api_demo.py" $QUANT_ARG
}

# -------------------- 停止服务 --------------------
stop_server() {
    if [ -f "$PID_FILE" ]; then
        local PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID"
            rm -f "$PID_FILE"
            info "服务已停止 (PID: $PID)"
            return
        fi
        rm -f "$PID_FILE"
    fi
    pkill -f "openai_api_demo.py" 2>/dev/null && \
        info "服务已停止" || warn "未找到运行中的服务"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "CogVLM2 服务正在运行"
        info "  PID:  $(cat "$PID_FILE")"
        info "  API:  http://localhost:$API_PORT/v1/chat/completions"
        info "  日志: $LOG_FILE"
    else
        warn "CogVLM2 服务未运行"
    fi
}

# -------------------- 测试 API --------------------
test_api() {
    info "测试 API 连通性（纯文本）..."
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:$API_PORT/v1/chat/completions" \
        -H "Content-Type: application/json" \
        -d '{
            "model": "cogvlm2-19b",
            "messages": [{"role": "user", "content": "Hello, respond with OK."}],
            "max_tokens": 50
        }' 2>&1)

    HTTP_CODE=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" = "200" ]; then
        CONTENT=$(echo "$BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['choices'][0]['message']['content'])" 2>/dev/null)
        info "API 测试通过，模型回复: $CONTENT"
    else
        error "API 测试失败 (HTTP $HTTP_CODE)，响应: $BODY"
    fi
}

# -------------------- 查看日志 --------------------
show_logs() {
    if [ -f "$LOG_FILE" ]; then
        tail -f "$LOG_FILE"
    else
        warn "日志文件不存在: $LOG_FILE"
    fi
}

# -------------------- 主入口 --------------------
usage() {
    echo "用法: $0 {install|start|start-fg|stop|status|test|logs|restart}"
    echo ""
    echo "  install   - 安装依赖、下载官方Demo和模型（首次使用）"
    echo "  start     - 后台启动 API 服务"
    echo "  start-fg  - 前台启动（调试用，Ctrl+C 停止）"
    echo "  stop      - 停止 API 服务"
    echo "  status    - 查看服务状态"
    echo "  test      - 测试 API 连通性"
    echo "  logs      - 查看服务日志（tail -f）"
    echo "  restart   - 重启 API 服务"
    echo ""
    echo "配置项（修改脚本顶部变量）:"
    echo "  QUANT=$QUANT  量化级别: 0=FP16(~40GB), 4=INT4(~13GB), 8=INT8(~20GB)"
    echo "  API_PORT=$API_PORT  服务端口"
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
    start)
        setup_conda_env
        start_server
        ;;
    start-fg)
        setup_conda_env
        start_foreground
        ;;
    stop)
        stop_server
        ;;
    status)
        show_status
        ;;
    test)
        setup_conda_env
        test_api
        ;;
    logs)
        show_logs
        ;;
    restart)
        stop_server
        sleep 2
        setup_conda_env
        start_server
        ;;
    *)
        usage
        ;;
esac
