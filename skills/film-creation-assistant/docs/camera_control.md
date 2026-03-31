# 镜头控制 (camera_control)

给一张图片，控制镜头运动方向（推拉摇移旋转）。

## 使用场景

```
# 推拉镜头
对这张图片做镜头推进效果（Zoom In）
对这张图片做镜头拉远效果（Zoom Out）

# 摇移镜头
对这张图片做向左平移效果（Pan Left）
对这张图片做向右平移效果（Pan Right）
对这张图片做向上仰拍效果（Tilt Up）
对这张图片做向下俯拍效果（Tilt Down）

# 旋转镜头
对这张图片做顺时针旋转效果（Clockwise）
对这张图片做逆时针旋转效果（Anticlockwise）
```

**可用镜头动作**：`Zoom In` / `Zoom Out` / `Pan Left` / `Pan Right` / `Tilt Up` / `Tilt Down` / `Clockwise` / `Anticlockwise` / `Static`

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 视频描述 |
| image | string | 必填 | 图片路径 |
| camera-pose | string | Zoom In | 镜头动作 |
| duration | int | 5 | 时长（秒） |

## 命令行调用

```bash
python scripts/comfyui_client.py --service camera_control --prompt "镜头推进" --image face.jpg --camera-pose "Zoom In" --output zoom.mp4
```