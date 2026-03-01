#!/bin/bash
# ============================================================
# JoyCaption Beta One 模型部署脚本
# 模型: fancyfeast/llama-joycaption-beta-one-hf-llava
# 模型存放: ~/tryingai/models/joycaption/
# API 地址: http://<host>:8804/v1/chat/completions
# ============================================================

set -e

# -------------------- 配置 --------------------
MODEL_ID="fancyfeast/llama-joycaption-beta-one-hf-llava"
MODEL_DIR="$HOME/tryingai/models/joycaption"
MODEL_FILES_DIR="$HOME/tryingai/models/joycaption/model_files"
CONDA_ENV_NAME="joycaption"
PYTHON_VERSION="3.10"
SERVER_SCRIPT="$MODEL_DIR/api_server.py"
API_HOST="0.0.0.0"
API_PORT=8803
LOG_FILE="$MODEL_DIR/server.log"
PID_FILE="$MODEL_DIR/server.pid"
# 量化选项: 0=不量化(bfloat16, ~16GB显存), 4=4bit(~6GB), 8=8bit(~10GB)
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

# -------------------- 安装依赖 --------------------
pkg_installed() {
    conda_run python3 -c "import $1" 2>/dev/null
}

install_dependencies() {
    info "[1/4] 升级 pip..."
    conda_run pip install --upgrade pip $PIP_MIRROR --root-user-action=ignore

    if pkg_installed torch; then
        local TORCH_VER
        TORCH_VER=$(conda_run python3 -c "import torch; print(torch.__version__)")
        info "[2/4] PyTorch 已安装 ($TORCH_VER)，跳过"
    else
        info "[2/4] 安装 PyTorch (CUDA 12.1，通过 conda 走国内镜像)..."
        conda install -n "$CONDA_ENV_NAME" \
            pytorch torchvision pytorch-cuda=12.1 \
            -c pytorch -c nvidia -y \
            || {
                warn "conda 安装失败，降级到 pip 安装（速度较慢）..."
                conda_run pip install torch torchvision \
                    --index-url https://download.pytorch.org/whl/cu121 \
                    --root-user-action=ignore
            }
    fi

    if pkg_installed transformers && pkg_installed accelerate && pkg_installed PIL; then
        info "[3/4] transformers / accelerate / Pillow 已安装，跳过"
    else
        info "[3/4] 安装 transformers 及模型依赖..."
        conda_run pip install \
            "transformers>=4.45.0" \
            "accelerate>=0.30.0" \
            "Pillow>=10.0.0" \
            $PIP_MIRROR --root-user-action=ignore
    fi

    if pkg_installed fastapi && pkg_installed uvicorn && pkg_installed huggingface_hub; then
        info "[4/4] fastapi / uvicorn / huggingface_hub 已安装，跳过"
    else
        info "[4/4] 安装 API 服务依赖..."
        conda_run pip install \
            "fastapi>=0.111.0" \
            "uvicorn[standard]>=0.30.0" \
            "huggingface_hub" \
            $PIP_MIRROR --root-user-action=ignore
    fi

    install_quant_deps
    info "全部依赖安装完成"
}

install_quant_deps() {
    if pkg_installed bitsandbytes; then
        info "bitsandbytes 已安装，跳过"
        return
    fi
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
    info "  镜像源: hf-mirror.com，并发线程: 8"

    conda_run python3 - <<EOF
import os
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
from huggingface_hub import snapshot_download
snapshot_download(
    repo_id='$MODEL_ID',
    local_dir='$MODEL_FILES_DIR',
    resume_download=True,
    max_workers=8,
    local_dir_use_symlinks=False,
)
print('模型下载完成')
EOF
}

