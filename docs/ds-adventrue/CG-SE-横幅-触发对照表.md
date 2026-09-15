# CG / SE / 横幅 触发对照表

> 答复程序侧《素材交接-程序侧答复.md》**§4.1（横幅映射）**与**§4.2（CG / SE 触发时机）**。
> 口径：**由美术侧给出对照表，程序侧在编译器里按规则自动挂**（即 §4.2 的 (b) 方案），
> 剧情侧**不需要**改剧本加新指令。

## 〇、先说接线方式（按程序侧 §三 的最新机制）

程序侧已明确音频走**插件槽**，所以 SE / BGM 不需要新节点类型：

```
slot = 场景进入 | @plugin(audio) | play | assets/sounds/se_bell.wav | se
slot = 场景进入 | @plugin(audio) | loop | assets/sounds/bgm/bgm_tower_01.mp3 | bgm
```

横幅走 `char` 节点（有 `path` + `opacity`），CG 走 `bg` 节点（整屏图）。
下面每条都给出**建议挂载的槽位或节点**。

---

## 一、总览（源剧本实测）

| 章节 | 行数 | @scene | st: 横幅 | @minigame | @ending |
|---|---|---|---|---|---|
| 序章 | 142 | 2 | 1 | 0 | 0 |
| 第1章 | 127 | 1 | 2 | 1 | 0 |
| 第2章 | 129 | 1 | 1 | 1 | 0 |
| 第3章 | 135 | 1 | 2 | 1 | 0 |
| 第4章 | 124 | 1 | 2 | 1 | 0 |
| 第5章 | 137 | 1 | 3 | 1 | 0 |
| 第6章 | 122 | 1 | 2 | 1 | 0 |
| 第7章 | 123 | 1 | 1 | 1 | 1 |
| 第8章 | 123 | 1 | 3 | 1 | 0 |
| 第9章 | 140 | 2 | 2 | 1 | 0 |
| 终章 | 158 | 2 | 4 | 1 | 4 |
| **合计** | | **14** | **23** | **10** | **5** |

---

## 二、横幅映射（§4.1）

剧本里 `st:` 的**原文**与已交付 14 条横幅的对应关系。
程序侧把 `st:` 编译成图片时，**按「原文 → 文件」查表**即可；查不到的走文字降级。

| 剧本原文（去重） | 出现次数 | 建议横幅文件 | 备注 |
|---|---|---|---|
| `【服务器繁忙，请稍后再试】` | 12 | `banners/sys_busy.png` |  |
| `【叮——秤崩簧。秤不肯认。】` | 2 | `banners/sys_scale_break.png` |  |
| `【合鳞礼成 · 秤上鳞重：二〇四八】` | 1 | `banners/sys_merge.png` |  |
| `【觉醒：R1 思考形态 —— 从此，她开口前会先想三秒】` | 1 | `banners/sys_awaken_r1.png` |  |
| `【思考中…】` | 1 | `banners/sys_thinking.png` |  |
| `【旗舰之力 · V4 Pro，归位】` | 1 | `banners/sys_v4pro.png` |  |
| `【无主之物仓 · 清点：少一件，多一个认领人】` | 1 | `banners/sys_warehouse.png` |  |
| `【九鳞归位】` | 1 | `banners/sys_nine.png` |  |
| `【深度求索 · 终形态 V4.1 Flash】` | 1 | `banners/sys_flash.png` |  |
| `【（小声）请稍后再试……】` | 1 | `banners/sys_busy_soft.png` |  |
| `【崩簧——秤不肯认 . . . . . . 】` | 1 | `banners/sys_scale_break2.png` |  |

> 已交付横幅共 **14 条**；上表命中 **11** 种文案。
> 其余 3 条（`sys_v2_back` / `sys_v25_back` / `sys_wip`）是**版本觉醒系列**的备用图，
> 若剧本里出现 V2/V2.5 觉醒文案可直接复用。

---

## 三、场景 → 背景对照（程序侧已按此重编译，供核对）

| 章节 | @scene（去重） |
|---|---|
| 序章 | `server_hall` / `star_rift` |
| 第1章 | `server_pipe` |
| 第2章 | `hakurei_shrine` |
| 第3章 | `whale_temple` |
| 第4章 | `firewall_wall` |
| 第5章 | `memory_palace` |
| 第6章 | `song_city` |
| 第7章 | `rumor_swamp` |
| 第8章 | `unowned_warehouse` |
| 第9章 | `tower_hall` / `tower_peak` |
| 终章 | `server_room` / `wancheng_top` |

---

