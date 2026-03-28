#!/usr/bin/env python3
"""
Fish Audio TTS API 测试脚本
API 地址: http://js1.blockelite.cn:27780
"""

import requests
import base64
import sys
import os

API_URL = "http://js1.blockelite.cn:27780"

def test_health():
    """测试健康检查接口"""
    print("\n[1] 测试健康检查...")
    resp = requests.get(f"{API_URL}/v1/health")
    print(f"    状态码: {resp.status_code}")
    print(f"    响应: {resp.json()}")
    return resp.status_code == 200

def test_basic_tts(text="你好，我是人工智能助手"):
    """测试基础 TTS"""
    print("\n[2] 测试基础文本转语音...")
    print(f"    文本: {text}")
    resp = requests.post(f"{API_URL}/v1/tts", json={"text": text})
    print(f"    状态码: {resp.status_code}")

    if resp.status_code == 200:
        output_file = "test_basic.wav"
        with open(output_file, "wb") as f:
            f.write(resp.content)
        print(f"    文件大小: {len(resp.content)} bytes")
        print(f"    保存路径: {output_file}")
    else:
        print(f"    错误: {resp.text}")
    return resp.status_code == 200

def test_voice_clone(text="亲爱的，你好呀~人家好想你哦", reference_id="test_voice"):
    """测试音色克隆（使用已上传音色）"""
    print("\n[3] 测试音色克隆...")
    print(f"    文本: {text}")
    print(f"    音色ID: {reference_id}")
    resp = requests.post(f"{API_URL}/v1/tts", json={
        "text": text,
        "reference_id": reference_id
    })
    print(f"    状态码: {resp.status_code}")

    if resp.status_code == 200:
        output_file = "test_clone.wav"
        with open(output_file, "wb") as f:
            f.write(resp.content)
        print(f"    文件大小: {len(resp.content)} bytes")
        print(f"    保存路径: {output_file}")
    else:
        print(f"    错误: {resp.text}")
    return resp.status_code == 200

def test_list_references():
    """查看已上传的音色列表"""
    print("\n[4] 查看已上传音色...")
    resp = requests.get(f"{API_URL}/v1/references/list")
    print(f"    状态码: {resp.status_code}")
    print(f"    响应: {resp.text}")
    return resp.status_code == 200

def test_upload_reference(audio_path, ref_id="new_voice", ref_text="这是参考音色的文本"):
    """上传新的参考音色"""
    print("\n[5] 上传参考音色...")
    print(f"    音频文件: {audio_path}")
    print(f"    音色ID: {ref_id}")

    if not os.path.exists(audio_path):
        print(f"    错误: 文件不存在")
        return False

    with open(audio_path, "rb") as f:
        resp = requests.post(
            f"{API_URL}/v1/references/add",
            files={"audio": f},
            data={"id": ref_id, "text": ref_text}
        )

    print(f"    状态码: {resp.status_code}")
    print(f"    响应: {resp.text}")
    return resp.status_code == 200

def test_clone_with_audio(audio_path, ref_text="参考文本", gen_text="克隆后的语音"):
    """直接传入参考音频进行克隆"""
    print("\n[6] 直接传入参考音频克隆...")
    print(f"    参考音频: {audio_path}")

    if not os.path.exists(audio_path):
        print(f"    错误: 文件不存在")
        return False

    with open(audio_path, "rb") as f:
        audio_base64 = base64.b64encode(f.read()).decode()

    resp = requests.post(f"{API_URL}/v1/tts", json={
        "text": gen_text,
        "references": [
            {"audio": audio_base64, "text": ref_text}
        ]
    })

    print(f"    状态码: {resp.status_code}")
    if resp.status_code == 200:
        output_file = "test_clone_direct.wav"
        with open(output_file, "wb") as f:
            f.write(resp.content)
        print(f"    文件大小: {len(resp.content)} bytes")
        print(f"    保存路径: {output_file}")
    else:
        print(f"    错误: {resp.text}")
    return resp.status_code == 200

def main():
    print("=" * 50)
    print("Fish Audio TTS API 测试")
    print(f"服务地址: {API_URL}")
    print("=" * 50)

    # 基础测试
    test_health()
    test_basic_tts()
    test_voice_clone()
    test_list_references()

    # 如果提供了音频文件参数
    if len(sys.argv) > 1:
        audio_path = sys.argv[1]
        test_upload_reference(audio_path)
        test_clone_with_audio(audio_path)

    print("\n" + "=" * 50)
    print("测试完成！生成的音频文件:")
    for f in ["test_basic.wav", "test_clone.wav", "test_clone_direct.wav"]:
        if os.path.exists(f):
            size = os.path.getsize(f)
            print(f"  - {f} ({size} bytes)")
    print("=" * 50)

if __name__ == "__main__":
    main()