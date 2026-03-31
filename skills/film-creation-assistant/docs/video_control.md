# 视频控制 (video_control)

给一张图片和参考视频，AI让图片中的人物/物体模仿视频里的动作。

## 使用场景

```
# 模仿动作
让这张照片模仿这个视频里的人物动作
用这张照片和这个跳舞视频生成同样的舞蹈动作

# 动作复用
让这张静态照片动起来，参考这个视频的动作
让这张照片模仿这个手势视频
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 视频描述 |
| image | string | 必填 | 图片路径 |
| video | string | 必填 | 参考视频路径 |
| duration | int | 5 | 时长（秒） |

## 命令行调用

```bash
python scripts/comfyui_client.py --service video_control --prompt "跳舞" --image face.jpg --video dance.mp4 --output result.mp4
```