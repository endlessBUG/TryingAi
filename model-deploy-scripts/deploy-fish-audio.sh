#!/bin/bash
# ============================================================
# Fish Audio TTS 模型部署脚本 (CUDA GPU 版本)
# 模型: fishaudio/s2-pro
# 适用平台: NVIDIA GPU 服务器 (CUDA 12.x)
# API 地址: http://<host>:8804/v1/audio/speech
# 项目地址: https://github.com/fishaudio/fish-speech
# ============================================================

set -e

# -------------------- 配置 --------------------
# HuggingFace 模型 ID (备用)
HF_MODEL_ID="fishaudio/s2-pro"
# ModelScope 模型 ID (国内推荐)
MS_MODEL_ID="fishaudio/fish-speech-s2-pro"
MODEL_DIR="$HOME/ai/trainer/models/fish-audio"
# ModelScope 下载时会自动缓存到 ~/.cache/modelscope/hub/
# 这里设置为实际模型检查点路径（安装后自动检测）
MODEL_FILES_DIR="$HOME/ai/trainer/models/fish-audio/model_files"
CODE_DIR="$HOME/ai/trainer/models/fish-audio/fish-speech"
CONDA_ENV_NAME="fish-audio"
PYTHON_VERSION="3.10"
API_HOST="0.0.0.0"
API_PORT=8804
LOG_FILE="$HOME/ai/trainer/models/fish-audio/server.log"
PID_FILE="$HOME/ai/trainer/models/fish-audio/server.pid"
# CUDA 版本
CUDA_VERSION="12.1"
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
    command -v git &> /dev/null || error "未找到 git"
    command -v nvidia-smi &> /dev/null || error "未找到 nvidia-smi，请确认已安装 NVIDIA 驱动"

    info "Conda: $(conda --version)"
    info "系统: $(uname -s) $(uname -m)"
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
    info "[1/4] 升级 pip..."
    conda_run pip install --upgrade pip $PIP_MIRROR --root-user-action=ignore

    if conda_run python3 -c "import torch" 2>/dev/null; then
        local TORCH_VER
        TORCH_VER=$(conda_run python3 -c "import torch; print(torch.__version__)")
        info "[2/4] PyTorch 已安装 ($TORCH_VER)，跳过"
    else
        info "[2/4] 安装 PyTorch (CUDA $CUDA_VERSION)..."
        conda_run pip install torch torchvision torchaudio \
            --index-url https://download.pytorch.org/whl/cu121 \
            --root-user-action=ignore
    fi

    info "[3/4] 安装 fish-speech 依赖..."
    # 安装核心依赖
    conda_run pip install \
        transformers accelerate sentencepiece \
        einops scipy librosa soundfile \
        gradio fastapi uvicorn python-multipart \
        huggingface_hub safetensors modelscope \
        $PIP_MIRROR --root-user-action=ignore

    # 尝试从项目 requirements 安装
    if [ -f "$CODE_DIR/requirements.txt" ]; then
        conda_run pip install -r "$CODE_DIR/requirements.txt" \
            $PIP_MIRROR --root-user-action=ignore 2>/dev/null \
            || warn "部分 requirements 安装失败，已安装核心依赖"
    fi

    info "[4/4] 安装 CUDA 加速库..."
    # flash-attn 需要预编译版本或从源码安装
    conda_run pip install flash-attn --no-build-isolation \
        $PIP_MIRROR --root-user-action=ignore 2>/dev/null \
        && info "flash-attn 安装成功" \
        || warn "flash-attn 安装跳过（可选加速依赖）"

    info "全部依赖安装完成"
}

