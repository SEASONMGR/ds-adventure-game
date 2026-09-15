# UI 素材与小游戏美术 · 接线规格

> 面向程序侧。目标：让已交付的 **47 件 UI + 14 横幅 + 标题**真正出现在游戏里，
> 并说明**小游戏美术**怎么接。
> 结论先行：**UI 部分不需要改引擎代码**，用现有的 `bg` / `char` 节点叠加即可（节点按顺序绘制）。

---

## 一、原理：节点是**按顺序绘制**的

已编译剧本里，一个场景就是一组节点：

```
[scene_name]
{ type = bg     … path = assets/sprites/backgrounds/xxx.png }
{ type = char   … path = assets/sprites/ds/normal.png }
{ type = dialog … text = <<< 台词 >>> }
{ type = name   … text = ds 娘 }
{ type = button … text = 选项 }
```

引擎按出现顺序依次绘制，**后画的盖在先画的上面**。
而 `bg` 和 `char` 两种节点**都接受 `path`**（`char` 还多一个 `opacity`）——
所以**任何 PNG 都能当一层贴上去**，位置和尺寸由 `x/y/width/height` 控制。

→ **UI 皮肤不需要新节点类型**：把 UI 图当作 `bg`/`char` 节点插在正确的位置即可。

---

## 二、UI 素材接线表（可直接粘）

### 2.1 对话框皮肤 + 名牌

关键：**UI 图节点放在 `dialog` / `name` 节点之前**，让引擎的文字压在图上。

```
{
type = char
id = ui_dialog
x = 64
y = 430
width = 1152
height = 230
path = assets/sprites/ui/dialog_box.png
opacity = 1.0
}

{
type = char
id = ui_nameplate
x = 96
y = 392
width = 300
height = 62
path = assets/sprites/ui/name_plate.png
opacity = 1.0
}

{
type = name
id = 角色名
x = 140
y = 404
width = 260
height = 40
text = ds 娘
fontSize = 22
align = left
}

{
type = dialog
id = 对话框
x = 96
y = 448
width = 1090
height = 190
text = <<<
台词写在这里。
>>>
}
```

> 坐标按游戏画布 **1280×720** 给。`dialog` 的 y=448 是为了让引擎文字落在对话框图的可读区里。

### 2.2 系统按钮（保存/读取/设置/跳过/自动/隐藏/回想）

`button` 节点是引擎绘制的，想用我的图标按钮，就**先铺一张图标 `char` 节点，再放一个透明 `button`** 在上面接管点击：

```
{
type = char
id = ui_btn_save_icon
x = 1180
y = 24
width = 40
height = 40
path = assets/sprites/ui/icon_save_48.png
opacity = 1.0
}

{
type = button
id = 存档
x = 1176
y = 20
width = 48
height = 48
text =
action = save
style = invisible
index = 0
}
```

> `style = invisible` 需要 CSS 里有对应类；若没有，就让 `button` 的 `text` 留空、
> 由 CSS 决定外观 —— **这一步需要程序侧确认 CSS 类名**（与 `styles/player.css` 对齐）。

可用图标（32/48 两档，共 11 组）：

| 用途 | 文件 |
|---|---|
| 存档 / 读取 | `icon_save_32.png` `icon_save_48.png` / `icon_load_*` |
| 设置 | `icon_settings_*` |
| 跳过 / 自动 | `icon_skip_*` / `icon_auto_*` |
| 隐藏 UI / 回想 | `icon_hide_*` / `icon_log_*` |
| 关闭 | `icon_close_*` |
| 好感（心） | `icon_heart_*` |
| 星星 / 鲸鱼（收藏、鳞片计数） | `icon_star_*` / `icon_whale_*` |

### 2.3 选项按钮

```
{
type = char
id = ui_choice
x = 390
y = 300
width = 500
height = 64
path = assets/sprites/ui/choice_button_normal.png
opacity = 1.0
}
```
（`choice_button_hover/pressed` 用于悬停/按下态，需 CSS 或引擎状态切换）

### 2.4 存档槽 / 设置面板 / 进度条 / 滑块 / 开关

| 素材 | 建议用法 |
|---|---|
| `save_slot_normal/hover/empty.png` | 存档界面每格铺一张，文字用 `text` 节点压在上面 |
| `settings_panel.png` | 设置界面整块背景 |
| `progress_bg.png` + `progress_fill.png` | 进度条两层，`progress_fill` 按进度改 `width` |
| `slider_track.png` + `slider_knob.png` | 音量滑块两层 |
| `toggle_on/off.png` | 开关两态切换 `path` |

