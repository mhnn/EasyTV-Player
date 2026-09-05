#!/usr/bin/env python3
"""生成 EasyTV 启动图标位图（mipmap 各密度），复刻矢量设计：
深色底 + 金色圆角矩形 + 黑色播放三角。供 Android TV/老桌面使用（矢量图标在部分桌面会回退成占位图）。"""
from PIL import Image, ImageDraw

DARK = (0x15, 0x15, 0x15, 255)
GOLD = (0xFF, 0xD5, 0x4F, 255)
BLACK = (0x15, 0x15, 0x15, 255)  # 与深色底一致，三角在金块上呈"挖空"效果

# 矢量 viewport=108 的设计坐标
RECT = (12, 18, 96, 90)        # 金块
TRI = [(43, 35), (74, 54), (43, 73)]  # 播放三角


def gen(size):
    img = Image.new("RGBA", (size, size), DARK)
    d = ImageDraw.Draw(img)
    s = size / 108.0

    def sx(x): return x * s
    def sy(y): return y * s

    rx0, ry0, rx1, ry1 = sx(RECT[0]), sy(RECT[1]), sx(RECT[2]), sy(RECT[3])
    radius = max(2, size * 0.06)
    d.rounded_rectangle([rx0, ry0, rx1, ry1], radius=radius, fill=GOLD)
    tri = [(sx(x), sy(y)) for (x, y) in TRI]
    d.polygon(tri, fill=BLACK)
    return img


SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

import os
base = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
for folder, sz in SIZES.items():
    out = os.path.join(base, folder)
    os.makedirs(out, exist_ok=True)
    p = os.path.join(out, "ic_launcher.png")
    gen(sz).save(p, "PNG")
    print("wrote", p)