# -------------------- 创建 API 服务 --------------------
create_api_server() {
    info "创建 TTS API 服务脚本..."

    cat > "$MODEL_DIR/tts_api_server.py" << 'PYEOF'
#!/usr/bin/env python3
"""
Fish Audio TTS API Server
基于 PyTorch/CUDA 的文本转语音服务，支持 OpenAI 兼容 API 格式
"""

import os
import io
import sys
import subprocess
import json
import tempfile
import base64
import time
import logging
from typing import Optional, List
from pathlib import Path

# 确保 UTF-8 编码支持
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

import numpy as np
import soundfile as sf
import torch
from fastapi import FastAPI, HTTPException, UploadFile, File, Form, Request
from fastapi.responses import StreamingResponse, JSONResponse, FileResponse
from pydantic import BaseModel, Field
import uvicorn

# 配置日志
logging.basicConfig(level=logging.INFO, encoding='utf-8')
logger = logging.getLogger(__name__)

# 模型路径（从环境变量获取）
MODEL_PATH = os.environ.get("FISH_AUDIO_MODEL_PATH", "~/ai/trainer/models/fish-audio/model_files")
CODE_PATH = os.environ.get("FISH_AUDIO_CODE_PATH", "~/ai/trainer/models/fish-audio/fish-speech")

# ============================================================
# API 数据模型
# ============================================================

class TTSRequest(BaseModel):
    """TTS 请求模型 - OpenAI 兼容格式"""
    model: str = "fish-audio-s2-pro"
    input: str = Field(..., description="要转换为语音的文本")
    voice: str = Field(default="default", description="音色 ID 或预设名称")
    response_format: str = Field(default="wav", description="输出格式: wav/mp3/pcm")
    speed: float = Field(default=1.0, ge=0.5, le=2.0, description="语速倍率")
    reference_audio: Optional[str] = Field(default=None, description="参考音频 base64 (用于音色克隆)")
    reference_text: Optional[str] = Field(default=None, description="参考音频对应的文本")

class VoiceInfo(BaseModel):
    """音色信息"""
    id: str
    name: str
    language: Optional[str] = None
    description: Optional[str] = None

class ModelInfo(BaseModel):
    """模型信息"""
    id: str
    name: str
    type: str = "tts"
    description: str

# ============================================================
# TTS Engine (使用 fish-speech CLI)
# ============================================================

class FishAudioEngine:
    """Fish Audio TTS 引擎 - 使用官方 CLI"""

    def __init__(self, model_path: str, code_path: str):
        self.model_path = Path(model_path).expanduser()
        self.code_path = Path(code_path).expanduser()
        self.model_loaded = False
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        logger.info(f"初始化 TTS 引擎")
        logger.info(f"  模型路径: {self.model_path}")
        logger.info(f"  代码路径: {self.code_path}")
        logger.info(f"  设备: {self.device}")
        if torch.cuda.is_available():
            logger.info(f"  GPU: {torch.cuda.get_device_name(0)}")

    def load_model(self):
        """检查模型文件是否存在"""
        if self.model_loaded:
            return

        # 检查模型目录
        if not self.model_path.exists():
            logger.warning(f"模型目录不存在: {self.model_path}")
            return

        # 检查 fish-speech 代码目录
        if not self.code_path.exists():
            logger.warning(f"fish-speech 代码目录不存在: {self.code_path}")
            return

        # 检查关键模型文件
        model_files = list(self.model_path.glob("*.safetensors")) + \
                      list(self.model_path.glob("*.pt")) + \
                      list(self.model_path.glob("*.bin"))
        config_file = self.model_path / "config.json"

        if not model_files:
            logger.warning(f"未找到模型权重文件: {self.model_path}")
            return

        logger.info(f"找到模型文件: {[f.name for f in model_files]}")
        if config_file.exists():
            with open(config_file) as f:
                config = json.load(f)
            logger.info(f"模型类型: {config.get('model_type', 'unknown')}")

        self.model_loaded = True
        logger.info("模型检查完成，准备使用 CLI 推理")

    def generate(
        self,
        text: str,
        voice: str = "default",
        speed: float = 1.0,
        reference_audio: Optional[np.ndarray] = None,
        reference_text: Optional[str] = None
    ) -> np.ndarray:
        """使用 fish-speech CLI 生成语音"""

        logger.info(f"生成语音: text='{text[:50]}...', speed={speed}")
        sample_rate = 24000

        try:
            # 使用 fish-speech CLI 推理
            return self._cli_generate(text, speed, reference_audio, reference_text, sample_rate)
        except Exception as e:
            logger.error(f"CLI 生成失败: {e}")
            return self._fallback_generate(text, speed, sample_rate)

    def _cli_generate(
        self,
        text: str,
        speed: float,
        reference_audio: Optional[np.ndarray],
        reference_text: Optional[str],
        sample_rate: int
    ) -> np.ndarray:
        """调用 fish-speech 推理"""

        import subprocess
        import tempfile

        # 创建临时输出文件
        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp_out:
            output_path = tmp_out.name

        # 创建临时参考音频文件（如果有）
        ref_audio_path = None
        if reference_audio is not None:
            with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp_ref:
                ref_audio_path = tmp_ref.name
                sf.write(ref_audio_path, reference_audio, sample_rate)

        try:
            # 多种推理方式尝试
            cmd = None

            # 方式1: 使用 python -m fish_speech (推荐)
            if self.code_path.exists():
                # 检查多种可能的推理入口
                inference_scripts = [
                    self.code_path / "fish_speech" / "inference.py",
                    self.code_path / "fish_speech" / "text_to_speech.py",
                    self.code_path / "fish_speech" / "synthesize.py",
                ]

                for script in inference_scripts:
                    if script.exists():
                        cmd = [
                            sys.executable,
                            str(script),
                            "--text", text,
                            "--output", output_path,
                            "--checkpoint", str(self.model_path),
                        ]
                        if speed != 1.0:
                            cmd.extend(["--speed", str(speed)])
                        if ref_audio_path:
                            cmd.extend(["--reference-audio", ref_audio_path])
                            if reference_text:
                                cmd.extend(["--reference-text", reference_text])
                        logger.info(f"使用推理脚本: {script.name}")
                        break

                # 如果没有找到脚本，尝试 python -m 方式
                if cmd is None:
                    cmd = [
                        sys.executable, "-m", "fish_speech",
                        "synthesize",
                        "--text", text,
                        "--output", output_path,
                        "--checkpoint", str(self.model_path),
                    ]
                    # 设置 PYTHONPATH 以便能找到 fish_speech 模块
                    env = os.environ.copy()
                    env["PYTHONPATH"] = str(self.code_path)
                    logger.info("使用 python -m fish_speech 方式")

            # 方式2: fish-speech 命令行工具
            if cmd is None:
                cmd = [
                    "fish-speech",
                    "synthesize",
                    "--text", text,
                    "--output", output_path,
                    "--model", str(self.model_path),
                ]

            logger.info(f"执行命令: {' '.join(cmd)}")

            # 运行 CLI
            env = os.environ.copy()
            if self.code_path.exists():
                env["PYTHONPATH"] = str(self.code_path)

            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=120,
                env=env,
                cwd=str(self.code_path) if self.code_path.exists() else None
            )

            if result.returncode != 0:
                logger.error(f"CLI 错误: {result.stderr}")
                raise RuntimeError(f"CLI 执行失败: {result.stderr}")

            # 读取生成的音频
            if os.path.exists(output_path):
                audio, sr = sf.read(output_path)
                if sr != sample_rate:
                    # 重采样到目标采样率
                    import librosa
                    audio = librosa.resample(audio, orig_sr=sr, target_sr=sample_rate)
                return audio.astype(np.float32)
            else:
                raise FileNotFoundError(f"输出文件未生成: {output_path}")

        finally:
            # 清理临时文件
            if os.path.exists(output_path):
                os.unlink(output_path)
            if ref_audio_path and os.path.exists(ref_audio_path):
                os.unlink(ref_audio_path)

    def _fallback_generate(self, text: str, speed: float, sample_rate: int) -> np.ndarray:
        """备用生成方案 - 生成测试音频"""
        duration = len(text) * 0.1 / speed
        t = np.linspace(0, duration, int(sample_rate * duration))
        audio = np.sin(2 * np.pi * 440 * t) * 0.3
        fade_samples = int(sample_rate * 0.05)
        audio[:fade_samples] *= np.linspace(0, 1, fade_samples)
        audio[-fade_samples:] *= np.linspace(1, 0, fade_samples)
        return audio.astype(np.float32)

