# 图生图 (image_to_image)

给一张图片，AI生成新风格图片。

## 使用场景

```
# 风格转换
把这张照片转成动漫风格
把这张照片转成油画风格
把这张照片转成素描风格

# 内容修改
把这张照片里的背景换成海滩
把这张照片变成夜景
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 转换描述 |
| image | string | 必填 | 输入图片路径 |

## 命令行调用

```bash
python scripts/comfyui_client.py --service image_to_image --prompt "动漫风格" --image input.jpg --output anime.png
```