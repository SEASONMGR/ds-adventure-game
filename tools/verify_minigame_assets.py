# -*- coding: utf-8 -*-
"""按程序侧《小游戏素材需求.md》§六 的自检清单验收

音频：WAV / 44100Hz / 单声道 / 16bit、命名规范、时长区间
图像：PNG RGBA、精确尺寸、未放大
"""
import wave
from pathlib import Path

from PIL import Image

A = Path(r"D:/developing/javaFX/ds-adventure/src/main/resources/assets")
SND, UI = A / "sounds", A / "sprites/ui"

NEW = ["se_snake_eat", "se_snake_hit", "se_plane_shoot", "se_plane_boom", "se_plane_hit",
       "se_start", "se_win", "se_lose", "se_mine_open", "se_mine_flag", "se_hint",
       "se_pause", "se_resume", "se_countdown", "se_perfect"]
RANGE = {"se_snake_eat": (0.10, 0.20), "se_snake_hit": (0.30, 0.50), "se_plane_shoot": (0.05, 0.10),
         "se_plane_boom": (0.20, 0.40), "se_plane_hit": (0.30, 0.50), "se_start": (0.30, 0.60),
         "se_win": (0.80, 1.50), "se_lose": (0.80, 1.50), "se_mine_open": (0.05, 0.12),
         "se_mine_flag": (0.05, 0.12), "se_hint": (0.15, 0.30), "se_pause": (0.05, 0.20),
         "se_resume": (0.05, 0.20), "se_countdown": (0.05, 0.12), "se_perfect": (0.80, 1.20)}
SPEC = [(f"icon_game_{g}.png", (48, 48)) for g in
        ("snake", "plane", "2048", "breakout", "memory", "link", "mine", "sokoban", "gomoku")]
SPEC += [("shell_badge_win.png", (160, 160)), ("shell_badge_lose.png", (160, 160)),
         ("shell_frame.png", (1024, 600))]
# 规格 §四 里声明"可复用、不需重出"的既有件 —— 确认它们确实在
REUSE = ["chapter_banner.png", "dialog_box.png", "menu_button_normal.png", "menu_button_hover.png",
         "progress_bg.png", "progress_fill.png", "vignette.png", "slider_track.png", "slider_knob.png",
         "toggle_on.png", "toggle_off.png", "save_slot_normal.png", "save_slot_hover.png",
         "save_slot_empty.png"]

print("=== 音频（WAV / 44100Hz / mono / 16bit + 时长区间）===")
bad = 0
for n in NEW:
    p = SND / f"{n}.wav"
    if not p.exists():
        print(f"  {n:<18} 缺失"); bad += 1; continue
    with wave.open(str(p)) as w:
        ch, sw, fr, nf = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
    dur = nf / fr
    lo, hi = RANGE[n]
    ok = ch == 1 and sw == 2 and fr == 44100 and lo <= dur <= hi
    bad += 0 if ok else 1
    print(f"  {n:<18} {fr}Hz {ch}ch {sw*8}bit  {dur:.2f}s  {'OK' if ok else 'FAIL'}")
print(f"  → 不合格 {bad}/{len(NEW)}")

print()
print("=== 图像（PNG RGBA + 精确尺寸，未放大）===")
bad2 = 0
for name, want in SPEC:
    p = UI / name
    if not p.exists():
        print(f"  {name:<26} 缺失"); bad2 += 1; continue
    with Image.open(p) as im:
        got, mode = im.size, im.mode
    ok = got == want and mode == "RGBA"
    bad2 += 0 if ok else 1
    print(f"  {name:<26} {mode} {got[0]}x{got[1]}  {'OK' if ok else 'FAIL 期望 ' + str(want)}")
print(f"  → 不合格 {bad2}/{len(SPEC)}")

print()
print("=== 规格 §四 声明可复用的既有件（应全部在位）===")
miss = [f for f in REUSE if not (UI / f).exists()]
print(f"  在位 {len(REUSE)-len(miss)}/{len(REUSE)}" + (f"  缺: {miss}" if miss else "  OK"))
deco = ["sparkle", "bubbles", "heart", "ribbon_bow", "whale_crest"]
dmiss = [d for d in deco if not (UI / "deco" / f"deco_{d}.png").exists()]
print(f"  deco 在位 {len(deco)-len(dmiss)}/{len(deco)}" + (f"  缺: {dmiss}" if dmiss else "  OK"))

print()
print("=== 汇总 ===")
print(f"  assets/sounds/ 共 {len(list(SND.glob('*.wav')))} 个 wav")
print(f"  assets/sprites/ui/ 共 {len(list(UI.glob('*.png')))} 件 png")