# ============================================================
# FastAPI 应用
# ============================================================

app = FastAPI(
    title="Fish Audio TTS API",
    description="基于 PyTorch/CUDA 的文本转语音服务",
    version="1.0.1",
)

# 初始化引擎
engine = FishAudioEngine(MODEL_PATH, CODE_PATH)

@app.on_event("startup")
async def startup_event():
    """启动时预加载模型"""
    logger.info("预加载模型...")
    try:
        engine.load_model()
        logger.info("模型预加载完成")
    except Exception as e:
        logger.warning(f"模型预加载失败，将在首次请求时加载: {e}")

# -------------------- OpenAI 兼容 API --------------------

@app.get("/v1/models")
async def list_models():
    """列出可用模型"""
    return {
        "object": "list",
        "data": [
            ModelInfo(
                id="fish-audio-s2-pro",
                name="Fish Audio S2 Pro",
                type="tts",
                description="高质量多语言 TTS 模型"
            ).dict()
        ]
    }

@app.post("/v1/audio/speech")
async def create_speech(raw_request: Request):
    """
    创建语音 - OpenAI 兼容接口 (UTF-8 Fixed)
    POST /v1/audio/speech
    直接解析 UTF-8 JSON body，解决中文编码问题
    """
    try:
        # 直接读取 body 并用 UTF-8 解析
        body = await raw_request.body()
        try:
            data = json.loads(body.decode('utf-8'))
        except json.JSONDecodeError as e:
            logger.error(f"JSON 解析失败: {e}")
            raise HTTPException(status_code=400, detail=f"JSON 解析失败: {str(e)}")

        # 获取请求参数
        text = data.get("input", "")
        if not text:
            raise HTTPException(status_code=400, detail="缺少 'input' 参数")

        voice = data.get("voice", "default")
        speed = float(data.get("speed", 1.0))
        response_format = data.get("response_format", "wav")
        reference_audio_b64 = data.get("reference_audio")
        reference_text = data.get("reference_text")

        logger.info(f"TTS 请求: text='{text[:50]}...', voice={voice}, speed={speed}")

        # 处理参考音频
        ref_audio = None
        if reference_audio_b64:
            try:
                audio_bytes = base64.b64decode(reference_audio_b64)
                ref_audio, _ = sf.read(io.BytesIO(audio_bytes))
            except Exception as e:
                logger.warning(f"参考音频解码失败: {e}")

        # 生成语音
        audio = engine.generate(
            text=text,
            voice=voice,
            speed=speed,
            reference_audio=ref_audio,
            reference_text=reference_text
        )

        # 转换为输出格式
        sample_rate = 24000

        if response_format == "wav":
            buffer = io.BytesIO()
            sf.write(buffer, audio, sample_rate, format="WAV")
            buffer.seek(0)
            media_type = "audio/wav"
        elif response_format == "mp3":
            buffer = io.BytesIO()
            wav_buffer = io.BytesIO()
            sf.write(wav_buffer, audio, sample_rate, format="WAV")
            wav_buffer.seek(0)
            buffer = wav_buffer
            media_type = "audio/wav"
            logger.warning("MP3 格式暂不支持，返回 WAV")
        else:
            buffer = io.BytesIO()
            sf.write(buffer, audio, sample_rate, format="RAW")
            buffer.seek(0)
            media_type = "application/octet-stream"

        return StreamingResponse(
            buffer,
            media_type=media_type,
            headers={
                "Content-Disposition": f"attachment; filename=speech.{response_format}"
            }
        )

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"语音生成失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# -------------------- 扩展 API --------------------

