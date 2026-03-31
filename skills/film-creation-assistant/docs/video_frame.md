# 首尾帧视频 (video_frame)

给开头和结尾两张图，AI生成中间的过渡动画。

## 使用场景

```
# 变身动画
用这两张图生成一个变身动画（从A变成B）
生成一个从美女变成美女2的过渡视频

# 场景转换
生成从白天变成夜晚的过渡动画
生成从室内走到室外的过渡动画
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 视频描述 |
| first-frame | string | 必填 | 首帧图片路径 |
| last-frame | string | 必填 | 尾帧图片路径 |
| duration | int | 5 | 时长（秒） |

## 命令行调用

```bash
python scripts/comfyui_client.py --service video_frame --prompt "转身" --first-frame a.jpg --last-frame b.jpg --output transition.mp4
```