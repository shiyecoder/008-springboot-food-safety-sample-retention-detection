#!/usr/bin/env python3
"""种子图片后处理：去水印 -> 统一尺寸 -> JPEG q85 <=400KB -> 重命名 01.jpg.."""
import pathlib
from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path("/Users/xiaoxin/showcase/build/NEW-008/uploads/seed")

# 目录 -> [(目标序号, 文件名关键字)]
PLAN = {
    "dish": [
        (1, "红烧肉"), (2, "西兰花"), (3, "番茄炒蛋"), (4, "清蒸鲈鱼"),
        (5, "酸辣土豆丝"), (6, "紫菜蛋花汤"), (7, "红烧排骨"), (8, "白灼虾"),
        (9, "香菇滑鸡"), (10, "鲜肉包子"), (11, "凉拌黄瓜"), (12, "小米南瓜粥"),
    ],
    "proof": [
        (1, "清炒西兰花"), (2, "留样冰箱内部"), (3, "电子厨房称"),
        (4, "温度计"), (5, "快检操作台"), (6, "登记台"),
    ],
    "disposal": [
        (1, "即将销毁的留样盒"), (2, "废弃物桶"),
        (3, "两名穿白色工作服"), (4, "沥水架"),
    ],
    "avatar": [
        (1, "三十多岁东亚女性食堂留样员"),
        (2, "四十多岁东亚女性食堂留样员"),
        (3, "二十多岁东亚女性食堂留样员"),
        (4, "四十岁上下东亚男性食堂留样员"),
        (5, "二十多岁东亚女性食品检测员"),
        (6, "二十多岁东亚男性食品检测员"),
        (7, "五十岁上下东亚男性食品安全主管"),
        (8, "四十岁上下东亚女性食品安全主管"),
    ],
}

TARGET = {"dish": (1024, 768), "proof": (1024, 768), "disposal": (1024, 768), "avatar": (1024, 1024)}


def strip_watermark(img):
    """用左侧同尺寸区域镜像覆盖右下角水印，边缘羽化融合。"""
    w, h = img.size
    bw, bh = int(w * 0.20), int(h * 0.115)
    x0, y0 = w - bw, h - bh
    src = img.crop((x0 - bw - 14, y0, x0 - 14, y0 + bh)).transpose(Image.FLIP_LEFT_RIGHT)
    mask = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(mask).rectangle([10, 6, bw - 4, bh - 4], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(5))
    out = img.copy()
    out.paste(src, (x0, y0), mask)
    return out


def save_jpeg(img, dst, size):
    if img.size != size:
        img = img.resize(size, Image.LANCZOS)
    q = 85
    while q >= 55:
        img.save(dst, "JPEG", quality=q, optimize=True, progressive=True)
        if dst.stat().st_size <= 400 * 1024:
            return q, dst.stat().st_size
        q -= 6
    return q, dst.stat().st_size


def main():
    for sub, items in PLAN.items():
        folder = ROOT / sub
        files = sorted([p for p in folder.glob("*.png")])
        picked = {}
        for no, kw in items:
            hit = [p for p in files if kw in p.name]
            assert hit, f"{sub}: 没找到包含「{kw}」的图片"
            picked[no] = hit[0]
            files = [p for p in files if p != hit[0]]
        for no, path in sorted(picked.items()):
            img = Image.open(path).convert("RGB")
            img = strip_watermark(img)
            dst = folder / f"{no:02d}.jpg"
            q, sz = save_jpeg(img, dst, TARGET[sub])
            print(f"{sub}/{dst.name}  q={q}  {sz/1024:.0f}KB")
        for p in folder.glob("*.png"):
            p.unlink()


if __name__ == "__main__":
    main()