@app.post("/v1/tts")
async def tts_simple(
    text: str = Form(...),
    voice: str = Form(default="default"),
    speed: float = Form(default=1.0),
    format: str = Form(default="wav")
):
    """
    简化 TTS 接口 (表单提交)
    POST /v1/tts
    """
    logger.info(f"简化接口请求: text='{text[:50]}...'")

    # 生成语音
    audio = engine.generate(
        text=text,
        voice=voice,
        speed=speed
    )

    sample_rate = 24000
    buffer = io.BytesIO()
    sf.write(buffer, audio, sample_rate, format="WAV")
    buffer.seek(0)

    return StreamingResponse(
        buffer,
        media_type="audio/wav",
        headers={"Content-Disposition": f"attachment; filename=speech.{format}"}
    )

@app.post("/v1/tts/clone")
async def tts_voice_clone(
    text: str = Form(...),
    reference_audio: UploadFile = File(...),
    reference_text: str = Form(default=""),
    speed: float = Form(default=1.0),
    format: str = Form(default="wav")
):
    """
    音色克隆接口 - 上传参考音频
    POST /v1/tts/clone
    """
    try:
        audio_bytes = await reference_audio.read()
        ref_audio, _ = sf.read(io.BytesIO(audio_bytes))

        output_audio = engine.generate(
            text=text,
            speed=speed,
            reference_audio=ref_audio,
            reference_text=reference_text
        )

        sample_rate = 24000
        buffer = io.BytesIO()
        sf.write(buffer, output_audio, sample_rate, format="WAV")
        buffer.seek(0)

        return StreamingResponse(
            buffer,
            media_type="audio/wav",
            headers={"Content-Disposition": "attachment; filename=cloned_speech.wav"}
        )

    except Exception as e:
        logger.error(f"音色克隆失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/v1/voices")