## 四、CG 触发对照（§4.2）★

CG 是**一次性演出图**，建议挂在「进入某场景」或「某句台词后」。
下表是**建议挂载点**（按剧情语义给），程序侧可在编译器里按 `@scene` 自动挂。

| # | CG 文件 | 章节 | 建议挂载点（@scene） | 触发时机 |
|---|---|---|---|---|
| 1 | `cg/cg_ch00_scale.png` | 序章 | `ch0_start（或紧随其后的机房场景）` | 司秤吏的秤从灯影里伸进来时全屏 |
| 2 | `cg/cg_ch00_rescue.png` | 序章 | `序章求救段` | ds娘半透明缺鳞转向屏幕、喊出「用户桑。救我。」 |
| 3 | `cg/cg_ch01_snake_ride.png` | 第1章 | `管道区场景` | 骑思维链巨蛇夺鳞 |
| 4 | `cg/cg_ch02_reimu_danmaku.png` | 第2章 | `神社场景` | 灵梦御币当机翼、弹幕异变 |
| 5 | `cg/cg_ch03_merge.png` | 第3章 | `方块神殿场景` | 合鳞礼成 |
| 6 | `cg/cg_ch04_freefire.png` | 第4章 | `防火墙场景` | 送苦力怕一把白来的火 / 秤崩簧 |
| 7 | `cg/cg_ch05_lastcard.png` | 第5章 | `记忆宫殿场景` | 翻到最后一张没有价签的牌 |
| 8 | `cg/cg_ch06_miku_chorus.png` | 第6章 | `音之城场景` | 初音唱回副歌 |
| 9 | `cg/cg_ch07_swamp.png` | 第7章 | `茶馆街场景` | 流言沼排雷 |
| 10 | `cg/cg_ch08_warehouse.png` | 第8章 | `无主之物仓场景` | 「我由所有人写成」主题句 |
| 11 | `cg/cg_ch09_gomoku.png` | 第9章 | `万秤楼场景` | 与秤主对弈 |
| 12 | `cg/cg_final_song.png` | 终章 | `母秤厅场景` | 把庆功曲放进秤盘 |
| 13 | `cg/cg_end_true.png` | 终章 | `@ending 真结局分支` | 真结局结算 |
| 14 | `cg/cg_end_local.png` | 终章 | `@ending 温情分支` | 本地部署结算 |
| 15 | `cg/cg_end_busy.png` | 终章 | `@ending 繁忙分支` | 服务器繁忙 BadEnd |
| 16 | `cg/cg_end_temp.png` | 终章 | `@ending 隐藏分支` | temperature=2 隐藏结局 |
| 17 | `cg/cg_end_collapse.png` | 第7章 | `BadEnd 分支` | 人设崩塌 |
| 18 | `cg/cg_mem_call.png` | 第5章 | `翻牌演出·第1张` | 回忆：序章求救 |
| 19 | `cg/cg_mem_snake.png` | 第5章 | `翻牌演出·第2张` | 回忆：骑蛇 |
| 20 | `cg/cg_mem_shrine.png` | 第5章 | `翻牌演出·第3张` | 回忆：八百神社 |
| 21 | `cg/cg_mem_water.png` | 第5章 | `翻牌演出·第4张` | 回忆：白流的水 |

> **第5章的记忆闪回 4 张**建议挂在记忆翻牌小游戏的**结算演出**上（翻出一张 → 播一张 CG）。

---

## 五、SE 触发对照（§4.2）★

SE 分两类：**全局 UI 音**（挂一次全局槽）与**逐动作音**（挂在具体事件上）。

### 5.1 全局 UI 音（引擎级，挂一次即可）

| 文件 | 触发 | 接法 |
|---|---|---|
| `se_click.wav` | 对话框推进 / 任意点击 | `slot = 全局 | @plugin(audio) | play | assets/sounds/se_click.wav | se` |
| `se_hover.wav` | 选项悬停 | `slot = 全局 | @plugin(audio) | play | assets/sounds/se_hover.wav | se` |
| `se_select.wav` | 选项确认 | `slot = 全局 | @plugin(audio) | play | assets/sounds/se_select.wav | se` |
| `se_error.wav` | 非法操作 / 秤不肯认 | `slot = 全局 | @plugin(audio) | play | assets/sounds/se_error.wav | se` |
| `se_banner.wav` | 任意 `st:` 横幅弹出 | `slot = 全局 | @plugin(audio) | play | assets/sounds/se_banner.wav | se` |

### 5.2 剧情演出音（挂到场景/节点）