# -------------------- 生成 API Server 脚本 --------------------
generate_server_script() {
    info "生成 API Server 脚本: $SERVER_SCRIPT"
    cat > "$SERVER_SCRIPT" <<'PYEOF'
#!/usr/bin/env python3
"""
JoyCaption Beta One - OpenAI 兼容 API Server
接口: POST /v1/chat/completions
      GET  /v1/models
"""
import argparse
import base64
import io
import os
import time
import uuid
from contextlib import asynccontextmanager
from typing import List, Optional, Union

import torch
from PIL import Image
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from transformers import (
    AutoProcessor,
    BitsAndBytesConfig,
    LlavaForConditionalGeneration,
    TextIteratorStreamer,
)
from threading import Thread
import uvicorn

# ---- 全局模型对象 ----
_model = None
_processor = None
_args = None


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-path", required=True)
    parser.add_argument("--host",       default="0.0.0.0")
    parser.add_argument("--port",       type=int, default=8803)
    parser.add_argument("--quant",      type=int, default=0, choices=[0, 4, 8],
                        help="量化位数: 0=bfloat16, 4=INT4, 8=INT8")
    return parser.parse_args()


def build_quant_config(quant: int) -> Optional[BitsAndBytesConfig]:
    if quant == 4:
        return BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_quant_storage=torch.bfloat16,
            bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.bfloat16,
        )
    if quant == 8:
        return BitsAndBytesConfig(load_in_8bit=True)
    return None


def _fix_vision_tower_out_proj(model):
    """https://github.com/fpgaminer/joycaption/issues/3#issuecomment-2619253277"""
    attn = model.vision_tower.vision_model.head.attention
    attn.out_proj = torch.nn.Linear(
        attn.embed_dim, attn.embed_dim,
        device=model.device, dtype=torch.bfloat16,
    )
    print("[INFO] 已修复 vision_tower out_proj 量化兼容问题")


def load_model(model_path: str, quant: int):
    global _model, _processor
    print(f"[INFO] 加载模型: {model_path}，量化: {quant}bit" if quant else f"[INFO] 加载模型: {model_path}，bfloat16")

    _processor = AutoProcessor.from_pretrained(model_path)

    quant_cfg = build_quant_config(quant)
    load_kwargs = dict(
        pretrained_model_name_or_path=model_path,
        torch_dtype="bfloat16",
        device_map=0,
    )
    if quant_cfg is not None:
        load_kwargs["quantization_config"] = quant_cfg

    _model = LlavaForConditionalGeneration.from_pretrained(**load_kwargs)
    if quant > 0:
        _fix_vision_tower_out_proj(_model)
    _model.eval()
    print("[INFO] 模型加载完成")


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_model(_args.model_path, _args.quant)
    yield


app = FastAPI(title="JoyCaption API", lifespan=lifespan)


# ---- Pydantic 模型 ----
class ImageUrl(BaseModel):
    url: str


class ContentPart(BaseModel):
    type: str
    text: Optional[str] = None
    image_url: Optional[ImageUrl] = None


class Message(BaseModel):
    role: str
    content: Union[str, List[ContentPart]]


class ChatRequest(BaseModel):
    model: Optional[str] = "joycaption"
    messages: List[Message]
    max_tokens: Optional[int] = 1024
    temperature: Optional[float] = 0.6
    top_p: Optional[float] = 0.9
    stream: Optional[bool] = False


# ---- 工具函数 ----
def decode_image(url: str) -> Image.Image:
    """从 data URI 或 URL 解码图片"""
    if url.startswith("data:"):
        header, data = url.split(",", 1)
        return Image.open(io.BytesIO(base64.b64decode(data))).convert("RGB")
    raise HTTPException(status_code=400, detail="仅支持 base64 data URI 格式的图片")


def extract_image_and_text(messages: List[Message]):
    """从消息列表中提取最后一条用户消息的图片和文本"""
    image = None
    prompt_text = ""
    for msg in reversed(messages):
        if msg.role != "user":
            continue
        if isinstance(msg.content, str):
            prompt_text = msg.content
        else:
            parts_text = []
            for part in msg.content:
                if part.type == "image_url" and part.image_url:
                    image = decode_image(part.image_url.url)
                elif part.type == "text" and part.text:
                    parts_text.append(part.text)
            prompt_text = " ".join(parts_text)
        break
    return image, prompt_text