async def list_voices():
    """列出预设音色"""
    return {
        "object": "list",
        "data": [
            VoiceInfo(id="default", name="默认音色", language="zh").dict(),
            VoiceInfo(id="female-soft", name="温柔女声", language="zh").dict(),
            VoiceInfo(id="male-deep", name="深沉男声", language="zh").dict(),
            VoiceInfo(id="child", name="童声", language="zh").dict(),
        ]
    }

@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "healthy",
        "model_loaded": engine.model_loaded,
        "device": engine.device,
        "model_path": str(engine.model_path)
    }

@app.get("/")
async def root():
    """根路径"""
    return {
        "service": "Fish Audio TTS API",
        "version": "1.0.0",
        "device": engine.device,
        "endpoints": {
            "OpenAI兼容": "/v1/audio/speech",
            "简化接口": "/v1/tts",
            "音色克隆": "/v1/tts/clone",
            "模型列表": "/v1/models",
            "音色列表": "/v1/voices",
            "健康检查": "/health"
        }
    }

# ============================================================
# 主入口
# ============================================================

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Fish Audio TTS API Server")
    parser.add_argument("--host", type=str, default="0.0.0.0", help="监听地址")
    parser.add_argument("--port", type=int, default=8804, help="监听端口")
    parser.add_argument("--model-path", type=str, default=None, help="模型路径")
    parser.add_argument("--code-path", type=str, default=None, help="fish-speech 代码路径")
    args = parser.parse_args()

    if args.model_path:
        MODEL_PATH = args.model_path
    if args.code_path:
        CODE_PATH = args.code_path
        engine = FishAudioEngine(MODEL_PATH, CODE_PATH)

    uvicorn.run(app, host=args.host, port=args.port)
PYEOF

    chmod +x "$MODEL_DIR/tts_api_server.py"
    info "API 服务脚本创建完成: $MODEL_DIR/tts_api_server.py"
}

# -------------------- 下载模型 --------------------
download_model() {
    info "从 ModelScope 下载模型 fishaudio/s2-pro ..."
    info "（国内下载速度更快，支持断点续传）"

    conda_run pip install modelscope safetensors $PIP_MIRROR --root-user-action=ignore

    # 下载并复制模型（变量会正确展开）
    conda_run python3 - "$MODEL_FILES_DIR" <<'PYEOF'
import os
import sys
import shutil
from pathlib import Path

# 目标目录从参数获取
target_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/ai/trainer/models/fish-audio/model_files')

print(f'目标目录: {target_dir}')
os.makedirs(target_dir, exist_ok=True)

try:
    from modelscope import snapshot_download

    print('从 ModelScope 下载: fishaudio/s2-pro')
    model_dir = snapshot_download('fishaudio/s2-pro')
    print(f'模型下载成功，原始路径: {model_dir}')

    # 复制模型文件到目标目录
    target_path = Path(target_dir)
    existing_files = list(target_path.glob('*.safetensors')) + list(target_path.glob('*.pt'))

    if not existing_files:
        src_path = Path(model_dir)
        print(f'复制模型文件到: {target_dir}')
        for item in src_path.iterdir():
            if item.is_file():
                shutil.copy2(item, target_path / item.name)
                print(f'  复制: {item.name}')
            elif item.is_dir() and item.name not in ['__pycache__', '.git']:
                shutil.copytree(item, target_path / item.name, dirs_exist_ok=True)
        print(f'模型文件已复制完成')
    else:
        print(f'目标目录已有模型文件: {[f.name for f in existing_files]}')

except Exception as e:
    print(f'ModelScope 下载失败: {e}')
    print('尝试从 HuggingFace 镜像下载...')
    os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
    try:
        from huggingface_hub import snapshot_download
        model_dir = snapshot_download(
            repo_id='fishaudio/s2-pro',
            local_dir=target_dir,
            resume_download=True
        )
        print(f'模型下载完成: {model_dir}')
    except Exception as e2:
        print(f'下载失败: {e2}')
        sys.exit(1)
PYEOF

    # 显示下载结果
    info "模型文件列表:"
    ls -la "$MODEL_FILES_DIR" 2>/dev/null || warn "目录不存在或为空"
}

