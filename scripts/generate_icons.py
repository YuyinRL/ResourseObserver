#!/usr/bin/env python3
"""
Generate 16x16 pixel art GUI sprite icons for the Resource Observer mod.
Each icon is designed to fit the sci-fi/dashboard theme of the terminal UI.
Uses the mod's theme palette (cyan, amber, emerald, blue, etc.).

All icons use transparent backgrounds for proper in-game overlay rendering.
"""

from PIL import Image, ImageDraw
import math
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(
    SCRIPT_DIR,
    "../src/main/resources/assets/resourceobserver/textures/gui/sprites/terminal"
))


def save(img: Image.Image, name: str):
    path = os.path.join(OUT, name)
    img.save(path)
    print(f"  ✓ {name}")


# === Color Palette (matching UiThemeTokens) ===
CYAN = (34, 211, 238, 255)
CYAN_DIM = (22, 163, 185, 255)
CYAN_BRIGHT = (165, 243, 252, 255)
AMBER = (245, 158, 11, 255)
AMBER_BRIGHT = (253, 224, 71, 255)
AMBER_DIM = (200, 120, 8, 255)
EMERALD = (16, 185, 129, 255)
EMERALD_BRIGHT = (52, 211, 153, 255)
EMERALD_DIM = (5, 150, 105, 255)
BLUE = (96, 165, 250, 255)
BLUE_BRIGHT = (147, 197, 253, 255)
BLUE_DIM = (59, 130, 246, 200)
ROSE = (244, 63, 94, 255)
GRAY = (148, 163, 184, 255)
GRAY_BRIGHT = (203, 213, 225, 255)
GRAY_DIM = (100, 116, 139, 255)
WHITE = (240, 240, 245, 255)
T = (0, 0, 0, 0)  # Transparent


def px(img, data, palette):
    """Draw pixel art from a 2D character map. Each char maps to a color in palette."""
    for y, row in enumerate(data):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), palette[ch])


def make_kpi_production():
    """生产量图标 — 向上箭头 + 加号，代表产出增长"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': CYAN,
        '+': CYAN_BRIGHT,
        '.': CYAN_DIM,
    }
    data = [
        "                ",
        "       ##       ",
        "      ####      ",
        "     ##++##     ",
        "    ## ++ ##    ",
        "       ++       ",
        "       ++       ",
        "       ++       ",
        "       ++       ",
        "       ++       ",
        "                ",
        "    .      .    ",
        "   ...    ...   ",
        "   .............",
        "   .............",
        "                ",
    ]
    px(img, data, p)
    return img


def make_kpi_consumption():
    """消耗量图标 — 向下箭头 + 火焰底部，代表资源消耗"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': AMBER,
        '+': AMBER_BRIGHT,
        '.': AMBER_DIM,
    }
    data = [
        "                ",
        "       ##       ",
        "       ##       ",
        "       ##       ",
        "       ##       ",
        "       ##       ",
        "    ## ## ##    ",
        "     ######     ",
        "      ####      ",
        "       ##       ",
        "                ",
        "     .+..+.     ",
        "    .+.++.+.    ",
        "    .+.++.+.    ",
        "    ..++++..    ",
        "     ......     ",
    ]
    px(img, data, p)
    return img