def build_inputs(image: Optional[Image.Image], prompt_text: str):
    """按官方方式构建模型输入：chat template 自动处理图片 token"""
    convo = [
        {"role": "system", "content": "You are a helpful image captioner."},
        {"role": "user", "content": prompt_text},
    ]
    convo_string = _processor.apply_chat_template(convo, tokenize=False, add_generation_prompt=True)
    if image is not None:
        inputs = _processor(text=[convo_string], images=[image], return_tensors="pt").to(_model.device)
        inputs["pixel_values"] = inputs["pixel_values"].to(torch.bfloat16)
    else:
        inputs = _processor(text=[convo_string], return_tensors="pt").to(_model.device)
    return inputs


def make_choice(content: str, finish_reason: str = "stop") -> dict:
    return {
        "index": 0,
        "message": {"role": "assistant", "content": content},
        "finish_reason": finish_reason,
    }


def make_response(content: str, req_id: str) -> dict:
    return {
        "id": f"chatcmpl-{req_id}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": "joycaption",
        "choices": [make_choice(content)],
    }


# ---- 路由 ----
@app.get("/v1/models")
def list_models():
    return {
        "object": "list",
        "data": [{"id": "joycaption", "object": "model", "created": 0, "owned_by": "fancyfeast"}],
    }


@app.post("/v1/chat/completions")
async def chat_completions(req: ChatRequest):
    if _model is None:
        raise HTTPException(status_code=503, detail="模型未加载")

    image, prompt_text = extract_image_and_text(req.messages)
    if not prompt_text:
        raise HTTPException(status_code=400, detail="消息中缺少文本内容")

    inputs = build_inputs(image, prompt_text)
    gen_kwargs = dict(
        **inputs,
        max_new_tokens=req.max_tokens or 1024,
        do_sample=(req.temperature or 0) > 0,
        suppress_tokens=None,
        use_cache=True,
        temperature=req.temperature or 1.0,
        top_p=req.top_p or 1.0,
    )
    req_id = uuid.uuid4().hex

    if req.stream:
        return _stream_response(gen_kwargs, req_id)
    return _sync_response(gen_kwargs, req_id, inputs)


def _sync_response(gen_kwargs: dict, req_id: str, inputs: dict) -> dict:
    with torch.no_grad():
        output_ids = _model.generate(**gen_kwargs)
    input_len = inputs["input_ids"].shape[1]
    new_tokens = output_ids[0][input_len:]
    content = _processor.tokenizer.decode(new_tokens, skip_special_tokens=True, clean_up_tokenization_spaces=False).strip()
    return make_response(content, req_id)


def _stream_response(gen_kwargs: dict, req_id: str) -> StreamingResponse:
    streamer = TextIteratorStreamer(_processor.tokenizer, skip_prompt=True, skip_special_tokens=True)
    gen_kwargs["streamer"] = streamer

    def generate():
        Thread(target=_model.generate, kwargs=gen_kwargs, daemon=True).start()
        for chunk in streamer:
            data = {
                "id": f"chatcmpl-{req_id}",
                "object": "chat.completion.chunk",
                "created": int(time.time()),
                "model": "joycaption",
                "choices": [{"index": 0, "delta": {"content": chunk}, "finish_reason": None}],
            }
            yield f"data: {__import__('json').dumps(data, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")


if __name__ == "__main__":
    _args = parse_args()
    uvicorn.run(app, host=_args.host, port=_args.port)
PYEOF
    chmod +x "$SERVER_SCRIPT"
    # 将配置变量替换到脚本中（仅更新默认端口）
    sed -i "s|default=8804|default=$API_PORT|" "$SERVER_SCRIPT"
    info "API Server 脚本生成完成"
}