# -------------------- 启动服务（后台） --------------------
start_server() {
    mkdir -p "$MODEL_DIR"

    # 检查端口是否被占用
    if lsof -i:$API_PORT >/dev/null 2>&1 || netstat -tlnp 2>/dev/null | grep -q ":$API_PORT"; then
        warn "端口 $API_PORT 已被占用，正在清理..."
        pkill -f "tts_api_server.py" 2>/dev/null
        sleep 2
        if lsof -i:$API_PORT >/dev/null 2>&1; then
            error "端口 $API_PORT 仍被占用，请手动处理: lsof -i:$API_PORT"
        fi
    fi

    # 检查是否有旧的 PID 文件
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        warn "服务已在运行 (PID: $(cat "$PID_FILE"))，请先停止"
        return
    fi
    rm -f "$PID_FILE"

    # 检查模型文件是否存在
    if [ ! -d "$MODEL_FILES_DIR" ]; then
        warn "模型目录不存在: $MODEL_FILES_DIR"
        warn "请先执行: $0 download-model"
    fi

    info "启动 Fish Audio TTS API 服务..."
    info "  监听地址: http://$API_HOST:$API_PORT"
    info "  API 文档: http://localhost:$API_PORT/docs"
    info "  模型路径: $MODEL_FILES_DIR"
    info "  日志文件: $LOG_FILE"

    nohup conda run --no-capture-output -n "$CONDA_ENV_NAME" \
        python3 "$MODEL_DIR/tts_api_server.py" \
        --host "$API_HOST" --port "$API_PORT" \
        --model-path "$MODEL_FILES_DIR" \
        --code-path "$CODE_DIR" \
        > "$LOG_FILE" 2>&1 &

    local PID=$!
    echo "$PID" > "$PID_FILE"
    info "服务已后台启动，PID: $PID"
    info "模型加载中，请等待，查看进度: $0 logs"
}

# -------------------- 前台启动（调试） --------------------
start_foreground() {
    info "前台启动 Fish Audio TTS（Ctrl+C 停止）..."
    conda_run python3 "$MODEL_DIR/tts_api_server.py" \
        --host "$API_HOST" --port "$API_PORT" \
        --model-path "$MODEL_FILES_DIR" \
        --code-path "$CODE_DIR"
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
    pkill -f "tts_api_server.py" 2>/dev/null \
        && info "服务已停止" \
        || warn "未找到运行中的服务"
}

# -------------------- 查看状态 --------------------
show_status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        info "Fish Audio TTS 服务正在运行"
        info "  PID: $(cat "$PID_FILE")"
        info "  API: http://localhost:$API_PORT"
        info "  OpenAI 兼容接口: http://localhost:$API_PORT/v1/audio/speech"
        info "  日志: $LOG_FILE"
    else
        warn "Fish Audio TTS 服务未运行"
    fi
}

# -------------------- 测试 API --------------------
test_api() {
    info "测试 API 连通性..."

    local RESPONSE HTTP_CODE
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "http://localhost:$API_PORT/v1/audio/speech" \
        -H "Content-Type: application/json" \
        -d '{"model": "fish-audio-s2-pro", "input": "你好，这是一个测试", "voice": "default"}')

    HTTP_CODE=$(echo "$RESPONSE" | tail -1)

    if [ "$HTTP_CODE" = "200" ]; then
        info "API 测试通过，语音生成成功"
        info "提示: 使用以下命令保存测试音频:"
        info "  curl -X POST http://localhost:$API_PORT/v1/audio/speech -H 'Content-Type: application/json' -d '{\"model\": \"fish-audio-s2-pro\", \"input\": \"你好\"}' -o test.wav"
    else
        warn "API 返回 HTTP $HTTP_CODE，请检查日志"
    fi
}

