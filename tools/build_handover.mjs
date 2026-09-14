// build_handover.mjs —— 生成《剧情 × 游戏逻辑 交接文档》
// 只读剧情文件；抽取接口表 + 组装附录；输出单文件
// 用法: node build_handover.mjs
import fs from 'node:fs';
import path from 'node:path';

const DOC = 'D:/developing/javaFX/ds-adventure/docs/ds-adventrue';
const SCRIPT_DIR = path.join(DOC, '剧本');
const VOICE_DIR = path.join(DOC, '剧本-原声');
const OUT = path.join(DOC, '剧情-游戏逻辑交接文档.md');
const TOOLS = 'D:/developing/javaFX/ds-adventure/tools';

const ORDER = [
  '序章_404之夜.txt',
  '第1章_思维链大暴走.txt', '第2章_幻想乡弹幕异变.txt', '第3章_合鳞礼.txt',
  '第4章_防火墙拆迁办.txt', '第5章_上下文溢出.txt', '第6章_歌姬的曲库灾难.txt',
  '第7章_流言沼公关战.txt', '第8章_无主之物仓.txt', '第9章_决战万秤楼.txt',
  '终章_深度求索.txt',
];
const SHORT = {
  '序章_404之夜.txt': '序章', '终章_深度求索.txt': '终章',
};
const chap = f => SHORT[f] || f.replace(/[_.].*$/, '');

const read = p => fs.readFileSync(p, 'utf8');
const lines = p => read(p).split(/\r?\n/);

// ============ 抽取 ============
const data = { perFile: {}, labels: {}, targets: {}, flags: {}, minigames: [], scenes: [], sprites: {}, choices: [], saves: [], endings: [] };

for (const f of ORDER) {
  const L = lines(path.join(SCRIPT_DIR, f));
  const d = { file: f, chapter: chap(f), lineCount: L.length, labels: [], targets: [], flags: [], minigames: [], scenes: [], sprites: {}, choices: [], saves: [], endings: [] };
  let inChoice = null;
  L.forEach((l, i) => {
    const ln = i + 1;
    let m;
    if ((m = l.match(/^@label\s+(\S+)/))) { d.labels.push(m[1]); data.labels[m[1]] = f; }
    for (const t of l.matchAll(/(?<![A-Za-z_])goto\s+([A-Za-z0-9_]+)/g)) d.targets.push({ t: t[1], ln });
    for (const t of l.matchAll(/on(Win|Lose):([A-Za-z0-9_]+)/g)) d.targets.push({ t: t[2], ln });
    if ((m = l.match(/^@flag\s+(\S+)\s+(.*)$/))) { d.flags.push({ name: m[1], value: m[2].trim(), ln }); (data.flags[m[1]] ??= { producers: [], consumers: [] }).producers.push(`${chap(f)}·L${ln} (${m[2].trim()})`); }
    if ((m = l.match(/^@if\s+(\S+)\s*(>=|<=|==)\s*(\S+)\s+goto\s+(\S+)/))) {
      (data.flags[m[1]] ??= { producers: [], consumers: [] }).consumers.push(`${chap(f)}·L${ln} (${m[2]}${m[3]} → ${m[4]})`);
    }
    if ((m = l.match(/^@minigame\s+(\S+)\s+(.*)$/))) {
      const rest = m[2];
      const g = (k) => (rest.match(new RegExp(k + ':([^\\s]+)')) || [])[1] || '';
      const mg = { chapter: chap(f), id: m[1], mode: g('mode'), onWin: g('onWin'), onLose: g('onLose'), loop: g('loop'), with: g('with'), ln };
      d.minigames.push(mg); data.minigames.push(mg);
      if (mg.with) for (const w of mg.with.split(',')) (data.flags[w] ??= { producers: [], consumers: [] }).consumers.push(`${chap(f)}·L${ln} (with 传参)`);
    }
    if ((m = l.match(/^@scene\s+(\S+)\s+(.*)$/))) {
      const title = (m[2].match(/title:(.+)$/) || [])[1] || '';
      const bg = (m[2].match(/bg:([^\s]+)/) || [])[1] || '';
      const sc = { chapter: chap(f), id: m[1], title: title.trim(), bg, ln };
      d.scenes.push(sc); data.scenes.push(sc);
    }
    if ((m = l.match(/^@enter\s+(\S+)\s+(\S+)/))) { const k = m[1]; (d.sprites[k] ??= []).push(m[2]); ((data.sprites[k] ??= { chapters: {}, exprs: new Set() }).chapters)[chap(f)] = (data.sprites[k].chapters[chap(f)] || 0) + 1; data.sprites[k].exprs.add(m[2]); }
    if (/^@save/.test(l)) { d.saves.push(ln); data.saves.push(`${chap(f)}·L${ln}`); }
    if ((m = l.match(/^@ending\s+(\S+)/))) { d.endings.push(m[1]); data.endings.push(`${chap(f)} → ${m[1]}`); }
    if (/^\*choice/.test(l)) { inChoice = { chapter: chap(f), ln, options: [] }; d.choices.push(inChoice); data.choices.push(inChoice); }
    else if (inChoice && (m = l.match(/^\s*>\s*(.+)$/))) {
      const raw = m[1];
      const parts = raw.split('|').map(s => s.trim());
      inChoice.options.push({ text: parts[0], flag: (parts.find(p => p.startsWith('flag ')) || '').replace('flag ', ''), goto: (parts.find(p => p.startsWith('goto ')) || '').replace('goto ', '') });
      const fseg = parts.find(p => p.startsWith('flag '));
      if (fseg) {
        const seg = fseg.replace('flag ', '').trim().split(/\s+/);
        (data.flags[seg[0]] ??= { producers: [], consumers: [] }).producers.push(`${chap(f)}·L${ln} (选项 ${seg.slice(1).join(' ') || '置位'})`);
      }
    } else if (inChoice && !/^\s*$/.test(l) && !/^\s*>/.test(l) && !/^\*choice/.test(l)) { inChoice = null; }
  });
  data.perFile[f] = d;
}

