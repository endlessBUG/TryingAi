# 文生图 (image)

写文字描述，AI生成对应图片。

## 使用场景

```
生成一张可爱的猫咪图片
生成一张赛博朋克风格的城市夜景
生成一张水墨山水画
生成一张科幻风格的宇宙飞船
生成一张古风美女肖像
```

## 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| prompt | string | 必填 | 图片描述 |
| width | int | 1024 | 图片宽度 |
| height | int | 1024 | 图片高度 |

## 命令行调用

```bash
python scripts/comfyui_client.py --service image --prompt "一只可爱的猫咪" --output cat.png
```