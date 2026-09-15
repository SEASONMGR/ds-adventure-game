# SE 音效清单

> 全部为 Python + numpy **程序化合成**，44.1kHz / 单声道 / 16bit WAV，无第三方版权，可自由修改参数重生成。
> 合成脚本：`make_se.py`（通用 20 个）· `make_se_minigame.py`（小游戏 15 个，按程序侧《小游戏素材需求.md》）

**合计 35 个**（通用 20 + 小游戏 15）

## 一、通用 SE（20 个）

| 文件 | 时长 | 体积 | 用途 |
|---|---|---|---|
| `se_abacus.wav` | 0.42s | 36 KB | 算珠滚动 |
| `se_banner.wav` | 0.30s | 26 KB | 横幅弹出 |
| `se_bell.wav` | 1.60s | 138 KB | 铜铃 / 满楼叮响 |
| `se_block_merge.wav` | 0.55s | 47 KB | 方块合并 / 合鳞礼 |
| `se_box_push.wav` | 0.34s | 29 KB | 推箱子 |
| `se_brick_break.wav` | 0.36s | 31 KB | 砖碎 |
| `se_busy.wav` | 1.00s | 86 KB | 服务器繁忙 |
| `se_card_flip.wav` | 0.17s | 15 KB | 翻牌 |
| `se_click.wav` | 0.04s | 4 KB | 对话框推进 / 通用点击 |
| `se_error.wav` | 0.26s | 22 KB | 非法操作 / 秤不肯认 |
| `se_hover.wav` | 0.02s | 2 KB | 选项悬停 |
| `se_link.wav` | 0.22s | 19 KB | 连连看消除 |
| `se_mine_boom.wav` | 0.85s | 73 KB | 雷炸 |
| `se_scale_break.wav` | 0.55s | 47 KB | 秤崩簧（关键演出） |
| `se_scale_ding.wav` | 1.10s | 95 KB | 称重成功【叮】 |
| `se_select.wav` | 0.14s | 13 KB | 选项确认 |
| `se_shutdown.wav` | 1.10s | 95 KB | 停机 / BadEnd |
| `se_star.wav` | 0.50s | 43 KB | 好评 / 星星 |
| `se_stone_place.wav` | 0.14s | 12 KB | 落子 |
| `se_tear.wav` | 0.22s | 19 KB | 撕价签 |

## 二、小游戏 SE（15 个）—— 按《小游戏素材需求.md》§二

| 文件 | 时长 | 体积 | 用途 | 优先级 |
|---|---|---|---|---|
| `se_snake_eat.wav` | 0.16s | 14 KB | 贪吃蛇 · 吃到食物 | P0
| `se_snake_hit.wav` | 0.42s | 36 KB | 贪吃蛇 · 撞墙/撞自己 | P0
| `se_plane_shoot.wav` | 0.07s | 7 KB | 飞机大战 · 开火（高频连发，务必轻） | P0
| `se_plane_boom.wav` | 0.30s | 26 KB | 飞机大战 · 击毁敌机 | P0
| `se_plane_hit.wav` | 0.42s | 36 KB | 飞机大战 · 我方被击中 | P0
| `se_start.wav` | 0.55s | 47 KB | 小游戏通用 · 点击开始（揭幕感） | P0
| `se_win.wav` | 1.30s | 112 KB | 小游戏通用 · 过关 | P0
| `se_lose.wav` | 1.30s | 112 KB | 小游戏通用 · 失败 | P0
| `se_mine_open.wav` | 0.08s | 7 KB | 扫雷 · 翻开安全格 | P1
| `se_mine_flag.wav` | 0.10s | 9 KB | 扫雷 · 插旗/拔旗 | P1
| `se_hint.wav` | 0.26s | 22 KB | 翻牌 / 连连看 · 提示 | P1
| `se_pause.wav` | 0.10s | 9 KB | 暂停 | P2
| `se_resume.wav` | 0.10s | 9 KB | 继续 | P2
| `se_countdown.wav` | 0.07s | 7 KB | 限时模式倒计时滴答 | P2
| `se_perfect.wav` | 1.10s | 95 KB | 满评价结算（全清 / 最少步数） | P2

## 三、尺寸说明

- 长音效（`se_bell` 1.6s / `se_win`·`se_lose` 1.30s / `se_scale_ding`·`se_shutdown` 1.1s）
  体积较大，因 44.1kHz 16bit 单声道约 86KB/s；若要压缩，降到 22.05kHz 即可减半。
- 全部时长均落在《小游戏素材需求.md》给定的区间内（脚本 `verify_minigame_assets.py` 可复验）。

## 四、可复用（规格 §四 声明不必重出，本轮由程序侧接到逐动作）

`se_brick_break`（撞砖）· `se_card_flip`（翻牌）· `se_mine_boom`（踩雷）· `se_box_push`（推箱）·
`se_stone_place`（落子）· `se_block_merge`（2048 合并）· `se_link`（连连看配对）·
`se_click` / `se_hover` / `se_select`（UI 三音）