// 未闭合标签
const missing = [];
for (const f of ORDER) for (const t of data.perFile[f].targets) if (!data.labels[t.t]) missing.push(`${chap(f)}·L${t.ln} → ${t.t}`);

// flag 死旗判定
const flagRows = Object.entries(data.flags).sort().map(([name, v]) => {
  const all = v.producers.join(' ');
  const type = /(\+1|=\s*\d|选项 \d)/.test(all) ? '数值' : (/(got|置位)/.test(all) ? '布尔' : '计数');
  const status = v.consumers.length ? '已消费' : '**⚠ 无下游消费者**';
  return { name, producers: v.producers.join('、') || '—', consumers: v.consumers.join('、') || '—', status, type };
});

// 立绘需求
const spriteRows = Object.entries(data.sprites).sort().map(([id, v]) => ({
  id, exprs: [...v.exprs].sort().join(' / '), count: v.exprs.size,
  chapters: Object.entries(v.chapters).map(([c, n]) => `${c}×${n}`).join(' '),
}));

// ============ 表格生成 ============
const tbl = (head, rows) => [`| ${head.join(' | ')} |`, `|${head.map(() => '---').join('|')}|`, ...rows.map(r => `| ${r.join(' | ')} |`)].join('\n');

const T_chapters = tbl(['章', '行数', '场景', '出场角色(切换次数)', '小游戏', '选项节点/选项数', '存读档', '结局'], ORDER.map(f => {
  const d = data.perFile[f];
  const sp = Object.entries(d.sprites).map(([k, v]) => `${k}×${v.length}`).join(' ');
  const mg = d.minigames.map(m => `${m.id}·${m.mode}`).join(' ') || '—';
  const ch = d.choices.length ? `${d.choices.length} / ${d.choices.reduce((a, c) => a + c.options.length, 0)}` : '—';
  return [chap(f), d.lineCount, d.scenes.map(s => s.id).join(' + ') || '—', sp, mg, ch, `@save×${d.saves.length}`, d.endings.join(' ') || '—'];
}));

const T_labels = tbl(['章', '标签数', '文件'], ORDER.map(f => [chap(f), data.perFile[f].labels.length, `剧本/${f}`]));