### 2.5 全局叠加层（每个场景都可以加）

| 素材 | 用法 | 位置建议 |
|---|---|---|
| `vignette.png` | **放在节点列表最后**，给所有场景加暗角 | 全屏 0,0,1280,720 |
| `chapter_banner.png` | 章节标题卡（配合 `text` 节点写章节名） | 居中 |
| `water_caustics.png` | 水面光斑（机房/神殿/记忆宫殿场景），`opacity` 调 0.3~0.5 | 全屏 |
| `deco/*.png`（气泡/心/缎带/闪光/鲸纹） | 演出点缀，配合 `@plugin` 的显隐槽 | 任意 |

### 2.6 演出横幅（14 条）

```
{
type = char
id = banner
x = 190
y = 96
width = 900
height = 110
path = assets/sprites/ui/banners/sys_busy.png
opacity = 1.0
}
```
> 横幅图自带透明底和装饰，直接叠在场景上即可。
> 若希望「弹出/淡出」有动画，需要引擎支持 `opacity` 的过渡或一个显隐插件
> （`varplugins.ini` 可注册自定义 `SlotPlugin`，例如 `@plugin(banner_show)`）。

---

## 三、小游戏美术怎么接

### 3.1 现状

`com.studio.plugin.demo.*` 的 8 个游戏插件（2048 / 扫雷 / 打砖块 / 翻牌 / 贪吃蛇 / 飞机大战 / 推箱子 / 五子棋 / 连连看）
**全部是 JavaFX 代码绘制**，颜色是写死的常量，例如：

```java
// SnakePlugin.java
private static final Color C_BG        = Color.web("#0d1020");
private static final Color C_SNAKE_HEAD= Color.web("#7ee787");
// GomokuPlugin.java
private static final Color BOARD_BG    = Color.web("#4a5375");
private static final Color STAR_COLOR  = Color.web("#ffd76a", 0.90);
```

**没有任何贴图加载** —— 所以「小游戏美术」要生效，有两条路。

### 3.2 路线 A：**改配色**（最省事，建议先做这个）

不改逻辑，只把上面那些 `Color.web(...)` 换成与游戏美术统一的色板。
我已生成 **10 套小游戏底图**（`assets/sprites/ui/minigames/mg_*.png`），
每套的**主色**列在 `小游戏色板.md` 里 —— 直接替换常量即可让棋盘/蛇/砖块与底图配色一致。

### 3.3 路线 B：**加载贴图**（需要读地图资源）

`GamePlugin` 接口给了 **`PARAM_MAP_FOLDER`（当前地图文件夹的 `File`）**，
所以插件**已经能读地图目录下的文件**。接法：

```java
// 在 createEmbeddedView(Map<String,Object> params) 里
File mapDir = (File) params.get(GamePlugin.PARAM_MAP_FOLDER);
File bg = new File(mapDir, "resources/images/mg_snake.png");
if (bg.isFile()) {
    ImageView iv = new ImageView(new Image(bg.toURI().toString()));
    iv.setFitWidth(...); iv.setFitHeight(...);
    root.getChildren().add(0, iv);      // 垫在最底层
}
```

配套素材放在 `maps/<地图>/resources/images/minigames/` 下即可（地图本地资源，
与 `demo_map` 现有的 `resources/images/background.png` 同一机制）。

> 贴图套件（棋盘/牌背/棋子/砖块/格子）我可以按需用脚本批量生成成 PNG 精灵图，
> **需要你确认走 A 还是 B**：A 只给色板，B 我给成套 PNG。

---

## 四、标题画面

见 `runtime/galgame-assets/title_scene.txt`（可粘贴的 `[title]` 节点片段）。
其中「退出」按钮缺 `action` 取值、「style」类名待对齐 CSS —— 这两点仍需程序侧确认。

---

## 五、需要程序侧确认的清单

| # | 事项 | 影响 |
|---|---|---|
| 1 | **CSS 类名**（`primary`/`normal`/`small`/`invisible` 是否存在） | 按钮/文字的样式表现 |
| 2 | **按钮 `action` 是否可扩展**（如 `quit`） | 标题「退出」按钮 |
| 3 | **`opacity` 是否支持过渡** | 横幅/暗角的淡入淡出 |
| 4 | **小游戏走路线 A 还是 B** | 我只给色板，还是给成套 PNG |
| 5 | **UI 皮肤是逐场景手写节点，还是做一个「UI 皮肤组」批量注入** | 若逐场景写，每个场景多 3~6 个节点；建议后者（引擎侧加默认 UI 层） |
