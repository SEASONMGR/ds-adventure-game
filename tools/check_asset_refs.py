# -*- coding: utf-8 -*-
"""素材引用完整性校验（真正的 path= / audio= 检查）

用法： python tools/check_asset_refs.py
口径： assets/* 从 src/main/resources 解析；resources/* 从该地图目录解析。
"""
import re
import sys
from pathlib import Path

PROJ = Path(__file__).resolve().parent.parent
RES = PROJ / "src/main/resources"


def main():
    total_ok = total_miss = 0
    problems = []
    for f in sorted((PROJ / "maps").rglob("scenario.txt")):
        t = f.read_text(encoding="utf-8", errors="replace")
        refs = sorted(set(re.findall(r"path\s*=\s*(\S+)", t))
                      | set(re.findall(r"audio\s*=\s*(\S+)", t)))
        ok = miss = 0
        for p in refs:
            cand = (f.parent / p) if p.startswith("resources/") else (RES / p)
            if cand.exists():
                ok += 1
            else:
                miss += 1
                problems.append(f"{f.parent.name}/{f.name}: {p}")
        total_ok += ok
        total_miss += miss
        flag = "OK " if miss == 0 else "FAIL"
        print(f"  [{flag}] {f.parent.name:<14} 引用 {len(refs):>3}  命中 {ok:>3}  缺失 {miss}")
    print()
    if problems:
        print("缺失明细：")
        for p in problems:
            print("   x", p)
    print(f"结果: {total_ok} 命中 / {total_miss} 缺失")
    return 1 if total_miss else 0


if __name__ == "__main__":
    sys.exit(main())