def make_kpi_storage():
    """库存量图标 — 箱子/仓库风格"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': BLUE,
        '+': BLUE_BRIGHT,
        '.': BLUE_DIM,
        'o': WHITE,
    }
    data = [
        "                ",
        "                ",
        "   ##########   ",
        "   #+++++++.#   ",
        "   #........#   ",
        "   #........#   ",
        "   ##########   ",
        "   #....oo..#   ",
        "   #....oo..#   ",
        "   #........#   ",
        "   #........#   ",
        "   #........#   ",
        "   #........#   ",
        "   ##########   ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_kpi_efficiency():
    """效率图标 — 仪表盘/速度计"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': EMERALD,
        '+': EMERALD_BRIGHT,
        '.': EMERALD_DIM,
    }
    data = [
        "                ",
        "     ######     ",
        "   ##......##   ",
        "  #..........#  ",
        "  #..........#  ",
        " #......++....# ",
        " #.....++.....# ",
        " #....++......# ",
        " #...++.......# ",
        " #..++........# ",
        "  #.+........#  ",
        "  #..........#  ",
        "   ##......##   ",
        "     ######     ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_header_status():
    """连接状态图标 — WiFi 信号弧"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': CYAN,
        '+': CYAN_BRIGHT,
        '.': CYAN_DIM,
    }
    data = [
        "                ",
        "                ",
        "   ..........   ",
        "  .          .  ",
        "                ",
        "    ........    ",
        "   .        .   ",
        "                ",
        "      ....      ",
        "     .    .     ",
        "                ",
        "       ##       ",
        "       ##       ",
        "                ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_action_bell():
    """通知铃铛图标"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': AMBER,
        '+': AMBER_BRIGHT,
        '.': AMBER_DIM,
    }
    data = [
        "                ",
        "       ##       ",
        "      ####      ",
        "     #++++#     ",
        "    #+....+#    ",
        "    #......#    ",
        "    #......#    ",
        "   #........#   ",
        "   #........#   ",
        "  #..........#  ",
        "  ############  ",
        "                ",
        "       ##       ",
        "       ##       ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_action_settings():
    """设置齿轮图标"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': GRAY,
        '+': GRAY_BRIGHT,
        '.': GRAY_DIM,
    }
    data = [
        "                ",
        "      .##.      ",
        "     .####.     ",
        "   ###.++.###   ",
        "   ##.+..+.##   ",
        "  .##......##.  ",
        "  ###......###  ",
        "  ###......###  ",
        "  .##......##.  ",
        "   ##.+..+.##   ",
        "   ###.++.###   ",
        "     .####.     ",
        "      .##.      ",
        "                ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_action_lang():
    """语言切换图标 — 地球仪风格"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': BLUE,
        '+': BLUE_BRIGHT,
        '.': BLUE_DIM,
    }
    data = [
        "                ",
        "     ######     ",
        "   ##..++..##   ",
        "  #....++....#  ",
        "  #....++....#  ",
        " #.....++.....# ",
        " #+++++++++++++#",
        " #+++++++++++++#",
        " #.....++.....# ",
        "  #....++....#  ",
        "  #....++....#  ",
        "   ##..++..##   ",
        "     ######     ",
        "                ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_watch_item():
    """关注列表物品图标 — 眼睛/监视"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': CYAN,
        '+': CYAN_BRIGHT,
        '.': CYAN_DIM,
    }
    data = [
        "                ",
        "                ",
        "                ",
        "     ######     ",
        "   ##......##   ",
        "  #...#++#...#  ",
        " #...#++++#...# ",
        " #...#++++#...# ",
        "  #...#++#...#  ",
        "   ##......##   ",
        "     ######     ",
        "                ",
        "                ",
        "                ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def make_table_item():
    """表格物品图标 — 3D 方块"""
    img = Image.new("RGBA", (16, 16), T)
    p = {
        '#': GRAY,
        '+': GRAY_BRIGHT,
        '.': GRAY_DIM,
    }
    data = [
        "                ",
        "       +        ",
        "      +++       ",
        "     +++++      ",
        "    +++++++     ",
        "   +++++++++    ",
        "    ##+++...    ",
        "    ##++....    ",
        "    ###+...     ",
        "    ###.....    ",
        "    ###....     ",
        "    ###...      ",
        "    ###..       ",
        "     ##.        ",
        "                ",
        "                ",
    ]
    px(img, data, p)
    return img


def _star_points(cx, cy, outer_r, inner_r):
    """计算五角星的 10 个顶点坐标（外点与内点交替排列）"""
    points = []
    for i in range(5):
        # 外顶点
        angle_out = math.radians(-90 + i * 72)
        points.append((cx + outer_r * math.cos(angle_out),
                        cy + outer_r * math.sin(angle_out)))
        # 内顶点
        angle_in = math.radians(-90 + i * 72 + 36)
        points.append((cx + inner_r * math.cos(angle_in),
                        cy + inner_r * math.sin(angle_in)))
    return points


def make_icon_star_filled():
    """实心五角星 — 已收藏状态"""
    img = Image.new("RGBA", (16, 16), T)
    draw = ImageDraw.Draw(img)
    pts = _star_points(7.5, 7.5, 6.5, 2.7)
    # 填充主色
    draw.polygon(pts, fill=AMBER, outline=AMBER_BRIGHT)
    # 添加高光：在上半部分叠加一个半透明亮色小星
    highlight_pts = _star_points(7.5, 7.0, 3.0, 1.2)
    highlight_color = (253, 224, 71, 90)  # AMBER_BRIGHT semi-transparent
    draw.polygon(highlight_pts, fill=highlight_color)
    return img


def make_icon_star_empty():
    """空心五角星 — 未收藏状态"""
    img = Image.new("RGBA", (16, 16), T)
    draw = ImageDraw.Draw(img)
    pts = _star_points(7.5, 7.5, 6.5, 2.7)
    # 只绘制轮廓线，不填充
    # 用连线方式绘制以确保可见度
    for i in range(len(pts)):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % len(pts)]
        draw.line([(x1, y1), (x2, y2)], fill=GRAY_DIM, width=1)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    print(f"Output: {OUT}\n")

    save(make_kpi_production(), "kpi_production.png")
    save(make_kpi_consumption(), "kpi_consumption.png")
    save(make_kpi_storage(), "kpi_storage.png")
    save(make_kpi_efficiency(), "kpi_efficiency.png")
    save(make_header_status(), "header_status.png")
    save(make_action_bell(), "action_bell.png")
    save(make_action_settings(), "action_settings.png")
    save(make_action_lang(), "action_lang.png")
    save(make_watch_item(), "watch_item.png")
    save(make_table_item(), "table_item.png")
    save(make_icon_star_filled(), "icon_star_filled.png")
    save(make_icon_star_empty(), "icon_star_empty.png")

    print(f"\nDone! All 12 icons generated in:\n  {OUT}")


if __name__ == "__main__":
    main()