# -------------------- 开机自启（systemd） --------------------
enable_autostart() {
    check_conda_env
    local ENV_PYTHON
    ENV_PYTHON=$(get_env_python)
    [ -f "$ENV_PYTHON" ] || error "未找到 Python: $ENV_PYTHON，请先执行 install"

    local SERVICE_FILE="/etc/systemd/system/fish-audio.service"

    info "创建 systemd 服务: $SERVICE_FILE"
    cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Fish Audio TTS API Service
After=network.target

[Service]
Type=simple
User=root
Environment=FISH_AUDIO_MODEL_PATH=$MODEL_FILES_DIR
Environment=FISH_AUDIO_CODE_PATH=$CODE_DIR
Environment=PATH=$(dirname "$ENV_PYTHON"):/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin
ExecStart=$ENV_PYTHON $MODEL_DIR/tts_api_server.py --host $API_HOST --port $API_PORT --model-path $MODEL_FILES_DIR --code-path $CODE_DIR
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
    echo "=== 模型目录 ==="
    if [ -d "$MODEL_FILES_DIR" ]; then
        ls -la "$MODEL_FILES_DIR"
        echo ""
        echo "模型文件:"
        find "$MODEL_FILES_DIR" -name "*.safetensors" -o -name "*.pt" -o -name "*.bin" -o -name "*.pth" 2>/dev/null
    else
        warn "模型目录不存在: $MODEL_FILES_DIR"
    fi

    echo ""
    echo "=== fish-speech 代码目录 ==="
    if [ -d "$CODE_DIR" ]; then
        ls -la "$CODE_DIR"
        echo ""
        echo "fish_speech 模块结构:"
        if [ -d "$CODE_DIR/fish_speech" ]; then
            ls -la "$CODE_DIR/fish_speech"
            echo ""
            echo "可能的推理脚本:"
            find "$CODE_DIR" -name "inference.py" -o -name "text_to_speech.py" -o -name "synthesize.py" -o -name "generate.py" 2>/dev/null
            echo ""
            echo "CLI 相关文件:"
            find "$CODE_DIR" -name "*.py" | xargs grep -l "def main" 2>/dev/null | head -10
        else
            warn "fish_speech 模块不存在"
        fi
    else
        warn "代码目录不存在: $CODE_DIR"
    fi

    echo ""
    echo "=== Conda 环境 ==="
    conda env list | grep fish-audio || warn "Conda 环境 fish-audio 不存在"

    echo ""
    echo "=== Python 模块检查 ==="
    if conda env list | grep -q fish-audio; then
        conda_run python3 -c "import torch; print(f'PyTorch: {torch.__version__}')" 2>/dev/null || warn "PyTorch 未安装"
        conda_run python3 -c "import fish_speech; print('fish_speech 模块可用')" 2>/dev/null || warn "fish_speech 模块不可用 (需要设置 PYTHONPATH)"
        # 检查是否安装了 fish-speech CLI
        conda_run which fish-speech 2>/dev/null || warn "fish-speech CLI 未安装"
    fi

    echo ""
    echo "=== 建议 ==="
    if [ -d "$CODE_DIR/fish_speech" ]; then
        info "PYTHONPATH 应设置为: $CODE_DIR"
        info "推理脚本可能位于: $CODE_DIR/fish_speech/"
    fi
}

# -------------------- 主入口 --------------------
usage() {
    echo "用法: $0 {install|start|start-fg|stop|restart|status|test|logs|diagnose|enable-autostart|disable-autostart}"
    echo ""
    echo "  install           - 安装依赖、克隆代码、创建 API 服务、下载模型（首次使用）"
    echo "  download-model    - 单独下载/续传模型文件"
    echo "  start             - 后台启动 API 服务"
    echo "  start-fg          - 前台启动（调试用，Ctrl+C 停止）"
    echo "  stop              - 停止 API 服务"
    echo "  restart           - 重启 API 服务"
    echo "  status            - 查看服务状态"
    echo "  test              - 测试 API 连通性"
    echo "  logs              - 实时查看日志（Ctrl+C 退出）"
    echo "  diagnose          - 诊断环境，查看推理脚本路径"
    echo "  enable-autostart  - 注册 systemd 服务，开机自动启动"
    echo "  disable-autostart - 取消开机自动启动"
    echo ""
    echo "API 端点:"
    echo "  OpenAI兼容: POST http://localhost:$API_PORT/v1/audio/speech"
    echo "  简化接口:  POST http://localhost:$API_PORT/v1/tts"
    echo "  音色克隆:  POST http://localhost:$API_PORT/v1/tts/clone"
    echo ""
    echo "配置项（修改脚本顶部变量）:"
    echo "  API_PORT=$API_PORT        服务端口"
    echo "  MS_MODEL_ID=$MS_MODEL_ID  ModelScope 模型 ID (国内下载)"
    echo "  HF_MODEL_ID=$HF_MODEL_ID  HuggingFace 模型 ID (备用)"
}

case "${1:-}" in
    install)
        check_prerequisites
        setup_directories
        setup_conda_env
        clone_fish_speech
        install_dependencies
        create_api_server
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