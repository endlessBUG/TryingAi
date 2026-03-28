#!/bin/bash
# ============================================================
# Fish Audio TTS 模型部署脚本 (CUDA GPU 版本)
# 模型: fishaudio/s2-pro
# 适用平台: NVIDIA GPU 服务器 (CUDA 12.x)
# API 地址: http://<host>:8804/v1/tts
# 项目地址: https://github.com/fishaudio/fish-speech
# 官方文档: https://fish.audio/docs
#
# 已知问题修复记录:
# 1. hydra-core 缺失 -> ModuleNotFoundError: No module named 'hydra'
#    修复: 在 install_dependencies 中添加 hydra-core omegaconf
# 2. reference_loader.py UnboundLocalError -> local variable 'torchaudio' referenced before assignment
#    原因: try block 内 import torchaudio.xxx 导致 Python 将 torchaudio 视为局部变量
#    修复: 使用 hasattr() 检查替代 try-except import
# 3. 多个依赖缺失 -> cachetools, datasets, lightning, loralib, opencc, resampy, silero-vad, tiktoken, wandb, zstandard
#    修复: 在 install_dependencies 中手动安装这些依赖
# 4. 核心依赖缺失 -> loguru, pyrootutils, ormsgpack, orjson, pydantic, tensorboard, tqdm
#    说明: 这些是 fish-speech 直接 import 的包，必须显式安装
#    修复: 在 install_dependencies [3/5] 步骤中添加
# ============================================================

set -e

# -------------------- 配置 --------------------
HF_MODEL_ID="fishaudio/s2-pro"
MODEL_DIR="$HOME/ai/trainer/models/fish-audio"
MODEL_FILES_DIR="$HOME/ai/trainer/models/fish-audio/model_files"
CODE_DIR="$HOME/ai/trainer/models/fish-audio/fish-speech"
CONDA_ENV_NAME="fish-audio"
PYTHON_VERSION="3.10"
API_HOST="0.0.0.0"
API_PORT=8804
LOG_FILE="$HOME/ai/trainer/models/fish-audio/server.log"
PID_FILE="$HOME/ai/trainer/models/fish-audio/server.pid"
CUDA_VERSION="12.1"
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
    command -v git &> /dev/null || error "未找到 git"
    command -v nvidia-smi &> /dev/null || error "未找到 nvidia-smi，请确认已安装 NVIDIA 驱动"

    info "Conda: $(conda --version)"
    info "系统: $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2 || uname -a)"
    info "GPU 信息:"
    nvidia-smi --query-gpu=name,memory.total --format=csv,noheader
    echo ""
}