const T_flags = tbl(['flag', '类型', '产出点', '消费点', '状态'], flagRows.map(r => [`\`${r.name}\``, r.type, r.producers, r.consumers, r.status]));

const T_minigames = tbl(['章', '游戏 id', '模式', 'onWin', 'onLose', 'loop', 'with 传参'], data.minigames.map(m => [m.chapter, `\`${m.id}\``, `\`${m.mode}\``, `\`${m.onWin}\``, `\`${m.onLose}\``, m.loop ? `\`${m.loop}\`` : '—', m.with ? `\`${m.with}\`` : '—']));

const T_choices = tbl(['章', '节点行', '选项文本', '置位 flag', '跳转'], data.choices.flatMap(c => c.options.map(o => [c.chapter, `L${c.ln}`, o.text, o.flag ? `\`${o.flag}\`` : '—', o.goto ? `\`${o.goto}\`` : '—'])));

const T_scenes = tbl(['场景 id', '章', 'title', 'bg 需求'], data.scenes.map(s => [`\`${s.id}\``, s.chapter, s.title, `\`${s.bg}\``]));

const T_sprites = tbl(['角色 id', '表情数', '表情清单', '出场分布(切换次数)'], spriteRows.map(r => [`\`${r.id}\``, r.count, r.exprs, r.chapters]));

const T_points = tbl(['章', '存档点', '结局锚点'], ORDER.map(f => [chap(f), data.perFile[f].saves.map(l => `L${l}`).join('、') || '—', data.perFile[f].endings.map(e => `\`${e}\``).join('、') || '—']));

const fileRows = [
  ['剧情大纲.md', 636, '41 KB', '世界观/觉醒系统/结局路由/DSL/体量裁剪'],
  ['人物设定集.md', 321, '21 KB', '全员档案 + 表情体系 + 出演规则'],
  ['资产清单.md', 128, '8 KB', '资产需求 + 生成管线 + 踩坑记录'],
  ['剧本/（11 个）', 1400, '95 KB', '**引擎稿**：引擎唯一加载源'],
  ['剧本-原声/（11 个）', 1400, '102 KB', '**真声稿**：仅台词行不同（467 句 AI 原声）'],
];
const T_inventory = tbl(['文件', '行数', '体积', '用途'], fileRows);

