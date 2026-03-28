# Fish Audio TTS API 文档

**服务地址**: `http://js1.blockelite.cn:27780`

---

## 接口列表

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/v1/health` | 健康检查 |
| POST | `/v1/tts` | 文本转语音 |
| POST | `/v1/vqgan/encode` | VQ编码（声音克隆） |
| POST | `/v1/vqgan/decode` | VQ解码 |
| POST | `/v1/references/add` | 上传参考音色 |
| GET | `/v1/references/list` | 查看已上传音色 |
| DELETE | `/v1/references/delete` | 删除参考音色 |

---

## 1. 健康检查

```
GET /v1/health
```

**响应示例**:
```json
{"status": "ok"}
```

---

## 2. 文本转语音（基础 TTS）

```
POST /v1/tts
Content-Type: application/json
```

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| text | string | ✅ | 要转换的文本 |
| reference_id | string | ❌ | 已上传的参考音色 ID |
| references | array | ❌ | 直接传入参考音频（见音色克隆） |
| format | string | ❌ | 输出格式，默认 `wav` |

### 请求示例

```json
{
  "text": "你好，我是人工智能助手"
}
```

### 响应

音频文件（WAV 格式）

### Python 调用

```python
import requests

resp = requests.post(
    'http://js1.blockelite.cn:27780/v1/tts',
    json={'text': '你好，我是人工智能助手'}
)

with open('output.wav', 'wb') as f:
    f.write(resp.content)
```

---

## 3. 音色克隆

有两种方式实现音色克隆：

### 方式一：上传参考音色后使用 reference_id

**步骤 1: 上传参考音色**
```
POST /v1/references/add
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | string | ✅ | 音色唯一标识 |
| audio | file | ✅ | 参考音频文件（WAV/MP3） |
| text | string | ✅ | 参考音频对应的文本内容 |

**请求示例**:
```bash
curl -X POST "http://js1.blockelite.cn:27780/v1/references/add" \
  -F "id=my_voice" \
  -F "audio=@voice.wav" \
  -F "text=这是参考音色的文本内容"
```

**步骤 2: 使用 reference_id 进行 TTS**
```json
{
  "text": "这是克隆后的语音",
  "reference_id": "my_voice"
}
```

---

### 方式二：直接在请求中传入参考音频

```
POST /v1/tts
Content-Type: application/json
```

| 参数 | 类型 | 说明 |
|------|------|------|
| text | string | 要生成的文本 |
| references | array | 参考音频列表 |
| references[].audio | string | Base64 编码的音频数据 |
| references[].text | string | 参考音频对应的文本 |

**请求示例**:
```json
{
  "text": "这是克隆后的语音",
  "references": [
    {
      "audio": "UklGRiQAAABXQVZFZm10...",
      "text": "这是参考音色的文本内容"
    }
  ]
}
```

**Python 调用**:
```python
import requests
import base64

# 读取参考音频
with open("reference.wav", "rb") as f:
    audio_base64 = base64.b64encode(f.read()).decode()

resp = requests.post(
    'http://js1.blockelite.cn:27780/v1/tts',
    json={
        "text": "这是克隆后的语音",
        "references": [
            {"audio": audio_base64, "text": "这是参考音色的文本内容"}
        ]
    }
)

with open('cloned.wav', 'wb') as f:
    f.write(resp.content)
```

---

## 4. 查看已上传音色

```
GET /v1/references/list
```

**响应示例**:
```json
{
  "success": true,
  "reference_ids": ["test_voice", "my_voice"],
  "message": "Found 2 reference voices"
}
```

---

## 5. 删除参考音色

```
DELETE /v1/references/delete
Content-Type: application/json
```

**请求参数**:
```json
{
  "reference_id": "my_voice"
}
```

---

## 6. VQ 编码/解码（高级）

```
POST /v1/vqgan/encode   # VQ 编码（用于声音特征提取）
POST /v1/vqgan/decode   # VQ 解码（从编码还原音频）
```

---

## 完整 Python 示例

```python
import requests
import base64

API_URL = "http://js1.blockelite.cn:27780"

# 1. 健康检查
health = requests.get(f"{API_URL}/v1/health")
print(f"服务状态: {health.json()}")

# 2. 基础 TTS
resp = requests.post(f"{API_URL}/v1/tts", json={"text": "你好世界"})
with open("basic.wav", "wb") as f:
    f.write(resp.content)

# 3. 使用已上传音色克隆
resp = requests.post(f"{API_URL}/v1/tts", json={
    "text": "亲爱的你好呀",
    "reference_id": "test_voice"
})
with open("clone.wav", "wb") as f:
    f.write(resp.content)

# 4. 上传新的参考音色
with open("my_voice.wav", "rb") as f:
    resp = requests.post(
        f"{API_URL}/v1/references/add",
        files={"audio": f},
        data={"id": "my_new_voice", "text": "这是我的声音样本"}
    )
print(resp.json())

# 5. 查看已上传音色
resp = requests.get(f"{API_URL}/v1/references/list")
print(resp.json())
```

---

## 错误响应

| 状态码 | 说明 |
|--------|------|
| 500 | 服务器内部错误（编码问题、模型加载失败等） |
| 400 | 请求参数错误 |
| 404 | 音色 ID 不存在 |

---

## 注意事项

1. **中文编码问题**: Windows 下 curl 处理中文 JSON 有编码问题，建议使用 Python 调用
2. **参考音频要求**: 建议使用 5-30 秒的清晰语音样本，背景噪音少效果更好
3. **reference_id 唯一性**: 每个音色 ID 必须唯一，重复上传会覆盖旧的