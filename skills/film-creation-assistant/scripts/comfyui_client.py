#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
影视内容生成客户端
支持 ComfyUI 7种模型 + FishAudio TTS，共8种服务

使用方法:
    python comfyui_client.py --service image --prompt "一只可爱的猫咪"
    python comfyui_client.py --service video --prompt "美女在海边散步" --duration 9
    python comfyui_client.py --service camera_control --prompt "镜头推进" --image input.png --camera-pose "Zoom In"
    python comfyui_client.py --service text_to_speech --prompt "爱我" --tts-url http://127.0.0.1:8080 --output speech.wav
"""

import sys
import os

# 修复 Windows 终端编码问题
if sys.platform == 'win32':
    os.environ['PYTHONIOENCODING'] = 'utf-8'

import json
import time
import argparse
import base64
import requests
import urllib.parse
from pathlib import Path
from typing import Optional, Dict, Any, Tuple

class ComfyUIClient:
    """ComfyUI API 客户端"""

    DEFAULT_NEGATIVE_PROMPT = """色调艳丽，过曝，静态，细节模糊不清，字幕，风格，作品，画作，画面，
静止，整体发灰，最差质量，低质量，JPEG压缩残留，丑陋的，残缺的，
多余的手指，画得不好的手部，画得不好的脸部，畸形的，毁容的，
形态畸形的肢体，手指融合，静止不动的画面，杂乱的背景，三条腿，
背景人很多，倒着走"""

    WORKFLOW_FILES = {
        'image': 'z_image_turbo.json',
        'image_to_image': 'flux2_klein_9b_kv_i2i.json',
        'video': 'wan22_t2v.json',
        'video_frame': 'wan22_flf2v.json',
        'sound_to_video': 'wan22_s2v.json',
        'camera_control': 'wan22_camera.json',
        'video_control': 'wan22_fun_control.json',
    }

    TIMEOUTS = {
        'image': 300,      # 5分钟
        'image_to_image': 300,
        'video': 1800,     # 30分钟
        'video_frame': 1800,
        'sound_to_video': 1800,
        'camera_control': 1800,
        'video_control': 1800,
    }

    POLL_INTERVALS = {
        'image': 2,
        'image_to_image': 2,
        'video': 5,
        'video_frame': 5,
        'sound_to_video': 5,
        'camera_control': 5,
        'video_control': 5,
    }

    CAMERA_POSES = [
        'Zoom In', 'Zoom Out', 'Pan Left', 'Pan Right',
        'Tilt Up', 'Tilt Down', 'Clockwise', 'Anticlockwise', 'Static'
    ]

    def __init__(self, base_url: str = "http://127.0.0.1:8188", workflow_dir: str = None):
        self.base_url = base_url.rstrip('/')
        # 默认工作流目录为脚本所在目录的 ../workflows/
        if workflow_dir:
            self.workflow_dir = Path(workflow_dir)
        else:
            self.workflow_dir = Path(__file__).parent.parent / 'workflows'
        self.client_id = f"client_{int(time.time())}"

    def test_connection(self) -> bool:
        """测试连接"""
        try:
            resp = requests.get(f"{self.base_url}/api/system_stats", timeout=10)
            return resp.status_code == 200
        except Exception as e:
            print(f"连接错误: {e}")
            return False

    def upload_file(self, file_path: str, overwrite: bool = True) -> Tuple[str, str]:
        """上传文件，返回 (filename, subfolder)"""
        path = Path(file_path)
        if not path.exists():
            raise FileNotFoundError(f"文件不存在: {file_path}")

        # 根据文件扩展名判断类型
        ext = path.suffix.lower()
        if ext in ['.png', '.jpg', '.jpeg', '.webp', '.gif']:
            mime_type = 'image/png'
        elif ext in ['.mp4', '.webm', '.mov', '.avi']:
            mime_type = 'video/mp4'
        elif ext in ['.wav', '.mp3', '.m4a']:
            mime_type = 'audio/wav'
        else:
            mime_type = 'application/octet-stream'

        with open(path, 'rb') as f:
            files = {'image': (path.name, f, mime_type)}
            data = {'overwrite': 'true' if overwrite else 'false'}
            resp = requests.post(
                f"{self.base_url}/upload/image",
                files=files,
                data=data,
                timeout=120
            )

        if resp.status_code != 200:
            raise Exception(f"上传失败: {resp.text}")

        result = resp.json()
        return result['name'], result.get('subfolder', '')

    def load_workflow(self, service_type: str, frames: int = None) -> Dict:
        """加载工作流模板"""
        filename = self.WORKFLOW_FILES.get(service_type)
        if not filename:
            raise ValueError(f"未知的服务类型: {service_type}")

        path = self.workflow_dir / filename
        if not path.exists():
            raise FileNotFoundError(f"工作流文件不存在: {path}")

        # 先读取为字符串，替换数值类型占位符后再解析JSON
        with open(path, 'r', encoding='utf-8') as f:
            content = f.read()

        # 数值类型占位符替换（这些在JSON中是数字，不是字符串）
        # SEED 使用时间戳保证唯一性
        content = content.replace('{{SEED}}', str(int(time.time())))
        content = content.replace('{{STEPS}}', '4')
        content = content.replace('{{STEPS_HALF}}', '2')
        content = content.replace('{{WIDTH}}', '640')
        content = content.replace('{{HEIGHT}}', '640')
        # FRAMES 根据参数动态替换，默认81帧（约5秒）
        actual_frames = frames if frames is not None else 81
        content = content.replace('{{FRAMES}}', str(actual_frames))

        # 字符串类型占位符保留，由 replace_placeholders 方法处理
        # 注意：不在这里替换 {{PROMPT}}, {{IMAGE}} 等，因为它们需要在运行时根据用户输入替换

        return json.loads(content)

    def replace_placeholders(self, workflow: Dict, params: Dict) -> Dict:
        """替换占位符"""
        # 确保 steps 有有效值
        steps = params.get('steps') or 4

        replacements = {
            '{{PROMPT}}': params.get('prompt', ''),
            '{{NEGATIVE_PROMPT}}': params.get('negativePrompt', self.DEFAULT_NEGATIVE_PROMPT),
            '{{WIDTH}}': str(params.get('width') or (1024 if 'image' in params.get('service_type', '') else 640)),
            '{{HEIGHT}}': str(params.get('height') or (1024 if 'image' in params.get('service_type', '') else 640)),
            '{{FRAMES}}': str(params.get('frames') or 81),
            '{{SEED}}': str(params.get('seed') or int(time.time())),
            '{{STEPS}}': str(steps),
            '{{STEPS_HALF}}': str(steps // 2),
            '{{IMAGE}}': params.get('image', ''),
            '{{AUDIO}}': params.get('audio', ''),
            '{{VIDEO}}': params.get('video', ''),
            '{{FIRST_FRAME}}': params.get('firstFrame', ''),
            '{{LAST_FRAME}}': params.get('lastFrame', ''),
            '{{CAMERA_POSE}}': params.get('cameraPose', 'Zoom In'),
        }

        def replace_recursive(obj):
            if isinstance(obj, str):
                for key, value in replacements.items():
                    obj = obj.replace(key, str(value))
                return obj
            elif isinstance(obj, dict):
                return {k: replace_recursive(v) for k, v in obj.items()}
            elif isinstance(obj, list):
                return [replace_recursive(item) for item in obj]
            return obj

        return replace_recursive(workflow)

    def submit_prompt(self, workflow: Dict) -> str:
        """提交工作流，返回 prompt_id"""
        payload = {
            "client_id": self.client_id,
            "prompt": workflow,
            "extra_data": {}
        }
        resp = requests.post(
            f"{self.base_url}/api/prompt",
            json=payload,
            timeout=30
        )

        if resp.status_code != 200:
            raise Exception(f"提交失败: {resp.text}")

        return resp.json()['prompt_id']

    def wait_for_completion(self, prompt_id: str, service_type: str) -> Dict:
        """等待完成，返回 outputs"""
        timeout = self.TIMEOUTS.get(service_type, 300)
        interval = self.POLL_INTERVALS.get(service_type, 2)
        start_time = time.time()

        print(f"等待任务完成... (超时: {timeout}秒, 轮询间隔: {interval}秒)")

        while time.time() - start_time < timeout:
            try:
                resp = requests.get(
                    f"{self.base_url}/api/history/{prompt_id}",
                    timeout=30
                )
                history = resp.json()

                if prompt_id in history:
                    status = history[prompt_id].get('status', {})

                    # 检查是否有错误
                    if status.get('status_str') == 'error':
                        messages = status.get('messages', {})
                        raise Exception(f"任务执行错误: {messages}")

                    if status.get('completed', False):
                        print(f"任务完成! 耗时: {int(time.time() - start_time)}秒")
                        return history[prompt_id].get('outputs', {})

                    # 显示进度
                    if 'progress' in status:
                        progress = status['progress']
                        print(f"进度: {progress}")

            except requests.RequestException as e:
                print(f"轮询错误: {e}")

            time.sleep(interval)

        raise TimeoutError(f"任务超时: {prompt_id}")

    def get_output_file(self, filename: str, subfolder: str = "", file_type: str = "output") -> bytes:
        """获取输出文件"""
        params = {
            'filename': filename,
            'type': file_type,
            'subfolder': subfolder
        }
        url = f"{self.base_url}/api/view?{urllib.parse.urlencode(params)}"
        resp = requests.get(url, timeout=120)
        return resp.content

    def save_output(self, data: bytes, output_path: str) -> str:
        """保存输出文件"""
        path = Path(output_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, 'wb') as f:
            f.write(data)
        return str(path)

    # ========== 7种模型调用方法 ==========

    def generate_image(self, prompt: str, output: str = "output.png", **kwargs) -> str:
        """文生图"""
        print(f"\n[文生图] 提示词: {prompt}")

        params = {
            'service_type': 'image',
            'prompt': prompt,
            'negativePrompt': kwargs.get('negativePrompt'),
            'width': kwargs.get('width', 1024),
            'height': kwargs.get('height', 1024),
            'steps': kwargs.get('steps', 8),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('image')
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'image')

        for node_id, node_output in outputs.items():
            if 'images' in node_output:
                img = node_output['images'][0]
                data = self.get_output_file(img['filename'], img.get('subfolder', ''), img.get('type', 'output'))
                return self.save_output(data, output)

        raise ValueError("未找到图片输出")

    def generate_image_to_image(self, prompt: str, image_path: str, output: str = "output.png", **kwargs) -> str:
        """图生图"""
        print(f"\n[图生图] 提示词: {prompt}, 输入: {image_path}")

        filename, subfolder = self.upload_file(image_path)

        params = {
            'service_type': 'image_to_image',
            'prompt': prompt,
            'image': filename,
            'steps': kwargs.get('steps', 4),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('image_to_image')
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'image_to_image')

        for node_id, node_output in outputs.items():
            if 'images' in node_output:
                img = node_output['images'][0]
                data = self.get_output_file(img['filename'], img.get('subfolder', ''), img.get('type', 'output'))
                return self.save_output(data, output)

        raise ValueError("未找到图片输出")

    def generate_video(self, prompt: str, duration: int = 9, output: str = "output.mp4", **kwargs) -> str:
        """文生视频"""
        frames = duration * 16 + 16  # 文生视频补16帧
        print(f"\n[文生视频] 提示词: {prompt}, 时长: {duration}秒, 帧数: {frames}")

        params = {
            'service_type': 'video',
            'prompt': prompt,
            'negativePrompt': kwargs.get('negativePrompt'),
            'width': kwargs.get('width', 640),
            'height': kwargs.get('height', 640),
            'frames': frames,
            'steps': kwargs.get('steps', 4),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('video', frames=frames)
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'video')

        return self._extract_video_output(outputs, output)

    def _extract_video_output(self, outputs: Dict, output: str) -> str:
        """从输出中提取视频文件"""
        for node_id, node_output in outputs.items():
            # 视频可能在 videos, images 或 animated 键中
            if 'videos' in node_output:
                vid = node_output['videos'][0]
                data = self.get_output_file(vid['filename'], vid.get('subfolder', ''), vid.get('type', 'output'))
                return self.save_output(data, output)
            elif 'images' in node_output:
                img = node_output['images'][0]
                data = self.get_output_file(img['filename'], img.get('subfolder', ''), img.get('type', 'output'))
                return self.save_output(data, output)
            elif 'animated' in node_output:
                ani = node_output['animated'][0]
                data = self.get_output_file(ani['filename'], ani.get('subfolder', ''), ani.get('type', 'output'))
                return self.save_output(data, output)

        raise ValueError("未找到视频输出")

    def generate_video_frame(self, prompt: str, first_frame: str, last_frame: str, duration: int = 5, output: str = "output.mp4", **kwargs) -> str:
        """首尾帧视频"""
        frames = duration * 16 + 1
        print(f"\n[首尾帧] 提示词: {prompt}, 时长: {duration}秒, 帧数: {frames}")

        first_filename, _ = self.upload_file(first_frame)
        last_filename, _ = self.upload_file(last_frame)

        params = {
            'service_type': 'video_frame',
            'prompt': prompt,
            'negativePrompt': kwargs.get('negativePrompt'),
            'width': kwargs.get('width', 640),
            'height': kwargs.get('height', 640),
            'firstFrame': first_filename,
            'lastFrame': last_filename,
            'frames': frames,
            'steps': kwargs.get('steps', 4),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('video_frame', frames=frames)
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'video_frame')

        return self._extract_video_output(outputs, output)

    def generate_sound_to_video(self, prompt: str, image_path: str, audio_path: str, output: str = "output.mp4", **kwargs) -> str:
        """语音图片转视频"""
        print(f"\n[语音视频] 提示词: {prompt}, 图片: {image_path}, 音频: {audio_path}")

        image_filename, _ = self.upload_file(image_path)
        audio_filename, _ = self.upload_file(audio_path)

        # S2V固定参数
        params = {
            'service_type': 'sound_to_video',
            'prompt': prompt,
            'negativePrompt': kwargs.get('negativePrompt'),
            'width': kwargs.get('width', 640),
            'height': kwargs.get('height', 640),
            'image': image_filename,
            'audio': audio_filename,
            'frames': 77,  # 固定值
            'steps': kwargs.get('steps', 4),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('sound_to_video', frames=77)  # S2V固定77帧
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'sound_to_video')

        return self._extract_video_output(outputs, output)

    def generate_camera_control(self, prompt: str, image_path: str, camera_pose: str = "Zoom In", duration: int = 5, output: str = "output.mp4", **kwargs) -> str:
        """镜头控制视频"""
        if camera_pose not in self.CAMERA_POSES:
            raise ValueError(f"无效的镜头动作: {camera_pose}. 可选值: {self.CAMERA_POSES}")

        frames = duration * 16 + 1  # camera_control需要+1
        print(f"\n[镜头控制] 提示词: {prompt}, 镜头: {camera_pose}, 时长: {duration}秒, 帧数: {frames}")

        image_filename, _ = self.upload_file(image_path)

        params = {
            'service_type': 'camera_control',
            'prompt': prompt,
            'negativePrompt': kwargs.get('negativePrompt'),
            'width': kwargs.get('width', 640),
            'height': kwargs.get('height', 640),
            'image': image_filename,
            'cameraPose': camera_pose,
            'frames': frames,
            'steps': kwargs.get('steps', 4),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('camera_control', frames=frames)
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'camera_control')

        return self._extract_video_output(outputs, output)

    def generate_video_control(self, prompt: str, image_path: str, video_path: str, duration: int = 5, output: str = "output.mp4", **kwargs) -> str:
        """视频控制（动作迁移）"""
        frames = duration * 16 + 1  # video_control需要+1
        print(f"\n[视频控制] 提示词: {prompt}, 参考图: {image_path}, 控制视频: {video_path}, 帧数: {frames}")

        image_filename, _ = self.upload_file(image_path)
        video_filename, _ = self.upload_file(video_path)

        params = {
            'service_type': 'video_control',
            'prompt': prompt,
            'negativePrompt': kwargs.get('negativePrompt'),
            'width': kwargs.get('width', 640),
            'height': kwargs.get('height', 640),
            'image': image_filename,
            'video': video_filename,
            'frames': frames,
            'steps': kwargs.get('steps', 4),
            'seed': kwargs.get('seed'),
        }

        workflow = self.load_workflow('video_control', frames=frames)
        workflow = self.replace_placeholders(workflow, params)
        prompt_id = self.submit_prompt(workflow)
        outputs = self.wait_for_completion(prompt_id, 'video_control')

        return self._extract_video_output(outputs, output)


class FishAudioTTSClient:
    """FishAudio TTS 客户端"""

    def __init__(self, base_url: str = "http://js1.blockelite.cn:26868"):
        self.base_url = base_url.rstrip('/')

    def list_voices(self) -> list:
        """获取可用音色列表"""
        url = f"{self.base_url}/v1/references/list"
        try:
            resp = requests.get(url, timeout=10)
            if resp.status_code == 200:
                # 返回的是 msgpack 格式，简单解析
                content = resp.content.decode('utf-8', errors='ignore')
                # 提取音色ID
                import re
                matches = re.findall(r'reference_ids[^\x00]*?([a-zA-Z0-9_]+)', content)
                return matches
            return []
        except:
            return []

    def generate_speech(self, text: str, voice_id: str = None, output: str = "output.wav",
                        temperature: float = 0.8, top_p: float = 0.8) -> str:
        """
        文本转语音 - 普通模式

        Args:
            text: 要转换的文本
            voice_id: 音色ID (reference_id)，可选
            output: 输出文件路径
            temperature: 温度参数 (0.1-1.0)
            top_p: Top-P 采样参数 (0.1-1.0)

        Returns:
            保存的文件路径
        """
        print(f"\n[语音合成-普通] 文本: {text}, 音色ID: {voice_id or '默认'}")

        url = f"{self.base_url}/v1/tts"
        body = {
            "text": text,
            "temperature": temperature,
            "top_p": top_p,
            "format": "wav"
        }

        if voice_id:
            body["reference_id"] = voice_id

        resp = requests.post(url, json=body, timeout=300)

        if resp.status_code != 200:
            raise Exception(f"TTS失败: HTTP {resp.status_code} - {resp.text}")

        return self._save_audio(resp.content, output)

    def generate_speech_clone(self, text: str, audio_path: str, ref_text: str = None,
                               output: str = "output.wav", temperature: float = 0.8, top_p: float = 0.8) -> str:
        """
        文本转语音 - 音色克隆模式
        使用参考音频克隆音色

        Args:
            text: 要转换的文本
            audio_path: 参考音频文件路径
            ref_text: 参考音频对应文本
            output: 输出文件路径
            temperature: 温度参数
            top_p: Top-P 采样参数

        Returns:
            保存的文件路径
        """
        print(f"\n[语音合成-克隆] 文本: {text}, 参考音频: {audio_path}, 参考文本: {ref_text or '无'}")

        url = f"{self.base_url}/v1/tts"

        # 读取音频文件并转base64
        with open(audio_path, "rb") as f:
            audio_base64 = base64.b64encode(f.read()).decode()

        body = {
            "text": text,
            "reference_audio": audio_base64,
            "temperature": temperature,
            "top_p": top_p,
            "format": "wav"
        }

        if ref_text:
            body["reference_text"] = ref_text

        resp = requests.post(url, json=body, timeout=300)

        if resp.status_code != 200:
            raise Exception(f"TTS克隆失败: HTTP {resp.status_code} - {resp.text}")

        return self._save_audio(resp.content, output)

    def _save_audio(self, data: bytes, output: str) -> str:
        """保存音频文件"""
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, 'wb') as f:
            f.write(data)
        return str(path)


def main():
    parser = argparse.ArgumentParser(description='影视内容生成客户端')
    parser.add_argument('--base-url', default='http://js1.blockelite.cn:26865', help='ComfyUI服务地址')
    parser.add_argument('--tts-url', default='http://js1.blockelite.cn:26868', help='FishAudio TTS服务地址')
    parser.add_argument('--workflow-dir', default=None, help='工作流文件目录')
    parser.add_argument('--service', choices=[
        'image', 'image_to_image', 'video', 'video_frame',
        'sound_to_video', 'camera_control', 'video_control', 'text_to_speech'
    ], required=True, help='服务类型')
    parser.add_argument('--prompt', required=True, help='提示词/文本')
    parser.add_argument('--output', default='output.png', help='输出文件路径')
    parser.add_argument('--image', help='输入图片路径')
    parser.add_argument('--audio', help='输入音频路径')
    parser.add_argument('--video', help='输入视频路径')
    parser.add_argument('--first-frame', help='首帧图片路径')
    parser.add_argument('--last-frame', help='尾帧图片路径')
    parser.add_argument('--camera-pose', choices=ComfyUIClient.CAMERA_POSES, default='Zoom In', help='镜头动作')
    parser.add_argument('--duration', type=int, default=9, help='视频时长(秒)')
    parser.add_argument('--width', type=int, help='宽度')
    parser.add_argument('--height', type=int, help='高度')
    parser.add_argument('--steps', type=int, help='采样步数')
    parser.add_argument('--seed', type=int, help='随机种子')
    parser.add_argument('--negative-prompt', help='负面提示词')
    parser.add_argument('--voice', help='TTS音色ID')
    parser.add_argument('--tts-mode', choices=['speech', 'clone'], default='speech', help='TTS模式')
    parser.add_argument('--reference-audio', help='TTS克隆模式参考音频')
    parser.add_argument('--reference-text', help='TTS克隆模式参考文本')
    parser.add_argument('--test', action='store_true', help='仅测试连接')

    args = parser.parse_args()

    # TTS 服务单独处理
    if args.service == 'text_to_speech':
        tts_client = FishAudioTTSClient(base_url=args.tts_url)
        try:
            if args.tts_mode == 'clone':
                if not args.reference_audio:
                    print("[X] 克隆模式需要指定 --reference-audio 参数")
                    return
                output = args.output.replace('.png', '.wav').replace('.mp4', '.wav')
                result = tts_client.generate_speech_clone(
                    text=args.prompt,
                    audio_path=args.reference_audio,
                    ref_text=args.reference_text,
                    output=output
                )
            else:
                output = args.output.replace('.png', '.wav').replace('.mp4', '.wav')
                result = tts_client.generate_speech(
                    text=args.prompt,
                    voice_id=args.voice,
                    output=output
                )
            print(f"\n[OK] 语音合成完成: {result}")
        except Exception as e:
            print(f"\n[X] 语音合成失败: {e}")
        return

    # ComfyUI 服务
    client = ComfyUIClient(base_url=args.base_url, workflow_dir=args.workflow_dir)

    if args.test:
        if client.test_connection():
            print("[OK] ComfyUI 连接成功")
        else:
            print("[X] ComfyUI 连接失败")
        return

    if not client.test_connection():
        print("[X] ComfyUI 连接失败，请检查服务是否启动")
        return

    try:
        kwargs = {
            'width': args.width,
            'height': args.height,
            'steps': args.steps,
            'seed': args.seed,
            'negativePrompt': args.negative_prompt,
        }

        result = None

        if args.service == 'image':
            result = client.generate_image(args.prompt, args.output, **kwargs)

        elif args.service == 'image_to_image':
            if not args.image:
                print("[X] 图生图需要指定 --image 参数")
                return
            result = client.generate_image_to_image(args.prompt, args.image, args.output, **kwargs)

        elif args.service == 'video':
            result = client.generate_video(args.prompt, args.duration, args.output.replace('.png', '.mp4'), **kwargs)

        elif args.service == 'video_frame':
            if not args.first_frame or not args.last_frame:
                print("[X] 首尾帧需要指定 --first-frame 和 --last-frame 参数")
                return
            result = client.generate_video_frame(args.prompt, args.first_frame, args.last_frame, args.duration, args.output.replace('.png', '.mp4'), **kwargs)

        elif args.service == 'sound_to_video':
            if not args.image or not args.audio:
                print("[X] 语音视频需要指定 --image 和 --audio 参数")
                return
            result = client.generate_sound_to_video(args.prompt, args.image, args.audio, args.output.replace('.png', '.mp4'), **kwargs)

        elif args.service == 'camera_control':
            if not args.image:
                print("[X] 镜头控制需要指定 --image 参数")
                return
            result = client.generate_camera_control(args.prompt, args.image, args.camera_pose, args.duration, args.output.replace('.png', '.mp4'), **kwargs)

        elif args.service == 'video_control':
            if not args.image or not args.video:
                print("[X] 视频控制需要指定 --image 和 --video 参数")
                return
            result = client.generate_video_control(args.prompt, args.image, args.video, args.duration, args.output.replace('.png', '.mp4'), **kwargs)

        if result:
            print(f"\n[OK] 生成完成: {result}")

    except Exception as e:
        print(f"\n[X] 生成失败: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()