# -------------------- 启动服务（后台） --------------------
start_server() {
    mkdir -p "$MODEL_DIR"
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        warn "服务已在运行 (PID: $(cat "$PID_FILE"))，请先停止"
        return
    fi

    [ -f "$SERVER_SCRIPT" ] || error "未找到 API Server 脚本，请先执行: $0 install"

    info "启动 JoyCaption API 服务..."
    info "  量化模式: $([ $QUANT -eq 0 ] && echo '无(bfloat16)' || echo "${QUANT}bit")"
    info "  监听地址: http://$API_HOST:$API_PORT"
    info "  日志文件: $LOG_FILE"

    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        python3 "$SERVER_SCRIPT" \
            --model-path "$MODEL_FILES_DIR" \
            --host "$API_HOST" \
            --port "$API_PORT" \
            --quant "$QUANT" \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已后台启动，PID: $PID"
    info "模型加载中，请等待 1-3 分钟，查看进度: $0 logs"
}

# -------------------- 前台启动（调试） --------------------
start_foreground() {
    [ -f "$SERVER_SCRIPT" ] || error "未找到 API Server 脚本，请先执行: $0 install"
    info "前台启动 JoyCaption（Ctrl+C 停止）..."
    info "  量化模式: $([ $QUANT -eq 0 ] && echo '无(bfloat16)' || echo "${QUANT}bit")"
    conda_run python3 "$SERVER_SCRIPT" \
        --model-path "$MODEL_FILES_DIR" \
        --host "$API_HOST" \
        --port "$API_PORT" \
        --quant "$QUANT"
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
    pkill -f "api_server.py.*joycaption" 2>/dev/null \
        && info "服务已停止" \
        || warn "未找到运行中的服务"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "JoyCaption 服务正在运行"
        info "  PID: $(cat "$PID_FILE")"
        info "  API: http://localhost:$API_PORT/v1/chat/completions"
        info "  日志: $LOG_FILE"
    else
        warn "JoyCaption 服务未运行"
    fi
}

# -------------------- 测试 API --------------------
test_api() {
    info "测试 API 连通性（本地生成测试图，无需外网）..."

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
            \"model\": \"joycaption\",
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

    local SERVICE_FILE="/etc/systemd/system/joycaption.service"
    local QUANT_PARAM=""
    [ "$QUANT" -gt 0 ] && QUANT_PARAM="--quant $QUANT"

    info "创建 systemd 服务: $SERVICE_FILE"
    cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=JoyCaption Beta One API Service
After=network.target

[Service]
Type=simple
User=root
Environment=PATH=$(dirname "$ENV_PYTHON"):/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin
ExecStart=$ENV_PYTHON $SERVER_SCRIPT --model-path $MODEL_FILES_DIR --host $API_HOST --port $API_PORT $QUANT_PARAM
Restart=on-failure
RestartSec=15
StandardOutput=append:$LOG_FILE
StandardError=append:$LOG_FILE

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable joycaption.service
    info "开机自启已启用"
    info "  手动控制: systemctl start/stop/status joycaption"
}

disable_autostart() {
    if systemctl is-enabled joycaption.service &>/dev/null; then
        systemctl disable joycaption.service
        rm -f /etc/systemd/system/joycaption.service
        systemctl daemon-reload
        info "开机自启已禁用"
    else
        warn "未找到 joycaption systemd 服务"
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
    echo "  install           - 安装依赖、生成 Server 脚本并下载模型（首次使用）"
    echo "  download-model    - 单独下载/续传模型文件"
    echo "  start             - 后台启动 API 服务"
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
    echo "  QUANT=$QUANT      量化级别: 0=bfloat16(~16GB), 4=INT4(~6GB), 8=INT8(~10GB)"
    echo "  API_PORT=$API_PORT    服务端口"
    echo ""
    echo "API 示例（带图片）:"
    echo "  curl -X POST http://localhost:$API_PORT/v1/chat/completions \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '{\"model\":\"joycaption\",\"messages\":[{\"role\":\"user\",\"content\":["
    echo "      {\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,<BASE64>\"}}"
    echo "      {\"type\":\"text\",\"text\":\"Describe this image in detail.\"}]}]}'"
}

case "${1:-}" in
    install)
        check_prerequisites
        setup_directories
        setup_conda_env
        install_dependencies
        generate_server_script
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
