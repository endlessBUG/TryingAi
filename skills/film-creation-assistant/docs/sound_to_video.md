# 语音图片转视频 (sound_to_video)

给一张图片和一段音频，AI生成人物说话/唱歌的视频。

## 使用场景

```
# 配音说话
让这张照片配上这段音频说话
用这张图片和这段录音生成一个说话视频

# 数字人播报
用这张人物照片和这段新闻稿音频生成播报视频

# 唱歌视频
让这张照片唱这首歌
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 视频描述 |
| image | string | 必填 | 图片路径 |
| audio | string | 必填 | 音频路径 |

## 命令行调用

```bash
python scripts/comfyui_client.py --service sound_to_video --prompt "说话" --image face.jpg --audio voice.wav --output talk.mp4
```