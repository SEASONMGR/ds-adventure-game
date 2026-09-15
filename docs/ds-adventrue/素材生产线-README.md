# ds-adventure 素材生产线（唯一来源）

> **本工作区 `D:\applications\comfy-ui\runtime\galgame-assets\` 是全部游戏素材的唯一来源。**
> 项目内旧的 `tools/gen_assets.mjs` 管线已废弃，不再使用。
> 项目文档（`剧本/`、`资产清单.md`、`人物设定集.md`）仍然是**需求规格**（表情 id / 角色 id / 场景 id 以它们为准），
> 但**生产、抠图、命名、归档全部由本工作区负责**。那三个文档只读，不改。

---

## 〇、交付现状与引擎约定（2026-09 收尾阶段）

### 0.1 引擎的真实素材约定 ★

剧本（`maps/*/scenario.txt`，由 `tools/build_story.mjs` 编译）里的节点用 **`path =`** 引用素材，
**格式是 `assets/sprites/<角色id>/<表情>.png`（角色文件夹）**，不是扁平的 `<id>_<表情>.png`。

- 权威清单 = 编译产物里的 `path = ` 值（不是任何手写需求表）
- 已编译范围：**序章 + 第 1~2 章**（`story` 10,854 行 / `prologue_404` 2,214 行）
- 校验脚本口径：`assets/*` 从 `src/main/resources` 解析；`resources/*` 从**地图目录**解析
- 当前状态：**42 个引用全部解析成功，0 缺失**

### 0.2 已交付清单

| 类别 | 位置 | 数量 |
|---|---|---|
| 角色立绘 | `assets/sprites/<角色id>/<表情>.png` | **19 个角色文件夹**，81 张官方表情 |
| 部件化分层 | `assets/sprites/layers/<角色id>/` | **17 角色 / 170 图层**（body + face_* + 4 情绪层），见 `分层命名对照表.md` |
| 背景 | `assets/sprites/backgrounds/`（+ `hires/`） | 22 张（氛围版） |
| **标题画面** | `assets/sprites/ui/title_bg.png` + `title_logo_{a,b,c}.png`（+ `hires/`） | 背景 1 + 美术字 3（片段见 `title_scene.txt`） |
| CG | `assets/cg/`（+ `hires/`） | 22 张，hires 为 3072 宽 |
| 演出横幅 | `assets/sprites/ui/banners/` | 14 条 |
| UI | `assets/sprites/ui/` | 47 件 + 5 装饰 |
| **音效 SE** | `assets/sounds/` | **20 个 wav**，见 `sound_list.md` |

### 0.3 未完成 / 待决策

| 项 | 状态 |
|---|---|
| **BGM 14 首** | 🔶 **已选路线 B（CC0 素材库）** → 检索条件见 `BGM检索清单.md`，等素材到位 |
| 标题画面 | 🔶 资源已归档（背景 A2 + 美术字 3 款）、`title_scene.txt` 片段已写；需引擎侧确认 `action=quit` 与 CSS `style` 类名 |
| 部件化分层 | ❌ **不做**（用户判定整体效果不好）→ 已撤出游戏项目，存档 `layers_archive/` |
| 小游戏美术 | ✅ **不需要** —— 22 个插件零图片引用，全代码绘制 |
| 中文字体 | ✅ **不是缺口** —— 引擎无 `Font.loadFont`，CSS 用系统字体 |
| 第 3~9 章素材 | ⬜ 剧本未编译，引用不可知 |
| 连连看插件 | ⬜ Java 代码缺口（`plugins.ini` 无此项），非素材问题 |

### 0.4 一条重要教训（分层坐标系）

分层**必须**在**合成图坐标系**（1024×1536，未裁切）里做。
用紧裁切后的立绘（~809×1444）会让脸部矩形整体错位；
用逐张 rembg 的抠图差异推矩形，会因边缘 matte 噪声把包围盒涨到整幅图。
详见 `分层命名对照表.md` §五。

---

## 一、总管线（按改动幅度分工）

```
① 定妆底图
   有官方参考图的角色：prep_ref.py（白底补边到 2:3，绝不拉伸）
                        → inpaint_exprs.py --full（i2i，denoise 0.45）
   有角色 LoRA 的角色：Anima t2i + 角色 LoRA（如 ds娘）
   原创/原作角色：      Anima t2i（canonical tag 或设计描述）

② 大改动（服装差分 / 动作姿势）→ Qwen-Image-Edit-2511（40 步 / CFG 4.0）
   ★ 换姿势/换角度前，必须描述参考图里被遮挡的部位，否则模型脑补（见 经验教训.md §1）

③ 小差分（表情）→ Anima 面部 inpaint（inpaint_exprs.py）
   make_face_mask.py 出灰度遮罩（只盖纯脸皮肤，避开头发和手）
   出图后自动回贴底图 → 遮罩外像素逐字节相同（实测 max diff = 0）

④ 抠图 → cutout.py（rembg 语义分割 + 边缘补色）
   扁平贴纸才用 chroma_key.py（白底键控）

⑤ 归档 → 项目 src/main/resources/assets/sprites/，命名 <角色id>_<表情>.png
```

**统一参数经验值**：denoise 0.55（抑制大嘴）/ 0.70（常规表情）/ 0.85（脸小、刘海厚的角色）

---

## 二、命名规范（对接剧本 DSL）

| 类型 | 命名 | 示例 | 剧本引用 |
|---|---|---|---|
| 立绘 | `<角色id>_<表情>.png` | `glm_normal.png` | `@enter glm normal` |
| 背景 | `bg_<场景id>.png` | `bg_server_hall.png` | `@scene server_hall bg:bg_server_hall.png` |
| 道具/UI | `ui_<名称>.png` | `ui_scale.png` | 演出层叠加 |
| CG | `cg_<章节>_<描述>.png` | `cg_ch05_lastcard.png` | 剧情演出 |

**表情 id 一律取《人物设定集》§10.2**（不许自造）。

---

## 三、生产状态

### 3.1 角色（19 个 id）

| 角色 id | 官方表情集 | 状态 |
|---|---|---|
| `glm` | normal / smug / angry / worried | ✅ 4/4（新底图版） |
| `qianwen` | normal / worried / happy / panic | ✅ 4/4 |
| `kimi` | gentle / sad / smile / serious | ✅ 4/4（denoise 0.85 加强版） |
| `dengguan`（灯官） | normal / delighted / panic | ✅ 3/3 |
| `qiguan`（契官） | normal / apologetic / panic | ✅ 3/3（denoise 0.55 修嘴） |
| `xiguan`（戏官） | normal / delighted / panic | ✅ 3/3 |
| `ds`（ds娘） | whale_cute / normal / cute / cry / whale_cry / smug / panic / fierce / weary / smile / serious / surprised / sad | ✅ **13/13**（面部 inpaint 于 Batch1 底图，脸部坐标 cx 0.490 / cy 0.190） |
| `ds` | 13 个（whale_cute/normal/cute/cry/whale_cry/smug/panic/fierce/weary/smile/serious/surprised/sad） | 🔶 见 §3.2 映射 |
| `creeper` | ash / normal / fierce / happy | 🔶 53 张二创参考已爬，待定设计 |
| `秤主` `snake_expert` `reimu` `paimon` `miku` `amiya` `sai` `怪力` `皮卡丘` `bugs` `sclerk` | 见 §10.2 | ⬜ 待做 |

### 3.2 ds娘 官方表情 id ↔ 现有素材映射

| 官方 id | 现有素材 | 状态 |
|---|---|---|
| `whale_cute` | `defect_happy` | ✅ 可直接用 |
| `whale_cry` | `defect_sad` | ✅ |
| `normal` | `normal` | ✅ |
| `cute` | `happy` | ✅ |
| `cry` | `sad` | ✅ |
| `surprised` | `surprised` | ✅ |
| `smug` | `smug` | ✅ |
| `sad` | `shy`？否 —— 需新出 | ⬜ |
| `smile` / `serious` / `panic` / `fierce` / `weary` | 无对应 | ⬜ 需按官方 id 新出 5 张 |

### 3.3 背景（14 场景）
仅 `server_room` 可复用 `bg_tech_serverroom`；其余 13 张 ⬜ 待做。
另有 9 张通用场景（校园/居家/城市/科技）可作支线备用。

### 3.4 其它
- 事件 CG 12 张 + 结局 5 张 + 记忆闪回 4 张：⬜
- 小游戏美术 10 套：⬜
- 演出横幅 13 条：⬜
- 音频（14 BGM + ~20 SE）：⬜
- 道具/特效 16 项：✅ 5 件装饰（`ui/deco/`）+ 温度旋钮（`add_dial.py`），其余 ⬜
- 系统 UI 47 件：✅

---

## 四、工具清单（本工作区）

| 脚本 | 用途 |
|---|---|
| `prep_ref.py` | 官方参考图 → 2:3 白底补边 + 缩放（不拉伸） |
| `inpaint_exprs.py` | **核心**：i2i 定妆（`--full`）/ 面部 inpaint 差分 / 底图回贴 |
| `make_face_mask.py` | 脸部遮罩（灰度图，`--full` 即全白=纯 i2i） |
| `cutout.py` | rembg 语义分割 + 边缘补色（**角色立绘用这个**） |
| `chroma_key.py` | 白底键控 + 去白边（只用于扁平贴纸） |
| `make_preview.py` | 预览图：`grid` 通用网格 / `chars` 角色总览 |
| `gen_official_expr.py` | 按 §10.2 官方表情 id 生成 args |
| `gen_expr_subtle.py` | 含蓄风格表情提示词（12 语义集，已被官方集取代） |
| `add_dial.py` | 温度旋钮发饰后处理 |
| `make_defect_form.py` | ds娘残缺鲸形态（半透明 + 九处缺鳞透光） |
| `crawl_booru.py` | 多图站参考图爬取（danbooru 403 → 用 safebooru） |
| `krea2_styleref.py` | Krea2 参考条件生成（绕过 node 直接 POST） |
| `archive_assets.py` | 归档到游戏项目 |
| `经验教训.md` | **所有实测踩坑记录，接手前必读** |
| `剧情素材需求总表.md` | 剧情 → 素材需求对照 |

---

## 五、环境约束

- **ComfyUI 必须由用户手动启动**（agent 启动的 GPU 进程会被沙箱静默 SIGKILL）
  启动命令：`python_embeded\python.exe -s ComfyUI\main.py --windows-standalone-build --disable-pinned-memory`
- 12GB 显存：一次一张，不并行
- 中文文本文件用 Python 写（显式 UTF-8），不用 PowerShell 5.x 的 `Set-Content`
- 下载大模型走 `hf-mirror.com`（HF 原站大文件传输不稳）