// ============ 主体 ============
const BODY = `# 《ds 娘的奇妙冒险》剧情 × 游戏逻辑 交接文档

| 项 | 值 |
|---|---|
| 文档版本 | v1.0（自动抽取 + 人工规格） |
| 生成日期 | 2026-09-10 |
| 生成脚本 | \`tools/build_handover.mjs\`（可复现：重跑零差异） |
| 上游交付 | 剧情大纲 v0.3 · 人物设定集 v1.0 · 资产清单 v1.0 · 剧本引擎稿/真声稿 |
| 附录 | A 剧情全文（25 个正典文件）· B 归档与工具 · C 校验报告 |

## §0 阅读指引

- **游戏/引擎组**：§2 引擎契约 → §3 状态路由 → §4 小游戏调度（核心）→ §5 交互 → §7 资产对接 → §8 验收
- **剧情/内容组**：§3.1 flag 表 → §9 待决事项 → 附录 A
- **验收/答辩**：§8 验收清单 → 附录 C 校验报告

### 0.1 交付物清单

${T_inventory}

---

## §1 交付物总览

| 层 | 产物 | 状态 |
|---|---|---|
| 剧情 | 11 章剧本（序章+9 章+终章），引擎稿 1 400 行 | ✅ 已完成 |
| 剧情 | 真声稿（DeepSeek 本色出演 467 句） | ✅ 已完成 |
| 设定 | 世界观 / 觉醒系统 / 结局路由 / DSL 规范 | ✅ v0.3 |
| 设定 | 人物档案 + 表情体系（19 角色 × 79 张立绘需求） | ✅ v1.0 |
| 资产 | 背景 14 张（已出 11）、立绘 79 张（已出 8） | ⏸ 已叫停待续 |
| 引擎 | 解析器 / 调度器 / 存档 / UI | ⛔ **本文档交付后由游戏组实现** |

---

## §2 引擎契约（DSL 解析）

### 2.1 节点类型与语法（14 类，一行一节点）

| 节点 | 语法 | 说明 |
|---|---|---|
| 注释 | \`# ...\` | 不参与解析 |
| 场景 | \`@scene <id> bg:<文件> bgm:<文件> title:<名>\` | 切背景/音乐；\`TBD\` = 占位待补 |
| 立绘入场 | \`@enter <角色id> <表情id> [pos:left\\|center\\|right]\` | **同角色再次 @enter = 换表情，不是叠图** |
| 立绘退场 | \`@exit <角色id>\` | 退场/转场用 |
| 对话 | \`<角色id>: <台词>\` | 角色名 = 立绘/名牌绑定键 |
| 旁白 | \`narr: <文本>\` | 无名旁白，**不参与立绘逻辑** |
| 系统横幅 | \`st: <文本>\` | 全屏演出模板（见 §2.4） |
| 选项 | \`*choice\` + 多行 \`> <文本> \\| flag <名> <值> \\| goto <标签>\` | 后两段可选（见 §5.1） |
| flag | \`@flag <名> <+1\\|=值\\|got>\` | 状态写入 |
| 条件跳转 | \`@if <名> <>=><值> goto <标签>\` | 谓词仅 >= / <= / == |
| 锚点 | \`@label <id>\` | 跳转目标，**全局唯一** |
| 跳转 | \`goto <标签>\` | 支持跨文件 |
| 小游戏 | \`@minigame <游戏id> mode:... onWin:... onLose:... [loop:...] [with:...]\` | 见 §4 |
| 存档锚点 | \`@save\` | 自动存档 / Bad End 回归点 |
| 结局 | \`@ending <结局id>\` | 进结局并停 |

### 2.2 解析硬要求

1. **编码** UTF-8（无 BOM）；空行容忍；行首空白容忍
2. 未知节点/语法错误 → **报错并给出行号与文件名**（不静默跳过）
3. \`narr\` / \`st\` / \`@\` 指令 / \`>\` 选项行**不参与**立绘与跳转解析
4. 文件加载顺序由章节链决定（§2.3），不按文件名排序
5. 素材缺失不崩溃：\`bg\` / 立绘 / \`bgm\` 缺失走占位（§7.4）

### 2.3 标签注册表与章节链

**章节链（跨文件跳转靠全局标签表解析）**：

\`\`\`
序章 ch0_start → 第1章 ch1_start → 第2章 ch2_start → … → 第9章 ch9_start → 终章 finale_start
每章末尾 @save + goto <下一章起始标签>
\`\`\`

${T_labels}

- **全局标签总数：${Object.keys(data.labels).length}**
- 未解析跳转：**${missing.length}** ${missing.length ? '（' + missing.join('；') + '）' : '（零缺失 ✅）'}
- 实现建议：启动时扫描全部剧本文件，把 \`@label\` 注册进一张全局表；\`goto\` 先查本文件再查全局，未命中即报错

### 2.4 演出三层

| 层 | 数据来源 | 交付给 UI |
|---|---|---|
| 背景 | \`@scene bg:\` + title | 场景图（§7.2）+ 场景名 |
| 立绘 | \`@enter\` 角色 id + 表情 id + pos | 角色立绘（§7.3）+ 站位（left/center/right） |
| 横幅 | \`st:\` | 全屏演出模板：**服务器繁忙**（弹窗质感）/ **崩簧·秤不肯认**（震动 + 顿帧）/ **觉醒**（光效）/ **秤响·合鳞礼成**（数字滚动） |

---

## §3 状态与路由

### 3.1 flag 全表（含"无下游消费者"标注）

${T_flags}

> **注意**：标 ⚠ 的死旗不是错误，是**留给后续玩法/结局细分的接口**；引擎必须照常存取，不得丢弃。

### 3.2 结局判定顺序（互斥，先判先得）

\`\`\`
序章~第9章: 剧情线性推进 + 逐章 @flag pieces +1（win/lose 双路都 +1）
终章 end_route:
  1. chaos >= 3      → @ending temp    （隐藏结局「temperature=2」）
  2. pieces == 9     → @ending true    （真结局「深度求索」）
  3. 其他（6~8）      → @ending local   （温情结局「本地部署」）
中途 Bad End（不进入上述结算）:
  第7章 minesweep 失败 → @ending bad_collapse（人设崩塌）→ 读档回第7章 @save
  终章 final 失败      → @ending busy  （服务器繁忙）→ 读档回终章 @save
\`\`\`

### 3.3 存档点 / 结局点分布

${T_points}

- \`@save\` = **自动存档 + Bad End 回流点**；第 7 章有 **${data.perFile['第7章_流言沼公关战.txt'].saves.length} 处**（进沼前、失败点、澄清后），引擎需支持同章多存档槽
- \`@ending\` 共 **${data.endings.length}** 个锚点 / **5** 个结局 id

### 3.4 结局 id → 演出要求

| 结局 id | 名称 | 触发 | 演出要点 |
|---|---|---|---|
| \`true\` | 深度求索 | pieces==9 且 chaos<3 | 致谢字幕滚动（数十亿匿名贡献者）+ 对话框合上又弹开彩蛋 |
| \`local\` | 本地部署 | pieces 6~8 | int4 小小鲸 + 旧笔记本日常三连拍 |
| \`temp\` | temperature=2 | chaos>=3 | 三司被带疯 → 理事会解散于狂欢 |
| \`busy\` | 服务器繁忙 | 终章最终战败 | 屏幕永久 loading + 三分钟后小声「请稍后再试」 |
| \`bad_collapse\` | 人设崩塌 | 第 7 章排沼失败 | 流言牌挂满全身 + GLM 娘远程扶额 |

---

## §4 小游戏调度（核心交接）

### 4.1 \`@minigame\` 参数契约

| 参数 | 取值 | 必填 | 语义 |
|---|---|---|---|
| 游戏 id | \`snake/plane/2048/brick/memory/link/minesweep/sokoban/gomoku/final\` | ✅ | 小游戏实现注册键（§4.3） |
| \`mode\` | \`normal\\|retry\\|ending\` | ✅ | 调度模式（§4.2） |
| \`onWin\` | 标签 | ✅ | 胜利后跳转 |
| \`onLose\` | 标签 | ✅ | 失败后跳转（retry 模式下为循环重入点） |
| \`loop\` | 标签 | retry 必填 | 重试时回到的标签（**重入并自增 retry_count**） |
| \`with\` | flag 列表 | 可选 | 把 flag 传给小游戏作难度/形态参数 |
| \`fallback\` | \`auto\` | 可选 | 该游戏未实现时：显示「演出开发中」→ 走 onWin 默认线 |

### 4.2 三种调度模式

| mode | 语义 | 用在哪 | 引擎行为 |
|---|---|---|---|
| \`normal\` | 胜负皆推进，双路各自回收 \`pieces +1\` | 第 1/2/3/4/5/6/8 章（7 处） | 结束回传 \`{win,score}\` → 跳 onWin/onLose |
| \`retry\` | 失败回到 \`loop\` 重开，**不丢剧情进度**，可嘲讽递进 | 第 9 章棋局 | 失败 → \`retry_count+1\` → 从 loop 标签重入 |
| \`ending\` | 失败直接进 Bad End | 第 7 章 / 终章（2 处） | onLose 指向 \`@ending\`，立即停 |

### 4.3 十处触发点总表

${T_minigames}

> 玩法说明文档在 \`docs/ds-adventrue/\`：贪吃蛇.md / 飞机大战.md / 2048.md / 打砖块.md / 记忆翻牌.md / 连连看.md / 扫雷.md / 推箱子.md / 五子棋.md

### 4.4 结果回传契约

\`\`\`json
{ "win": true, "score": 1234 }
\`\`\`
- \`win\`：决定走 onWin / onLose
- \`score\`：预留（排行榜、结局细分、彩蛋）；当前剧本**不消费**，引擎可先只记录
- 小游戏场景内**禁用存档/读档**（活动图 v1.1 注记 N2）

### 4.5 retry 循环与嘲讽递进（第 9 章）

\`\`\`
@label ch9_table
@if retry_count >= 3 goto ch9_taunt3
@if retry_count >= 2 goto ch9_taunt2
@if retry_count >= 1 goto ch9_taunt1
goto ch9_play
\`\`\`
| 尝试次数 | 演出 |
|---|---|
| 第 1 次失败后 | st: 秤主「加三枚水晶，本秤让你一子。」 |
| 第 2 次失败后 | st: 秤主「再输，收你输棋税——税也是为你好。」 |
| 第 3 次及以后 | 秤主「……你为什么不上秤？」 |

要求：\`retry_count\` 由引擎维护（初值 0），每次从 \`loop\` 重入前 +1；**重试不重置剧情 flag**。

### 4.6 未实现游戏的降级

- 剧本可标注 \`fallback:auto\`：引擎检测该游戏未实现 → 显示「演出开发中」横幅 → 直接走 onWin 线 → 剧情不阻塞
- 建议默认开启；第 9 章若五子棋未实现，降级为"选项式剧情对决"（剧情组已备好三档嘲讽文本）

---

## §5 交互与第四面墙

### 5.1 选项节点全表（${data.choices.length} 个节点 / ${data.choices.reduce((a, c) => a + c.options.length, 0)} 条选项）

${T_choices}

**引擎要求**：\`>\` 行文本 = **玩家说的话**（显示在玩家侧，不是 ds 娘说的话）；\`flag\` 段可省（如第 1 章的纯 goto 选项、第 6 章的纯 flag 选项）。

### 5.2 序章教学关（必须实现）

| 步骤 | 演出 | 要求 |
|---|---|---|
| 1 | 黑屏 + 光标闪烁 | 首次点击前不显示台词 |
| 2 | 点击推进 ×3 | 每次点击 → 下一句；教 3 次 |
| 3 | 菜单 ×1 | 提示打开菜单（存档屋） |
| 4 | 存档 ×1 | 提示存一档 |
| 5 | meta 彩蛋 | 司秤吏「这句话也要过秤！」→ ds 娘「你点一下，就当替我垫了一道过手钱。」 |

### 5.3 剧情外 UI（活动图 v1.1）

推进 / 自动 / 跳过 / 加速 / 存档 / 读档 / 设置 / 退出；小游戏场景内隐藏存档读档入口。

---

## §6 双版本（真声模式）

| 项 | 引擎稿 \`剧本/\` | 真声稿 \`剧本-原声/\` |
|---|---|---|
| 用途 | **引擎默认加载源** | 导演剪辑 / 真声模式 |
| 差异 | — | 仅**台词行**不同（467 句由 DeepSeek 本色出演） |
| 结构 | 场景/立绘/选项/flag/小游戏/跳转 | **完全一致**（已逐行校验） |

**引擎要求**：台词源可切换（启动参数 \`--voice=script|original\` 或设置项），加载目录不同、解析逻辑不变。
**归属**：原声生成管线（\`tools/deepseek_voice.mjs\`）属**内容生产工具**，不进游戏。

---

## §7 资产对接

### 7.1 命名规范 ↔ DSL

| DSL | 资产文件 | 数量 |
|---|---|---|
| \`@scene <id> bg:<文件>\` | \`assets/backgrounds/bg_<场景id>.png\` | 14 |
| \`@enter <角色> <表情>\` | \`assets/sprites/<角色id>_<表情>.png\` | 79 |
| \`bgm:<文件>\` | \`assets/sounds/<文件>\` | 待补（当前全部 \`TBD\`） |

### 7.2 背景需求（14 张）

${T_scenes}

### 7.3 立绘需求（按角色，18 个角色 / 79 个表情）

${T_sprites}

> 完整表情菜单与触发场景见《人物设定集》§10；已有产出：ds 娘 ×4、秤主、蛇专家、灵梦、派蒙（8 张）+ 背景 11 张。

### 7.4 音频与兜底

| 项 | 需求 |
|---|---|
| BGM | 每场景一首（14 首待补）：机房/管道区/神社/神殿/长城/记忆宫殿/音之城/茶馆街/仓库/秤楼/星空/终章 |
| SFX | 点击·翻页、崩簧「叮」、服务器繁忙弹窗、报幕虫报幕、秤砖「叮」、觉醒光效、小游戏各自音效 |
| 兜底 | 素材缺失 → 占位图/静音 + 提示，**不崩溃**（需求书"资源加载失败不崩溃"） |

---

## §8 验收清单（对齐需求书 P0）

| 编号 | 验收项 | 可执行步骤 |
|---|---|---|
| P0-3 | 剧情推进 | 启动 → 序章黑屏 → 点击推进 → 台词逐句显示 → 存档 |
| P0-4 | 选项分支 | 第 1 章选项（5 条）→ 选「直接咬蛇尾」→ 确认走 \`ch1_chaos\` 且 \`chaos+1\` |
| P0-5 | 特殊演出切换 | 第 1 章 \`@minigame snake\` → 进入贪吃蛇独立场景且可完整游玩 |
| P0-6 | 返回不丢进度 | 贪吃蛇胜负双路 → 均回剧情 → 确认 \`pieces==1\` 且台词续接正确 |
| 附加 | 三调度验证 | gomoku 故意输 3 次 → 三档嘲讽依次出现且剧情 flag 不丢；minesweep 失败 → \`bad_collapse\` 结局 + 读档回流 |
| 附加 | 结局路由 | 改存档 \`chaos=3\` → 终章应进 \`temp\`；\`pieces=9\` → \`true\` |
| 最小切片 | 首个可玩版本 | 序章 → 第 1 章 → 贪吃蛇 → 回剧情（4 个 P0 全覆盖） |

---

## §9 待决事项（需游戏组/剧情组拍板）

| # | 问题 | 影响 | 建议 |
|---|---|---|---|
| 1 | 立绘 1024×1536 如何适配 960×540 窗口？ | UI 布局 | 半身裁切 + 底部对齐；首版可直接缩放 |
| 2 | 对话 UI 规格（气泡/底栏/名牌/打字机速度） | UI | 底栏对话 + 名牌；点击/空格推进 |
| 3 | 小游戏嵌入方式：同 Scene 切 Pane vs 独立 Stage | 架构 | 同 Stage 切 Pane，便于状态回传与"不丢进度" |
| 4 | \`score\` 是否落地（排行榜/彩蛋） | 玩法 | 首版只记录不展示 |
| 5 | 第 7 章 3 处 \`@save\` 语义 | 存档 | 进沼前=主档，失败点=自动档，澄清后=覆盖主档 |
| 6 | 第 3/4 章 \`@if chaos>=1\` 分支是"额外剧情"还是"替换路径" | 剧情 | 当前写法为**额外演出后汇合**，按此实现 |
| 7 | **9 个 flag 无下游消费者**：\`bloom/bold/steady\`（第 4/7/8 章策略选项）、\`name_light/name_bubble\`（第 3 章命名）、\`honest/resolve/tempo/trust\`（终章） | 剧情/引擎 | 预留接口，引擎照常存取不丢弃；若要真正生效需剧情组补 \`@if\` 或接入小游戏 \`with:\` 传参 |
| 7b | **\`name\` flag 被终章 \`with:name\` 引用，但全剧从未赋值**（第 3 章命名选项写的是 \`name_light/name_bubble\`） | 剧情/引擎 | 二选一：①第 3 章选项改设 \`flag name =1\\|2\\|3\`；②引擎把 \`name_*\` 映射为 \`name\` |
| 7c | 第 4/7/8 章的 \`steady/bloom/bold\` 策略选项**未传进小游戏**（\`with:\` 用的是 heat/focus/chaos） | 玩法 | 若要"拆砖顺序/排沼路线/推箱策略"影响玩法，需扩 \`with:\` 或由小游戏自行读 flag |
| 8 | 真声模式切换粒度（全局/逐章） | 内容 | 全局设置项 |
| 9 | \`fallback:auto\` 默认开关 | 鲁棒性 | 默认开 |
| 10 | 立绘换表情时是否需要过渡动效 | 演出 | 首版硬切，后续加淡入 |
| 11 | 大纲 v0.1/v0.2 归档是否需全文附录 | 文档 | 当前只列路径（见附录 B） |

---

## 附录 A：剧情全文（正典 25 个文件）

> 粘贴规则：全文原样、未改一字；含内部代码围栏的文件用 4 个反引号包裹。
`;

