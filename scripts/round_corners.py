#!/usr/bin/env python3
"""
将截图或其他矩形图片裁剪为圆角矩形，并输出为带透明四角的 PNG。

示例：
    python scripts/round_corners.py docs/images/InGame_Overview.jpg
    python scripts/round_corners.py docs/images/InGame_Overview.jpg --radius 24 --inset 0
    python scripts/round_corners.py docs/images/InGame_Overview.jpg --output build/tmp/overview-rounded.png
    python scripts/round_corners.py docs/images --radius 28 --output-dir build/tmp/rounded

说明：
    - 输入支持常见位图格式，输出固定为 PNG。
    - 输出图片会保留原始内容，仅将四角裁为透明圆角。
    - 对截图类图片默认启用更平滑的蒙版缩放，避免边缘锯齿。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

SUPPORTED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def positive_int(value: str) -> int:
    """解析非负整数参数。"""
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("参数必须为非负整数")
    return parsed


def parse_args(argv: list[str]) -> argparse.Namespace:
    """解析命令行参数。"""
    parser = argparse.ArgumentParser(description="将矩形图片裁剪为带透明圆角的 PNG")
    parser.add_argument("path", help="输入文件或目录")
    parser.add_argument("--radius", type=positive_int, default=50, help="圆角半径，默认 50")
    parser.add_argument("--inset", type=positive_int, default=0, help="圆角向内收缩距离，默认 0")
    parser.add_argument(
        "--smooth-scale",
        type=positive_int,
        default=4,
        help="蒙版超采样倍数，默认 4；值越大边缘越平滑",
    )
    parser.add_argument("--output", help="单文件模式下的输出 PNG 路径")
    parser.add_argument("--output-dir", help="目录模式下的输出目录")
    parser.add_argument("--overwrite", action="store_true", help="允许覆盖已存在的输出文件")
    return parser.parse_args(argv)


def ensure_valid_geometry(width: int, height: int, radius: int, inset: int) -> int:
    """校验圆角参数，并返回实际可用的圆角半径。"""
    max_corner_span = min(width, height) // 2
    if inset > max_corner_span:
        raise ValueError(f"inset 过大：当前图片尺寸为 {width}x{height}，最大允许值为 {max_corner_span}")

    max_radius = max_corner_span - inset
    if radius > max_radius:
        return max_radius
    return radius


def build_mask(size: tuple[int, int], radius: int, inset: int, smooth_scale: int) -> Image.Image:
    """构建带抗锯齿效果的圆角蒙版。"""
    if smooth_scale < 1:
        raise ValueError("smooth_scale 必须大于等于 1")

    width, height = size
    actual_radius = ensure_valid_geometry(width, height, radius, inset)
    if actual_radius == 0:
        return Image.new("L", size, 255)

    scale = smooth_scale
    scaled_width = width * scale
    scaled_height = height * scale
    scaled_radius = actual_radius * scale
    scaled_inset = inset * scale
    edge = scaled_radius + scaled_inset

    mask = Image.new("L", (scaled_width, scaled_height), 0)
    draw = ImageDraw.Draw(mask)

    draw.rectangle([edge, 0, scaled_width - edge, scaled_height], fill=255)
    draw.rectangle([0, edge, scaled_width, scaled_height - edge], fill=255)

    draw.pieslice([scaled_inset, scaled_inset, scaled_inset + 2 * scaled_radius, scaled_inset + 2 * scaled_radius], 180, 270, fill=255)
    draw.pieslice([scaled_width - 2 * scaled_radius - scaled_inset, scaled_inset, scaled_width - scaled_inset, scaled_inset + 2 * scaled_radius], 270, 360, fill=255)
    draw.pieslice([scaled_inset, scaled_height - 2 * scaled_radius - scaled_inset, scaled_inset + 2 * scaled_radius, scaled_height - scaled_inset], 90, 180, fill=255)
    draw.pieslice([scaled_width - 2 * scaled_radius - scaled_inset, scaled_height - 2 * scaled_radius - scaled_inset, scaled_width - scaled_inset, scaled_height - scaled_inset], 0, 90, fill=255)

    return mask.resize(size, Image.Resampling.LANCZOS)


def default_output_path(input_path: Path, output_dir: Path | None) -> Path:
    """根据输入文件推导默认输出路径。"""
    parent = output_dir if output_dir is not None else input_path.parent
    if input_path.suffix.lower() == ".png":
        return parent / f"{input_path.stem}_rounded.png"
    return parent / f"{input_path.stem}.png"


def round_corners(input_path: Path, output_path: Path, radius: int, inset: int, smooth_scale: int) -> None:
    """读取图片，用圆角蒙版裁剪并保存为 PNG。"""
    with Image.open(input_path) as original:
        img = ImageOps.exif_transpose(original).convert("RGBA")

    mask = build_mask(img.size, radius, inset, smooth_scale)
    img.putalpha(mask)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path, "PNG")
    print(
        f"  OK  {input_path.name} -> {output_path.name} "
        f"(r={radius} inset={inset} smooth={smooth_scale})"
    )


def iter_input_files(input_dir: Path) -> list[Path]:
    """枚举目录中的可处理图片文件。"""
    return [
        path
        for path in sorted(input_dir.iterdir())
        if path.is_file() and path.suffix.lower() in SUPPORTED_EXTENSIONS
    ]


def should_write(output_path: Path, overwrite: bool) -> bool:
    """判断是否允许写入输出文件。"""
    if overwrite or not output_path.exists():
        return True
    print(f"  SKIP 已存在: {output_path}")
    return False


def process_file(
    input_path: Path,
    output_path: Path,
    radius: int,
    inset: int,
    smooth_scale: int,
    overwrite: bool,
) -> bool:
    """处理单个文件，返回是否成功输出。"""
    if input_path.resolve() == output_path.resolve():
        print(f"  SKIP 输入与输出相同: {input_path}")
        return False

    if not should_write(output_path, overwrite):
        return False

    round_corners(input_path, output_path, radius, inset, smooth_scale)
    return True


def main() -> int:
    """脚本入口。"""
    args = parse_args(sys.argv[1:])
    input_path = Path(args.path).expanduser().resolve()

    if not input_path.exists():
        print(f"文件或目录不存在: {input_path}")
        return 1

    if args.output and input_path.is_dir():
        print("目录模式下不能使用 --output，请改用 --output-dir")
        return 1

    if input_path.is_file():
        if input_path.suffix.lower() not in SUPPORTED_EXTENSIONS:
            print(f"不支持的文件类型: {input_path.suffix}")
            return 1

        output_path = Path(args.output).expanduser().resolve() if args.output else default_output_path(input_path, None)
        written = process_file(
            input_path=input_path,
            output_path=output_path,
            radius=args.radius,
            inset=args.inset,
            smooth_scale=args.smooth_scale,
            overwrite=args.overwrite,
        )
        return 0 if written else 1

    output_dir = Path(args.output_dir).expanduser().resolve() if args.output_dir else None
    files = iter_input_files(input_path)
    if not files:
        print(f"目录中没有可处理的图片: {input_path}")
        return 1

    success_count = 0
    for file_path in files:
        output_path = default_output_path(file_path, output_dir)
        try:
            if process_file(
                input_path=file_path,
                output_path=output_path,
                radius=args.radius,
                inset=args.inset,
                smooth_scale=args.smooth_scale,
                overwrite=args.overwrite,
            ):
                success_count += 1
        except ValueError as exc:
            print(f"  FAIL {file_path.name}: {exc}")

    if success_count == 0:
        print("没有生成任何文件")
        return 1

    print(f"完成，共输出 {success_count} 个文件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
