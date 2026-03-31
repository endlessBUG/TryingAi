# 文本转语音 (text_to_speech)

写文字，AI生成配音。支持普通合成和音色克隆两种模式。

## 使用场景

```
# 普通语音合成（默认音色）
把这段文字转成语音："大家好，欢迎观看本期视频"
生成这段文字的配音："今天天气真好"

# 音色克隆（用参考音频克隆音色）
用这段音频的音色读这句话："大家好"
克隆这个人的声音读这段文字
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 要转换的文字 |
| tts-mode | string | speech | 模式：speech(普通) / clone(克隆) |
| voice | string | 可选 | 音色ID（普通模式） |
| reference-audio | string | 可选 | 参考音频路径（克隆模式） |

## 命令行调用

```bash
# 普通模式
python scripts/comfyui_client.py --service text_to_speech --prompt "大家好" --output voice.wav

# 音色克隆
python scripts/comfyui_client.py --service text_to_speech --prompt "大家好" --tts-mode clone --reference-audio ref.wav --output cloned.wav
```