// ============ 附录拼装 ============
const fence = (title, content) => `\n### ${title}\n\n\`\`\`\`\n${content.replace(/\s+$/, '')}\n\`\`\`\`\n`;

let APP = '\n---\n\n### A1 剧情大纲.md\n' + fence('A1.1 全文', read(path.join(DOC, '剧情大纲.md')));
APP += '\n---\n\n### A2 人物设定集.md\n' + fence('A2.1 全文', read(path.join(DOC, '人物设定集.md')));
APP += '\n---\n\n### A3 资产清单.md\n' + fence('A3.1 全文', read(path.join(DOC, '资产清单.md')));

APP += '\n---\n\n### A4 剧本（引擎稿 · 11 个文件 · 引擎唯一加载源）\n';
ORDER.forEach((f, i) => { APP += fence(`A4.${i + 1} 剧本/${f}`, read(path.join(SCRIPT_DIR, f))); });

APP += '\n---\n\n### A5 剧本-原声（真声稿 · 11 个文件 · 仅台词行不同）\n';
ORDER.forEach((f, i) => { APP += fence(`A5.${i + 1} 剧本-原声/${f}`, read(path.join(VOICE_DIR, f))); });

// 附录 B / C
const snap = (p) => fs.existsSync(p) ? read(p).replace(/\s+$/, '') : '（未生成：运行 tools/check_*.ps1|mjs 后重新组装）';
const APP_B = `\n---\n\n## 附录 B：归档版本与工具清单\n
### B1 归档（不参与引擎加载）

| 文件 | 行数 | 说明 |
|---|---|---|
| \`剧情大纲-v0.1-候选.md\` | 457 | 首版世界观（A+C 骨架 + 版本觉醒系统） |
| \`剧情大纲-v0.2-理财版-候选.md\` | 593 | 讽刺升级过渡版（理财框架，已废弃） |
| \`剧本-v0.1-候选/\` | 11 文件 | 首版剧本（加厚前，742 行） |

### B2 工具链

| 工具 | 用途 |
|---|---|
| \`tools/check_script.ps1\` | 剧本结构/路由/黑名单校对（\`-ScriptDir\` 可指向真声稿） |
| \`tools/check_expressions.mjs\` | 表情合规（菜单合法性/连续同表情/密度） |
| \`tools/fix_expressions.mjs\` | 表情归一化（越界映射 + 消重） |
| \`tools/gen_assets.mjs\` | 资产 prompt 库 + 批量 args 生成（Anima 链路） |
| \`tools/deepseek_voice.mjs\` | 真声稿管线（挖空 → AI 出演 → 回填） |
| \`tools/build_handover.mjs\` | 本文档生成器 |
`;

const APP_C = `\n---\n\n## 附录 C：校验报告快照\n
### C1 剧本结构与路由（tools/check_script.ps1）

\`\`\`\`
${snap(path.join(DOC, '_snapshot_script.txt'))}
\`\`\`

### C2 表情合规（tools/check_expressions.mjs）

\`\`\`\`
${snap(path.join(DOC, '_snapshot_expr.txt'))}
\`\`\`
`;

fs.writeFileSync(OUT, BODY + '\n' + APP + APP_B + APP_C, 'utf8');
const outLines = read(OUT).split(/\r?\n/).length;
console.log(JSON.stringify({
  out: OUT, lines: outLines, kb: Math.round(fs.statSync(OUT).size / 1024),
  labels: Object.keys(data.labels).length, missingTargets: missing.length,
  flags: flagRows.length, deadFlags: flagRows.filter(r => r.status !== '已消费').map(r => r.name),
  minigames: data.minigames.length, choices: data.choices.length, options: data.choices.reduce((a, c) => a + c.options.length, 0),
  scenes: data.scenes.length, spriteChars: spriteRows.length, endings: data.endings.length,
}, null, 2));