# -------------------- 安装系统依赖 --------------------
install_system_deps() {
    info "安装系统依赖..."

    if command -v yum &> /dev/null; then
        yum install -y git portaudio-devel ffmpeg-devel || warn "部分系统依赖安装失败"
    elif command -v apt-get &> /dev/null; then
        apt-get update && apt-get install -y git portaudio19-dev libportaudio2 ffmpeg || warn "部分系统依赖安装失败"
    else
        warn "未知包管理器，请手动安装: portaudio-devel ffmpeg"
    fi
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

check_conda_env() {
    conda env list | grep -q "^${CONDA_ENV_NAME} " \
        || error "Conda 环境 '$CONDA_ENV_NAME' 不存在，请先执行: $0 install"
}

get_env_python() {
    local CONDA_BASE
    CONDA_BASE=$(conda info --base)
    echo "$CONDA_BASE/envs/$CONDA_ENV_NAME/bin/python3"
}

# -------------------- 克隆官方代码 --------------------
clone_fish_speech() {
    info "克隆 fish-speech 官方代码..."
    if [ -d "$CODE_DIR" ]; then
        info "代码目录已存在，更新..."
        cd "$CODE_DIR" && git pull || warn "更新失败，使用现有代码"
    else
        git clone https://github.com/fishaudio/fish-speech.git "$CODE_DIR"
    fi
    info "代码目录: $CODE_DIR"
}

# -------------------- 安装依赖 --------------------
install_dependencies() {
    info "[1/5] 升级 pip..."
    conda_run pip install --upgrade pip setuptools wheel $PIP_MIRROR --root-user-action=ignore

    info "[2/5] 安装 PyTorch (CUDA $CUDA_VERSION)..."
    if conda_run python3 -c "import torch; assert torch.cuda.is_available()" 2>/dev/null; then
        local TORCH_VER
        TORCH_VER=$(conda_run python3 -c "import torch; print(torch.__version__)")
        info "PyTorch 已安装 ($TORCH_VER)，跳过"
    else
        conda_run pip install torch torchvision torchaudio \
            --index-url https://download.pytorch.org/whl/cu121 \
            --root-user-action=ignore
    fi

    info "[3/5] 安装核心依赖..."
    # 基础依赖：transformers/accelerate 用于模型加载
    # hydra-core/omegaconf: fish-speech 配置管理（缺失会导致 ModuleNotFoundError）
    # loguru: 日志库，fish-speech 直接使用
    # pyrootutils: 路径处理，api_server.py 直接 import
    # ormsgpack/orjson: API 响应序列化
    # pydantic: 数据验证，schema.py 使用
    conda_run pip install \
        transformers accelerate sentencepiece \
        einops scipy librosa soundfile \
        fastapi uvicorn python-multipart \
        huggingface_hub safetensors modelscope \
        gradio kui \
        hydra-core omegaconf \
        loguru pyrootutils ormsgpack orjson pydantic requests \
        tensorboard tqdm \
        $PIP_MIRROR --root-user-action=ignore

    info "[4/5] 安装 fish-speech..."
    cd "$CODE_DIR"
    # 先安装 fish-speech 包本身（不安装其依赖，避免版本冲突）
    conda_run pip install -e . --no-deps --root-user-action=ignore || warn "fish-speech 安装失败"

    # 手动安装 fish-speech 的核心依赖（解决 setup.py 中缺失的依赖声明）
    # 注意: 不使用 2>/dev/null 隐藏错误，以便正确安装
    # cachetools: 缓存工具，用于 reference 缓存
    # datasets: HuggingFace 数据集加载
    # einx[torch]: Einstein summation 扩展
    # lightning/pytorch-lightning: 训练框架
    # loralib: LoRA 微调支持
    # opencc-python-reimplemented: 中文简繁转换
    # resampy: 音频重采样
    # silero/silero-vad: 语音活动检测
    # tiktoken: OpenAI tokenizer
    # wandb: 实验追踪
    # zstandard: 压缩库
    # vocos: 语音编码器
    # openai-whisper: Whisper 模型支持
    # descript-audio-codec: 音频解码器
    # natsort: 自然排序
    # pyaudio: 音频 I/O
    conda_run pip install \
        vocos openai-whisper \
        silero silero-vad \
        cachetools datasets==2.18.0 "einx[torch]==0.2.2" \
        "lightning>=2.1.0" "loralib>=0.1.2" \
        opencc-python-reimplemented==0.1.7 \
        resampy "tiktoken>=0.8.0" \
        "wandb>=0.19.0" "zstandard>=0.22.0" \
        descript-audio-codec natsort pyaudio \
        $PIP_MIRROR --root-user-action=ignore || warn "部分可选依赖安装失败"

    info "[5/5] 安装 CUDA 加速库 (可选)..."
    conda_run pip install flash-attn --no-build-isolation \
        $PIP_MIRROR --root-user-action=ignore 2>/dev/null \
        && info "flash-attn 安装成功" \
        || warn "flash-attn 安装跳过（可选加速依赖）"

    info "依赖安装完成"

    # 显示已安装的关键包
    info "已安装的关键包:"
    conda_run pip list | grep -E "torch|fish|kui|fastapi|gradio" || true
}

# -------------------- 下载模型 --------------------
download_model() {
    info "下载 Fish Audio S2-Pro 模型..."

    # 方式1: 使用 huggingface-cli (推荐)
    if conda_run python3 -c "import huggingface_hub" 2>/dev/null; then
        info "从 HuggingFace 下载: $HF_MODEL_ID"
        conda_run huggingface-cli download $HF_MODEL_ID \
            --local-dir "$MODEL_FILES_DIR" \
            --local-dir-use-symlinks False || {
            warn "HuggingFace 下载失败，尝试国内镜像..."
            # 使用国内镜像
            export HF_ENDPOINT=https://hf-mirror.com
            conda_run huggingface-cli download $HF_MODEL_ID \
                --local-dir "$MODEL_FILES_DIR" \
                --local-dir-use-symlinks False
        }
    else
        # 方式2: 使用 ModelScope
        info "从 ModelScope 下载..."
        conda_run pip install modelscope $PIP_MIRROR --root-user-action=ignore
        conda_run python3 << 'PYEOF'
import os
from modelscope import snapshot_download

target_dir = os.path.expanduser('~/ai/trainer/models/fish-audio/model_files')
os.makedirs(target_dir, exist_ok=True)

print(f'下载模型到: {target_dir}')
model_dir = snapshot_download('fishaudio/s2-pro')
print(f'模型下载成功: {model_dir}')

# 复制到目标目录
import shutil
from pathlib import Path
src = Path(model_dir)
dst = Path(target_dir)
for f in src.iterdir():
    if f.is_file():
        shutil.copy2(f, dst / f.name)
        print(f'  复制: {f.name}')
PYEOF
    fi

    info "模型文件列表:"
    ls -la "$MODEL_FILES_DIR"
}

# -------------------- 启动服务（后台） --------------------
start_server() {
    mkdir -p "$MODEL_DIR"

    # 检查端口
    if lsof -i:$API_PORT >/dev/null 2>&1 2>/dev/null || netstat -tlnp 2>/dev/null | grep -q ":$API_PORT"; then
        warn "端口 $API_PORT 已被占用，正在清理..."
        pkill -f "api_server.py" 2>/dev/null || true
        sleep 2
    fi

    # 清理旧 PID
    rm -f "$PID_FILE"

    # 检查模型
    if [ ! -d "$MODEL_FILES_DIR" ]; then
        warn "模型目录不存在: $MODEL_FILES_DIR"
        warn "请先执行: $0 download-model"
    fi

    info "启动 Fish Audio TTS API 服务..."
    info "  监听地址: http://$API_HOST:$API_PORT"
    info "  模型路径: $MODEL_FILES_DIR"
    info "  日志文件: $LOG_FILE"
    info ""
    info "  API 端点:"
    info "    - GET  /v1/health    健康检查"
    info "    - POST /v1/tts        文本转语音"
    info "    - POST /v1/vqgan/encode  VQ编码(声音克隆)"
    info "    - POST /v1/vqgan/decode  VQ解码"
    info ""

    # 启动官方 API server
    cd "$CODE_DIR"
    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        python3 tools/api_server.py \
        --listen $API_HOST:$API_PORT \
        --llama-checkpoint-path "$MODEL_FILES_DIR" \
        --decoder-checkpoint-path "$MODEL_FILES_DIR/codec.pth" \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已启动，PID: $PID"
    info "模型加载中，查看进度: $0 logs"
    info "测试接口: $0 test"
}

# -------------------- 前台启动（调试） --------------------
start_foreground() {
    info "前台启动 Fish Audio TTS（Ctrl+C 停止）..."
    cd "$CODE_DIR"
    conda_run python3 tools/api_server.py \
        --listen $API_HOST:$API_PORT \
        --llama-checkpoint-path "$MODEL_FILES_DIR" \
        --decoder-checkpoint-path "$MODEL_FILES_DIR/codec.pth"
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
    pkill -f "api_server.py" 2>/dev/null || true
    info "服务已停止"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "Fish Audio TTS 服务正在运行"
        info "  PID: $(cat "$PID_FILE")"
        info "  API: http://localhost:$API_PORT"
        info "  日志: $LOG_FILE"

        # 检查健康状态
        local HEALTH
        HEALTH=$(curl -s "http://localhost:$API_PORT/v1/health" 2>/dev/null || echo "服务无响应")
        info "  健康状态: $HEALTH"
    else
        warn "Fish Audio TTS 服务未运行"
    fi
}

# -------------------- 测试 API --------------------
test_api() {
    info "测试 API 连通性..."

    # 健康检查
    local HEALTH
    HEALTH=$(curl -s "http://localhost:$API_PORT/v1/health" 2>/dev/null)
    if echo "$HEALTH" | grep -q "ok"; then
        info "✅ 健康检查通过"
    else
        warn "❌ 健康检查失败: $HEALTH"
        return 1
    fi

    # TTS 测试
    info "测试 /v1/tts 接口..."
    local HTTP_CODE
    HTTP_CODE=$(curl -s -w "%{http_code}" -o /tmp/test_tts.wav \
        -X POST "http://localhost:$API_PORT/v1/tts" \
        -H "Content-Type: application/json" \
        -d '{"text":"你好，这是一个测试"}')

    if [ "$HTTP_CODE" = "200" ]; then
        local SIZE
        SIZE=$(ls -lh /tmp/test_tts.wav 2>/dev/null | awk '{print $5}')
        info "✅ TTS 接口测试成功，音频大小: $SIZE"
        info "  保存位置: /tmp/test_tts.wav"
    else
        warn "❌ TTS 接口返回 HTTP $HTTP_CODE"
    fi

    echo ""
    info "调用示例:"
    info "  # TTS 文本转语音"
    info "  curl -X POST http://localhost:$API_PORT/v1/tts \\"
    info "    -H 'Content-Type: application/json' \\"
    info "    -d '{\"text\":\"你好世界\"}' -o output.wav"
    echo ""
    info "  # 声音克隆（使用已上传的参考音色）"
    info "  curl -X POST http://localhost:$API_PORT/v1/tts \\"
    info "    -H 'Content-Type: application/json' \\"
    info "    -d '{\"text\":\"克隆语音\",\"reference_id\":\"my_voice\"}' -o cloned.wav"
}

# -------------------- 测试音色克隆 --------------------
# 音色克隆有两种方式:
# 1. 先上传参考音色，再用 reference_id 引用
# 2. 直接在 TTS 请求中传入 reference_audio (base64编码)
test_voice_clone() {
    local AUDIO_FILE="${2:-}"

    if [ -z "$AUDIO_FILE" ]; then
        warn "用法: $0 test-clone <音频文件路径> [参考文本]"
        warn "示例: $0 test-clone /path/to/voice.wav '这是参考文本'"
        warn ""
        info "音色克隆 API 说明:"
        info "  方式1: 上传参考音色 -> 使用 reference_id"
        info "    POST /v1/references/add  上传音色文件"
        info "    GET  /v1/references/list  查看已上传音色"
        info "    POST /v1/tts?reference_id=xxx  使用指定音色"
        info ""
        info "  方式2: 直接传入参考音频 (适合单次使用)"
        info "    POST /v1/tts 请求中包含 references 字段"
        info "    references: [{audio: base64编码, text: 参考文本}]"
        return 1
    fi

    if [ ! -f "$AUDIO_FILE" ]; then
        error "音频文件不存在: $AUDIO_FILE"
    fi

    local REF_TEXT="${3:-"这是参考音色的文本内容"}"
    local REF_ID="test_clone_voice"

    info "测试音色克隆功能..."
    info "  参考音频: $AUDIO_FILE"
    info "  参考文本: $REF_TEXT"
    info "  音色ID: $REF_ID"
    echo ""

    # 检查服务状态
    local HEALTH
    HEALTH=$(curl -s "http://localhost:$API_PORT/v1/health" 2>/dev/null)
    if ! echo "$HEALTH" | grep -q "ok"; then
        warn "服务未运行，请先启动: $0 start"
        return 1
    fi

    # 方式1: 上传参考音色
    info "[方式1] 上传参考音色到服务器..."
    local UPLOAD_RESULT
    UPLOAD_RESULT=$(curl -s -X POST "http://localhost:$API_PORT/v1/references/add" \
        -F "id=$REF_ID" \
        -F "audio=@$AUDIO_FILE" \
        -F "text=$REF_TEXT" 2>/dev/null)

    if echo "$UPLOAD_RESULT" | grep -q "success"; then
        info "✅ 参考音色上传成功"
    else
        warn "❌ 上传失败: $UPLOAD_RESULT"
        return 1
    fi

    # 查看已上传的音色列表
    info "已上传的音色列表:"
    curl -s "http://localhost:$API_PORT/v1/references/list" 2>/dev/null | \
        python3 -c "import sys,ormsgpack; print(ormsgpack.unpackb(sys.stdin.buffer.read()))" 2>/dev/null || \
        echo "(msgpack格式，需安装ormsgpack解析)"

    echo ""

    # 使用 reference_id 进行克隆
    info "[方式1] 使用 reference_id 进行音色克隆..."
    local CLONE_HTTP
    CLONE_HTTP=$(curl -s -w "%{http_code}" -o /tmp/cloned_voice1.wav \
        -X POST "http://localhost:$API_PORT/v1/tts" \
        -H "Content-Type: application/json" \
        -d "{\"text\":\"这是使用克隆音色生成的语音测试\",\"reference_id\":\"$REF_ID\"}")

    if [ "$CLONE_HTTP" = "200" ]; then
        local SIZE
        SIZE=$(ls -lh /tmp/cloned_voice1.wav 2>/dev/null | awk '{print $5}')
        info "✅ 音色克隆成功，音频大小: $SIZE"
        info "  保存位置: /tmp/cloned_voice1.wav"
    else
        warn "❌ 克隆失败，HTTP $CLONE_HTTP"
    fi

    echo ""

    # 方式2: 直接传入参考音频
    info "[方式2] 直接在请求中传入参考音频..."
    source ~/miniconda3/etc/profile.d/conda.sh && conda activate fish-audio

    python3 << PYEOF
import requests
import base64

# 读取参考音频
with open("$AUDIO_FILE", "rb") as f:
    audio_data = f.read()
audio_base64 = base64.b64encode(audio_data).decode()

# 直接在请求中提供参考音频
response = requests.post(
    "http://localhost:$API_PORT/v1/tts",
    json={
        "text": "这是直接传入参考音频的克隆测试",
        "references": [
            {
                "audio": audio_base64,
                "text": "$REF_TEXT"
            }
        ]
    }
)

if response.status_code == 200:
    with open("/tmp/cloned_voice2.wav", "wb") as f:
        f.write(response.content)
    print(f"✅ 直接传入方式克隆成功，音频大小: {len(response.content)} bytes")
    print("  保存位置: /tmp/cloned_voice2.wav")
else:
    print(f"❌ 克隆失败: HTTP {response.status_code}")
PYEOF

    echo ""
    info "音色克隆测试完成！"
    info "  /tmp/cloned_voice1.wav  - 使用 reference_id 方式"
    info "  /tmp/cloned_voice2.wav  - 直接传入参考音频方式"

    # 清理测试音色
    info ""
    info "清理测试音色..."
    curl -s -X DELETE "http://localhost:$API_PORT/v1/references/delete" \
        -H "Content-Type: application/json" \
        -d "{\"reference_id\":\"$REF_ID\"}" 2>/dev/null
    info "测试音色已删除"
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

# -------------------- 诊断 --------------------
diagnose() {
    info "诊断 Fish Audio 环境..."

    echo ""
    echo "=== 系统信息 ==="
    echo "OS: $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d= -f2)"
    echo "Python: $(conda_run python3 --version 2>/dev/null || echo '未安装')"
    echo "Conda 环境: $CONDA_ENV_NAME"
    conda env list | grep fish-audio || warn "Conda 环境不存在"

    echo ""
    echo "=== GPU 信息 ==="
    nvidia-smi --query-gpu=name,memory.total,memory.free --format=csv,noheader 2>/dev/null || warn "无 GPU 或驱动未安装"

    echo ""
    echo "=== 模型目录 ==="
    if [ -d "$MODEL_FILES_DIR" ]; then
        ls -la "$MODEL_FILES_DIR"
        echo ""
        echo "模型文件:"
        find "$MODEL_FILES_DIR" -name "*.safetensors" -o -name "*.pth" -o -name "*.pt" -o -name "*.bin" 2>/dev/null
    else
        warn "模型目录不存在: $MODEL_FILES_DIR"
    fi

    echo ""
    echo "=== 代码目录 ==="
    if [ -d "$CODE_DIR" ]; then
        echo "fish-speech 版本: $(cd $CODE_DIR && git log -1 --oneline 2>/dev/null || echo 'unknown')"
        ls "$CODE_DIR/tools/api_server.py" 2>/dev/null && echo "✅ API server 脚本存在" || warn "API server 脚本不存在"
    else
        warn "代码目录不存在: $CODE_DIR"
    fi

    echo ""
    echo "=== 关键 Python 包 ==="
    conda_run pip list 2>/dev/null | grep -E "torch|fish|kui|fastapi|transformers|accelerate" || warn "请检查依赖安装"

    echo ""
    echo "=== 服务状态 ==="
    if [ -f "$PID_FILE" ] && kill -0 "$(cat $PID_FILE)" 2>/dev/null; then
        echo "✅ 服务运行中 (PID: $(cat $PID_FILE))"
        curl -s "http://localhost:$API_PORT/v1/health" 2>/dev/null || echo "服务无响应"
    else
        echo "❌ 服务未运行"
    fi
}

# -------------------- 开机自启 --------------------
enable_autostart() {
    check_conda_env
    local ENV_PYTHON
    ENV_PYTHON=$(get_env_python)
    [ -f "$ENV_PYTHON" ] || error "未找到 Python: $ENV_PYTHON"

    local SERVICE_FILE="/etc/systemd/system/fish-audio.service"
    info "创建 systemd 服务: $SERVICE_FILE"

    cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Fish Audio TTS API Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$CODE_DIR
Environment=PATH=$(dirname "$ENV_PYTHON"):/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin
ExecStart=$ENV_PYTHON $CODE_DIR/tools/api_server.py --listen $API_HOST:$API_PORT --llama-checkpoint-path $MODEL_FILES_DIR --decoder-checkpoint-path $MODEL_FILES_DIR/codec.pth
Restart=on-failure
RestartSec=15
StandardOutput=append:$LOG_FILE
StandardError=append:$LOG_FILE

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable fish-audio.service
    info "开机自启已启用"
    info "  手动控制: systemctl start/stop/status fish-audio"
}

disable_autostart() {
    if systemctl is-enabled fish-audio.service &>/dev/null; then
        systemctl disable fish-audio.service
        rm -f /etc/systemd/system/fish-audio.service
        systemctl daemon-reload
        info "开机自启已禁用"
    else
        warn "未找到 fish-audio systemd 服务"
    fi
}

# -------------------- 主入口 --------------------
usage() {
    echo "用法: $0 <命令>"
    echo ""
    echo "命令列表:"
    echo "  install          - 完整安装（系统依赖 + Conda环境 + 代码 + 依赖）"
    echo "  install-deps     - 安装系统依赖 (portaudio, ffmpeg 等)"
    echo "  install-python   - 只安装 Python 依赖"
    echo "  download-model   - 下载模型文件"
    echo "  start            - 后台启动 API 服务"
    echo "  start-fg         - 前台启动（调试用）"
    echo "  stop             - 停止服务"
    echo "  restart          - 重启服务"
    echo "  status           - 查看服务状态"
    echo "  test             - 测试 API 接口"
    echo "  test-clone       - 测试音色克隆 (需提供音频文件)"
    echo "  logs             - 查看日志 (Ctrl+C 退出)"
    echo "  diagnose         - 诊断环境"
    echo "  enable-autostart - 开机自启"
    echo "  disable-autostart- 取消开机自启"
    echo ""
    echo "快速开始:"
    echo "  $0 install          # 首次安装"
    echo "  $0 download-model   # 下载模型"
    echo "  $0 start            # 启动服务"
    echo "  $0 test             # 测试接口"
    echo ""
    echo "API 端点:"
    echo "  GET  /v1/health         健康检查"
    echo "  POST /v1/tts            文本转语音"
    echo "  POST /v1/vqgan/encode   VQ编码(声音克隆)"
    echo "  POST /v1/vqgan/decode   VQ解码"
    echo "  POST /v1/references/add   上传参考音色"
    echo "  GET  /v1/references/list  查看已上传音色"
    echo "  DELETE /v1/references/delete  删除参考音色"
    echo ""
    echo "音色克隆示例:"
    echo "  # 上传参考音色"
    echo "  curl -X POST http://localhost:$API_PORT/v1/references/add \\"
    echo "    -F 'id=my_voice' -F 'audio=@voice.wav' -F 'text=参考文本'"
    echo ""
    echo "  # 使用参考音色进行 TTS"
    echo "  curl -X POST http://localhost:$API_PORT/v1/tts \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"text\":\"你好\",\"reference_id\":\"my_voice\"}' -o output.wav"
    echo ""
    echo "配置项 (修改脚本顶部变量):"
    echo "  API_PORT=$API_PORT"
    echo "  MODEL_FILES_DIR=$MODEL_FILES_DIR"
    echo "  CODE_DIR=$CODE_DIR"
}

case "${1:-}" in
    install)
        check_prerequisites
        install_system_deps
        setup_directories
        setup_conda_env
        clone_fish_speech
        install_dependencies
        info "安装完成！"
        info "下一步: $0 download-model"
        ;;
    install-deps)
        install_system_deps
        ;;
    install-python)
        check_conda_env
        install_dependencies
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
    test-clone)
        test_voice_clone "$@"
        ;;
    logs)
        show_logs
        ;;
    diagnose)
        diagnose
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