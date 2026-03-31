# 文生视频 (video)

写文字描述，AI生成对应视频。

## 使用场景

```
生成一个美女在海边散步的视频，5秒
生成一只熊猫在竹林里吃竹子的视频，10秒
生成一辆跑车在公路上飞驰的视频
生成一个日落时分的风景视频
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 视频描述 |
| duration | int | 9 | 时长（秒） |
| width | int | 640 | 视频宽度 |
| height | int | 640 | 视频高度 |

## 命令行调用

```bash
python scripts/comfyui_client.py --service video --prompt "美女在海边散步" --duration 5 --output beach.mp4
```