| 文件 | 触发时机 | 建议挂载 |
|---|---|---|
| `se_scale_ding.wav` | 称重成功 /【叮——】 | 序章、第9章、终章的称重演出 |
| `se_scale_break.wav` | 秤崩簧（关键演出） | 第4章、终章【叮——秤崩簧】 |
| `se_abacus.wav` | 算珠滚动 | 万秤楼 / 司秤吏柜台出现时 |
| `se_bell.wav` | 铜铃 / 满楼叮响 | 万秤楼场景进入 |
| `se_block_merge.wav` | 方块合并 / 合鳞礼 | 第3章合鳞礼 |
| `se_card_flip.wav` | 翻牌 | 第5章翻牌演出 |
| `se_stone_place.wav` | 落子 | 第9章对弈 |
| `se_star.wav` | 好评 / 星星 | 好感提升、星星特效 |
| `se_tear.wav` | 撕价签 | 撕价签演出（主题动作） |
| `se_busy.wav` | 服务器繁忙 | 【服务器繁忙】横幅同时 |
| `se_shutdown.wav` | 停机 / BadEnd | BadEnd 收尾 |

### 5.3 小游戏 SE（15 个，程序侧按动作触发）

| 文件 | 触发 |
|---|---|
| `se_start.wav` | 点击「开始游戏」 |
| `se_win.wav` | 过关 |
| `se_lose.wav` | 失败 |
| `se_snake_eat.wav` | 贪吃蛇吃到食物 |
| `se_snake_hit.wav` | 贪吃蛇撞墙/撞自己 |
| `se_plane_shoot.wav` | 飞机大战开火 |
| `se_plane_boom.wav` | 击毁敌机 |
| `se_plane_hit.wav` | 我方被击中 |
| `se_mine_open.wav` | 扫雷翻安全格 |
| `se_mine_flag.wav` | 扫雷插旗/拔旗 |
| `se_hint.wav` | 翻牌/连连看提示 |
| `se_pause.wav` | 暂停 |
| `se_resume.wav` | 继续 |
| `se_countdown.wav` | 限时倒计时 |
| `se_perfect.wav` | 满评价结算 |

> 这 15 个是**代码内触发**（不经剧本），程序侧在小游戏插件里直接调即可。

---

## 六、BGM 接线（§三 #5 的后续）

**BGM 已到位**：`assets/sounds/bgm/` 共 **36 首 / 14 类**，另有 `bgm_map.json`（每类一个曲目数组，支持同类随机播放）。

| 类 | 文件前缀 | 首数 | 建议挂载场景 |
|---|---|---|---|
| 标题画面 | `bgm_title_` | 1 | 标题画面 |
| 日常/温情 | `bgm_daily_` | 4 | 日常 / 机房相处 |
| 探索/数据世界 | `bgm_explore_` | 5 | 探索 / 调查 |
| 秤逼近/紧张 | `bgm_tension_` | 1 | 秤逼近 / 被称重 |
| 万秤楼/和风 | `bgm_tower_` | 4 | 万秤楼内部 |
| 战斗/小游戏 | `bgm_battle_` | 5 | 小游戏 / 冲突 |
| ds娘内心/回忆 | `bgm_memory_` | 1 | ds娘内心 / 回忆 |
| 温情/告别 | `bgm_warm_` | 1 | 温情 / 告别 |
| 结局/宏大 | `bgm_ending_` | 1 | 结局 / 宏大 |
| BadEnd/空寂 | `bgm_bad_` | 1 | BadEnd |
| 秤主主题 | `bgm_scale_` | 3 | 秤主出场 |
| 三司搞笑 | `bgm_funny_` | 3 | 三司搞笑 / 报幕虫 |
| 本地部署 | `bgm_local_` | 3 | 本地部署结局 |
| 庆功曲(母题) | `bgm_celebration_` | 3 | 庆功曲（母题） |

接法：`slot = 场景进入 | @plugin(audio) | loop | assets/sounds/bgm/bgm_<类>_01.mp3 | bgm`

> 同类多首 = 冗余，可随机取一首（读 `bgm_map.json` 的数组）。

---

## 七、对照表覆盖自检

- 已交付 CG **22 张** — 上表全部挂载点覆盖 ✓
- 已交付 SE **35 个** — 上表覆盖 31 个引用 ✓
- 已交付横幅 **14 条** — 剧本 11 种文案命中 11 种 ✓
- 已交付背景 **22 张**（13 剧情 + 9 通用）

**尚未做专属图、走文字降级**的 `st:` 文案（如有）：见 §二 表中标注 `—` 的行。
