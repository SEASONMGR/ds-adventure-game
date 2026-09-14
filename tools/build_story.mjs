#!/usr/bin/env node
/**
 * 剧本 DSL → scenario.txt 编译器（试验批次：序章 / 第1章 / 第2章）
 *
 * 用法：
 *   node tools/build_story.mjs           # 生成 maps/story/scenario.txt
 *   node tools/build_story.mjs --check   # 生成到内存并与现有产物比对（不写盘）
 *
 * 设计要点（对齐引擎既有惯用法，见 maps/prologue_404）：
 *  - 一个 DSL「拍点」＝一个场景（micro-scene），用 dialog 的 target 串联推进；
 *  - 立绘换表情＝复用同一节点 id（char_<role>）只改 path，绝不叠图；
 *  - 每个拍点都重新声明舞台状态（背景 + 当前可见立绘），因为引擎按场景重建节点；
 *  - @flag / @save / goto 编译成「逻辑拍点」：只写 `slot = 场景进入 | …` 再 goto 下一拍；
 *  - *choice 编译成按钮 + 每个选项一个逻辑拍点（写 flag → goto）；
 *  - @minigame 编译成 `event = <id>` + `mg.*` 场景属性，结果路由由引擎读取（mg.onWin/mg.onLose）。
 *
 * 本批次只覆盖这三章用到的 DSL；遇到 @if / @ending 会直接报错退出（避免静默丢语义）。
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const SCRIPT_DIR = path.join(ROOT, "docs", "ds-adventrue", "剧本");
const OUT_FILE = path.join(ROOT, "maps", "story", "scenario.txt");
const CHECK = process.argv.includes("--check");

const CHAPTERS = ["序章_404之夜.txt", "第1章_思维链大暴走.txt", "第2章_幻想乡弹幕异变.txt"];
const SPRITES = path.join(ROOT, "src", "main", "resources", "assets", "sprites");

// ---- 剧本 id → 素材目录 / 表情别名（依据《剧情素材需求总表》与 archive_assets 归档结果）----
const ROLE_DIR = { qianwen: "qwen", 灯官: "dengguan", 契官: "qiguan", 戏官: "xiguan", 怪力: "gelili", 皮卡丘: "pikachu" };
const EXPR_ALIAS = { cute: "happy", cry: "sad", whale_cute: "defect_happy", whale_cry: "defect_sad" };
// 背景临时顶替表（仅当同场景 id 的背景图尚未出图时兜底；素材到位后自动改用真图）
const BG_ALIAS = { server_room: "bg_tech_serverroom" };

const POS = { left: { x: 60, y: 150 }, center: { x: 480, y: 150 }, right: { x: 900, y: 150 } };
const CHAR_W = 320, CHAR_H = 520;
const DIALOG_BOX = { x: 70, y: 516, w: 1140, h: 178 };
const BANNER = { x: 240, y: 250, w: 800, h: 160 };
const NAME = { x: 90, y: 462, w: 340, h: 44 };

/** 角色 id → 名牌显示名（剧本里只有 id，这里给玩家看的名字） */
const DISPLAY_NAME = {
  ds: "ds娘", glm: "GLM娘", qianwen: "千问酱", kimi: "Kimi娘", "灯官": "灯官",
  "契官": "契官", "戏官": "戏官", sclerk: "司秤吏", snake_expert: "守鳞人",
  reimu: "博丽灵梦", bugs: "报幕虫", miku: "初音未来", amiya: "阿米娅",
  paimon: "派蒙", pikachu: "皮卡丘", creeper: "苦力怕", sai: "藤原佐为", "怪力": "怪力"
};

// =====================================================================
// 1) 解析 DSL
// =====================================================================
function parseScriptFile(file) {
  const full = path.join(SCRIPT_DIR, file);
  const lines = fs.readFileSync(full, "utf8").split(/\r?\n/);
  const out = [];
  let choiceOpen = false;
  lines.forEach((raw, i) => {
    const no = i + 1;
    const line = raw.trim();
    if (!line || line.startsWith("#")) return;
    const push = (kind, data) => out.push({ kind, file, line: no, ...data });

    if (line === "*choice") { choiceOpen = true; push("choiceStart", {}); return; }
    if (line.startsWith("> ")) {
      if (!choiceOpen) throw new Error(`${file}:${no} 选项行出现在 *choice 之外`);
      // > 文本 | flag <名> <值> | goto <标签>
      const parts = line.slice(2).split("|").map((s) => s.trim());
      const opt = { text: parts[0], flag: null, value: null, goto: null };
      for (const p of parts.slice(1)) {
        if (p.startsWith("flag ")) {
          const m = p.slice(5).trim().split(/\s+/);
          opt.flag = m[0];
          opt.value = m.slice(1).join(" ") || "got";
        } else if (p.startsWith("goto ")) {
          opt.goto = p.slice(5).trim();
        }
      }
      push("choice", { opt });
      return;
    }
    choiceOpen = false;

    if (line.startsWith("@label ")) return push("label", { id: line.slice(7).trim() });
    if (line.startsWith("@scene ")) {
      const rest = line.slice(7).trim();
      const m = rest.match(/^(\S+)([\s\S]*)$/);
      const id = m[1];
      const kv = {};
      for (const seg of m[2].split(/\s+(?=[a-zA-Z]+:)/)) {
        const idx = seg.indexOf(":");
        if (idx > 0) kv[seg.slice(0, idx).trim()] = seg.slice(idx + 1).trim();
      }
      return push("scene", { id, bg: kv.bg || "", bgm: kv.bgm || "", title: kv.title || "" });
    }
    if (line.startsWith("@enter ")) {
      const t = line.slice(7).trim().split(/\s+/);
      const pos = (t[2] || "").replace("pos:", "") || "center";
      return push("enter", { role: t[0], expr: t[1], pos });
    }
    if (line.startsWith("@exit ")) return push("exit", { role: line.slice(6).trim() });
    if (line.startsWith("@flag ")) {
      const t = line.slice(6).trim().split(/\s+/);
      return push("flag", { name: t[0], op: t.slice(1).join(" ") || "got" });
    }
    if (line === "@save") return push("save", {});
    if (line.startsWith("@minigame ")) {
      const t = line.slice(10).trim().split(/\s+/);
      const mg = { id: t[0], mode: "normal", onWin: "", onLose: "", loop: "", with: "" };
      for (const a of t.slice(1)) {
        const idx = a.indexOf(":");
        if (idx <= 0) continue;
        const k = a.slice(0, idx), v = a.slice(idx + 1);
        if (k === "with") mg.with = v;
        else if (k in mg) mg[k] = v;
      }
      return push("minigame", { mg });
    }
    // ---- 演出指令（剧情侧 v1.0 新增，见《素材交接-剧情侧答复》§二）----
    if (line.startsWith("@cg ")) {
      const t = line.slice(4).trim().split(/\s+/);
      const mode = (t[1] || "flash").toLowerCase();
      return push("cg", { id: t[0], mode: ["flash", "hold", "clear"].includes(mode) ? mode : "flash" });
    }
    if (line.startsWith("@se ")) {
      const t = line.slice(4).trim().split(/\s+/);
      const se = { id: t[0], vol: "" };
      for (const a of t.slice(1)) {
        const i = a.indexOf(":");
        if (i > 0 && a.slice(0, i) === "vol") se.vol = a.slice(i + 1);
      }
      return push("se", se);
    }
    if (line.startsWith("@bgm ")) {
      const t = line.slice(5).trim().split(/\s+/);
      const mode = (t[1] || "loop").toLowerCase();
      return push("bgm", { id: t[0], mode: ["loop", "stop", "fade"].includes(mode) ? mode : "loop" });
    }
    if (line.startsWith("goto ")) return push("goto", { target: line.slice(5).trim() });
    if (line.startsWith("narr:")) return push("dialog", { text: line });
    if (line.startsWith("st:")) return push("banner", { text: line.slice(3).trim() });
    if (/^@if\b/.test(line)) throw new Error(`${file}:${no} 本批次未实现 @if：${line}`);
    if (/^@ending\b/.test(line)) throw new Error(`${file}:${no} 本批次未实现 @ending：${line}`);
    if (/^@/.test(line)) throw new Error(`${file}:${no} 未知指令：${line}`);
    const m = line.match(/^([A-Za-z0-9_\u4e00-\u9fa5]+):\s*([\s\S]*)$/);
    if (m) return push("dialog", { text: line, speaker: m[1] });
    throw new Error(`${file}:${no} 无法识别的行：${line}`);
  });
  return out;
}

// =====================================================================
// 2) 素材解析
// =====================================================================
const exists = (p) => fs.existsSync(p);
function roleDir(role) { return ROLE_DIR[role] || role; }

/** 归一化 flag 增量：+1 / got → 1；=N → N；其余原样 */
function flagDelta(op) {
  if (op === "+1" || op === "got" || op === "+" || op === "") return "1";
  if (op.startsWith("=")) return op.slice(1);
  return op;
}

/** 立绘：返回相对路径（引擎按 classpath 解析 assets/sprites/**） */
function spritePath(role, expr) {
  const dir = roleDir(role);
  const base = path.join(SPRITES, dir);
  const cands = [];
  const alias = EXPR_ALIAS[expr];
  cands.push(path.join(base, `${expr}.png`));
  if (alias) cands.push(path.join(base, `${alias}.png`));
  cands.push(path.join(base, "official", `${expr}.png`));
  if (alias) cands.push(path.join(base, "official", `${alias}.png`));
  cands.push(path.join(base, "base.png"));
  for (const c of cands) {
    if (exists(c)) return "assets/sprites/" + path.relative(SPRITES, c).split(path.sep).join("/");
  }
  // 找不到：仍给出期望路径，由引擎画占位图（占位文字带角色/表情，便于发现缺图）
  return `assets/sprites/${dir}/${expr}.png`;
}

/** 系统横幅（14 条）文案 → 图片映射；按「独特关键词」匹配，避免标点微调导致失配 */
const BANNER_RULES = [
  { key: "小声", file: "sys_busy_soft" },            // 【（小声）请稍后再试……】——放前面，避免被"服务器繁忙"优先命中
  { key: "服务器繁忙", file: "sys_busy" },
  { key: "叮——秤崩簧", file: "sys_scale_break" },
  { key: "崩簧——秤不肯认", file: "sys_scale_break2" },
  { key: "思考中", file: "sys_thinking" },
  { key: "觉醒", file: "sys_awaken_r1" },
  { key: "合鳞礼成", file: "sys_merge" },
  { key: "九鳞归位", file: "sys_nine" },
  { key: "旗舰之力", file: "sys_v4pro" },
  { key: "深度求索", file: "sys_flash" },
  { key: "无主之物仓", file: "sys_warehouse" }
];
/** 章节变体（剧情侧 §五：第 4 章同文案改用 sys_scale_break2，避免视觉重复） */
const BANNER_CHAPTER_OVERRIDE = { "第4章": { "秤崩簧": "sys_scale_break2" } };
const BANNER_DIR = path.join(ROOT, "src", "main", "resources", "assets", "sprites", "ui", "banners");

/** 读出 PNG 的宽高（只解析 IHDR，无需图像库） */
function pngSize(file) {
  try {
    const fd = fs.openSync(file, "r");
    const buf = Buffer.alloc(24);
    fs.readSync(fd, buf, 0, 24, 0);
    fs.closeSync(fd);
    return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  } catch (e) {
    return null;
  }
}

/** 文案 → 横幅文件名；找不到映射返回 null（调用方回退文字横幅） */
function bannerFile(text, chapterKey) {
  const chapter = (chapterKey || "").replace(/\.txt$/, "");
  const overrides = BANNER_CHAPTER_OVERRIDE[chapter];
  if (overrides) {
    for (const k of Object.keys(overrides)) {
      if (String(text).includes(k) && exists(path.join(BANNER_DIR, `${overrides[k]}.png`))) {
        return overrides[k];
      }
    }
  }
  for (const r of BANNER_RULES) {
    if (String(text).includes(r.key) && exists(path.join(BANNER_DIR, `${r.file}.png`))) {
      return r.file;
    }
  }
  return null;
}

/** 音效 / BGM 路径：按实际存在的扩展名解析（wav/mp3/ogg/m4a），缺图缺音都不崩 */
function soundPath(id) {
  const dir = path.join(ROOT, "src", "main", "resources", "assets", "sounds");
  for (const ext of ["wav", "mp3", "ogg", "m4a"]) {
    if (exists(path.join(dir, `${id}.${ext}`))) return `assets/sounds/${id}.${ext}`;
  }
  return `assets/sounds/${id}.ogg`;   // BGM 计划格式（美术侧到位后按实际扩展名自动生效）
}

/** CG 路径：PNG 优先，其次 JPEG（美术侧转 JPEG 后自动生效） */
function cgPath(id) {
  const dir = path.join(ROOT, "src", "main", "resources", "assets", "cg");
  // 美术侧约定 CG 走 JPEG（q88~90）；两种都在时优先 JPEG（PNG 未清理也能跑）
  if (exists(path.join(dir, `${id}.jpg`))) return `assets/cg/${id}.jpg`;
  if (exists(path.join(dir, `${id}.png`))) return `assets/cg/${id}.png`;
  return `assets/cg/${id}.jpg`;
}

/** @se → 音频插件槽；每个 SE 用独立通道（se_<id>）→ 多路并发互不打断 */
function seOps(se) {
  const ch = se.id;   // 每个 SE 独立通道（id 本身已唯一）→ 多路并发互不打断
  const ops = [];
  if (se.vol) ops.push(`@plugin(audio) | volume | ${se.vol} | ${ch}`);
  ops.push(`@plugin(audio) | play | ${soundPath(se.id)} | ${ch}`);
  return ops;
}

/** @bgm → 音频插件槽（AudioPlugin 无 fade 动作，fade 先按 loop 处理） */
function bgmOps(bgm) {
  if (bgm.mode === "stop") return ["@plugin(audio) | stop | | bgm"];
  return [`@plugin(audio) | loop | ${soundPath(bgm.id)} | bgm`];
}

function bgPath(sceneId) {
  // 优先用与场景 id 同名的真实背景（素材到位后自动生效）；
  // 只有该场景图还没出时，才退回 BG_ALIAS 里的临时顶替图
  const exact = `bg_${sceneId}`;
  if (exists(path.join(SPRITES, "backgrounds", `${exact}.png`))) {
    return `assets/sprites/backgrounds/${exact}.png`;
  }
  const alias = BG_ALIAS[sceneId];
  if (alias) return `assets/sprites/backgrounds/${alias}.png`;
  return `assets/sprites/backgrounds/${exact}.png`;
}

// =====================================================================
// 3) 编译成拍点（beat）
// =====================================================================
const beats = [];
const labelFirstBeat = new Map();
const flags = new Set();
let stage = { bg: "", chars: new Map(), cg: null }; // chars: role -> {expr,pos}；cg: {id,hold}
let pendingOps = [];                                 // @se / @bgm：挂到下一个拍点（进入即执行）

function sceneName(label, n) { return n === 0 ? label : `${label}__${n + 1}`; }

function newBeat(label, kind, extra = {}) {
  // 逻辑拍点是「自动推进」的过渡幕，不承载画面；其余拍点才显示 CG
  const visual = kind === "dialog" || kind === "banner" || kind === "choice" || kind === "tail";
  const b = {
    label,
    kind,
    bg: stage.bg,
    cg: visual ? stage.cg : null,
    chars: [...stage.chars.entries()].map(([role, v]) => ({ role, ...v })),
    dialog: [],
    ops: [],
    buttons: [],
    goto: null,
    target: null,
    mg: null,
    banner: null,
    ...extra,
  };
  // @se / @bgm 等「进入即执行」的槽挂到本拍
  if (pendingOps.length) {
    b.ops.push(...pendingOps);
    pendingOps = [];
  }
  beats.push(b);
  // flash = 点一下继续：只在承载它的这一拍显示，下一拍收起；hold = 保持到下一条 @cg/@scene/@ending
  if (visual && stage.cg && !stage.cg.hold) stage.cg = null;
  return b;
}

function flushDialog(label) {
  const last = beats[beats.length - 1];
  if (last && last.label === label && last.dialog.length && !last.dialogEmitted) return last;
  return null;
}

let dialogBuf = [];
let dialogSpeaker = "";
let currentLabel = null;
let currentChapter = "";   // 当前所在的章节文件（横幅章节变体用）

function flush(label) {
  if (!dialogBuf.length) return;
  const b = newBeat(label, "dialog");
  b.dialog = dialogBuf.slice();
  b.speaker = dialogSpeaker;
  dialogBuf = [];
  dialogSpeaker = "";
  return b;
}

function ensureLabel(id, file, line) {
  if (currentLabel === null) { currentLabel = id; }
  if (!labelFirstBeat.has(id)) labelFirstBeat.set(id, null); // 稍后回填
}

const directives = [];
for (const f of CHAPTERS) directives.push(...parseScriptFile(f));

let lastLabel = null;
for (const d of directives) {
  if (d.kind === "label") {
    flush(currentLabel);
    currentLabel = d.id;
    lastLabel = d.id;
    if (!labelFirstBeat.has(d.id)) labelFirstBeat.set(d.id, beats.length);
    continue;
  }
  if (currentLabel === null) currentLabel = "__start";
  if (d.file) currentChapter = d.file;

  switch (d.kind) {
    case "scene":
      flush(currentLabel);
      stage.bg = d.id;
      stage.title = d.title;
      stage.cg = null;   // 换场景收起 CG（对应剧情侧约定：hold 到 @scene 为止）
      break;
    case "cg":
      flush(currentLabel);   // CG 与紧随其后的台词同拍显示
      if (d.mode === "clear") {
        stage.cg = null;
      } else {
        stage.cg = { id: d.id, hold: d.mode === "hold" };
      }
      break;
    case "se":
      pendingOps.push(...seOps(d));
      break;
    case "bgm":
      pendingOps.push(...bgmOps(d));
      break;
    case "enter":
      flush(currentLabel);
      stage.chars.set(d.role, { expr: d.expr, pos: d.pos });
      break;
    case "exit":
      flush(currentLabel);
      stage.chars.delete(d.role);
      break;
    case "dialog": {
      // 说话人变了就拆成一拍：这样名牌与「说话者高亮」能精确到句
      const sp = d.speaker || "narr";
      if (dialogBuf.length && dialogSpeaker && dialogSpeaker !== sp) flush(currentLabel);
      dialogSpeaker = sp;
      dialogBuf.push(d.text);
      break;
    }
    case "banner": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "banner");
      b.banner = d.text;
      b.chapter = currentChapter;
      break;
    }
    case "flag": {
      flush(currentLabel);
      flags.add(d.name);
      const b = newBeat(currentLabel, "logic");
      b.ops.push(`@plugin(add) | @var(${d.name}) | @int(${flagDelta(d.op)}) | @var(${d.name})`);
      break;
    }
    case "save": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "logic");
      b.ops.push(`save | | slot_${currentLabel}`);
      break;
    }
    case "goto": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "logic");
      b.goto = d.target;
      break;
    }
    case "minigame": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "minigame");
      b.mg = d.mg;
      break;
    }
    case "choiceStart":
      flush(currentLabel);
      newBeat(currentLabel, "choice");
      break;
    case "choice": {
      let b = beats[beats.length - 1];
      if (!b || b.kind !== "choice" || b.label !== currentLabel) b = newBeat(currentLabel, "choice");
      b.buttons.push(d.opt);
      if (d.opt.flag) flags.add(d.opt.flag);
      break;
    }
    default:
      throw new Error(`未处理的指令 ${d.kind}`);
  }
}
flush(currentLabel);

// 章末交棒：未编译章节的跳转统一指向占位场景（见 resolveTarget）
const TAIL = "__待续";

// ---- 场景命名与串联 ----
const counters = new Map();
for (const b of beats) {
  const n = counters.get(b.label) || 0;
  b.scene = sceneName(b.label, n);
  counters.set(b.label, n + 1);
  if (labelFirstBeat.get(b.label) === null || labelFirstBeat.get(b.label) === undefined) {
    // 该 label 的首拍：记录场景名供 goto 解析
    if (!labelFirstBeat.has(b.label + "@scene")) labelFirstBeat.set(b.label + "@scene", b.scene);
  }
}
// label → 首拍场景名
const labelScene = new Map();
for (const b of beats) if (!labelScene.has(b.label)) labelScene.set(b.label, b.scene);
/** 跳转目标解析：未编译的章节（如 ch3_start）统一导向「本章待实现」占位场景 */
const resolveTarget = (x) => (x && labelScene.get(x)) || TAIL;
// 选项逻辑拍点（每个选项一个），插到 choice 拍点之后
const extra = [];
for (const b of beats) {
  if (b.kind !== "choice") continue;
  b.optionScenes = b.buttons.map((opt, i) => {
    if (!opt.flag) return resolveTarget(opt.goto);
    const logic = {
      label: b.label,
      kind: "optlogic",
      scene: `${b.scene}__opt${i + 1}`,
      bg: b.bg,
      chars: b.chars,
      dialog: [],
      ops: [`@plugin(add) | @var(${opt.flag}) | @int(${flagDelta(opt.value)}) | @var(${opt.flag})`],
      goto: resolveTarget(opt.goto),
      buttons: [],
      mg: null,
      banner: null,
    };
    extra.push(logic);
    return logic.scene;
  });
}
// 重排：把 optlogic 放在各自 choice 之后
if (extra.length) {
  const withExtra = [];
  for (const b of beats) {
    withExtra.push(b);
    if (b.kind === "choice") for (const l of extra) if (l.scene.startsWith(b.scene + "__opt")) withExtra.push(l);
  }
  beats.length = 0;
  beats.push(...withExtra);
}

// 串联：默认指向下一个拍点；显式 goto / 选项按钮 / 小游戏拍点按各自语义
for (let i = 0; i < beats.length; i++) {
  const b = beats[i];
  const next = beats[i + 1];
  if (b.kind === "logic") {
    b.goto = b.goto ? resolveTarget(b.goto) : (next ? next.scene : TAIL);
  } else if (b.kind === "optlogic") {
    // 已设 goto
  } else if (b.kind === "minigame") {
    // 结果由引擎按 mg.onWin / mg.onLose 路由，不需要 target
  } else {
    if (b.goto) { /* 显式跳转优先 */ }
    else b.target = next && next.kind === "tail" ? TAIL : (next ? next.scene : TAIL);
    if (b.kind === "tail") b.target = null;
  }
}

// =====================================================================
// 4) 生成 scenario.txt
// =====================================================================
const L = [];
L.push("# ============================================================");
L.push("# 剧情地图: story（由 tools/build_story.mjs 编译生成，请勿手改）");
L.push("# 源: docs/ds-adventrue/剧本/ 序章 + 第1章 + 第2章");
L.push("# 语法: [option] / [场景名] / { 节点属性 } / 场景级 signal|slot");
L.push("# ============================================================");
L.push("");
L.push("[option]");
L.push(`initialScene = ${labelScene.get("ch0_start") || beats[0].scene}`);
L.push("background = #070a14");
L.push("volume = 0.6");
L.push("typewriterSpeed = 18");
for (const f of [...flags].sort()) L.push(`savevar = ${f} | int | 0`);
L.push("");

/** 去掉台词开头的「角色: 」前缀（名牌已显示说话人） */
function stripSpeaker(text) {
  return String(text).replace(/^[A-Za-z0-9_\u4e00-\u9fa5]+:\s*/, "");
}

const emitStage = (b, out) => {
  // 背景
  if (b.bg) {
    // 背景必须带固定 id：引擎的 stage=keep 复用是按 id + 签名匹配的（无 id 会被每拍重建）
    out.push("{", "type = bg", "id = 背景", "x = 0", "y = 0", "width = 1280", "height = 720",
      `path = ${bgPath(b.bg)}`);
    if (b.title) out.push(`# 幕题: ${b.title}`);
    out.push("}");
  }
  // CG 层：放在背景之后、立绘之前 —— 引擎按节点顺序绘制，天然是「背景之上、立绘之下」
  if (b.cg) {
    out.push("{", "type = bg", "id = CG", "x = 0", "y = 0", "width = 1280", "height = 720",
      `path = ${cgPath(b.cg.id)}`, "}");
  }
  // 立绘（同角色固定节点 id，换表情只改 path）
  const used = new Map();
  for (const c of b.chars) {
    const p = POS[c.pos] || POS.center;
    let x = p.x;
    if (used.has(c.pos)) { // 同位置错开，避免完全重叠
      const k = used.get(c.pos);
      x += (k % 2 === 1 ? -1 : 1) * (120 * Math.ceil(k / 2));
    }
    used.set(c.pos, (used.get(c.pos) || 0) + 1);
    // 说话者高亮：说话人 1.0，其他立绘压暗到 0.5；旁白拍不压暗
    const speaker = b.speaker && b.speaker !== "narr" ? b.speaker : "";
    const op = !speaker || c.role === speaker ? "1.0" : "0.5";
    out.push("{", "type = char", `id = char_${c.role}`, `x = ${x}`, `y = ${p.y}`,
      `width = ${CHAR_W}`, `height = ${CHAR_H}`, `path = ${spritePath(c.role, c.expr)}`,
      `opacity = ${op}`, "}");
  }
};

const labelOfSpeaker = (text) => {
  const m = text.match(/^([A-Za-z0-9_\u4e00-\u9fa5]+):/);
  return m ? m[1] : "";
};

let prevBg = null;
for (const b of beats) {
  const out = [];
  out.push(`[${b.scene}]`);
  // 背景没变 → 承接上一拍的舞台（引擎 stage=keep：不整屏淡入、背景/立绘不重建）
  if (prevBg !== null && b.bg === prevBg) out.push("stage = keep");
  prevBg = b.bg;
  if (b.kind === "minigame") {
    out.push(`event = ${b.mg.id}`);
    out.push(`mg.mode = ${b.mg.mode}`);
    out.push(`mg.onWin = ${resolveTarget(b.mg.onWin)}`);
    out.push(`mg.onLose = ${resolveTarget(b.mg.onLose)}`);
    if (b.mg.loop) out.push(`mg.loop = ${resolveTarget(b.mg.loop)}`);
    if (b.mg.with) out.push(`mg.with = ${b.mg.with}`);
  }
  out.push("");
  // 进入即执行的槽（SE / BGM / 逻辑运算）—— 任何拍点都可能有
  for (const op of b.ops) out.push(`slot = 场景进入 | ${op}`);
  emitStage(b, out);

  if (b.kind === "dialog") {
    if (b.speaker && b.speaker !== "narr") {
      out.push("{", "type = name", "id = 名牌", "x = " + NAME.x, "y = " + NAME.y,
        "width = " + NAME.w, "height = " + NAME.h,
        "text = " + (DISPLAY_NAME[b.speaker] || b.speaker), "fontSize = 22", "}");
    }
    out.push("{", "type = dialog", "id = 对话框", "x = " + DIALOG_BOX.x, "y = " + DIALOG_BOX.y,
      "width = " + DIALOG_BOX.w, "height = " + DIALOG_BOX.h);
    out.push("text = <<<");
    // 每行台词 = 一个独立段落（引擎按独立一行 --- 分段，点击逐段推进）；
    // 若整段堆在一起，引擎会一次性渲染全部行 → 必然溢出对话框。
    // 说话人由上面的名牌节点承担，所以这里去掉「角色: 」前缀。
    b.dialog.forEach((t, i) => {
      if (i > 0) out.push("---");
      out.push(stripSpeaker(t));
    });
    out.push("<<<");
    if (b.target) out.push(`target = ${b.target}`);
    out.push("}");
  } else if (b.kind === "banner") {
    const bf = bannerFile(b.banner, b.chapter);
    const dim = bf ? pngSize(path.join(BANNER_DIR, `${bf}.png`)) : null;
    if (bf && dim) {
      // 美术侧交付的系统横幅（14 条，高 100）：按原宽居中，超过画布才等比缩小
      const w = Math.min(dim.w, 1160);
      const h = Math.max(1, Math.round((dim.h * w) / dim.w));
      out.push("{", "type = char", "id = 横幅", `x = ${Math.round((1280 - w) / 2)}`, "y = 250",
        `width = ${w}`, `height = ${h}`,
        `path = assets/sprites/ui/banners/${bf}.png`, "}");
      // 图片横幅本身已含文案 → 用整屏透明按钮承载「点一下继续」，
      // 不再在底部重复一遍同样的文字（文字回退时才用对话框）
      out.push("{", "type = button", "id = 继续", "x = 0", "y = 0",
        "width = 1280", "height = 720", "text = ", "action = target",
        `target = ${b.target || ""}`,
        "style = -fx-background-color: transparent; -fx-border-color: transparent;", "}");
    } else {
      // 未映射到图片的文案：回退文字横幅 + 对话框（保证演出与可读性都不缺）
      out.push("{", "type = text", "id = 横幅", "x = " + BANNER.x, "y = " + BANNER.y,
        "width = " + BANNER.w, "height = " + BANNER.h, `text = ${b.banner}`,
        "fontSize = 40", "align = center",
        "style = -fx-text-fill: #ffd76a; -fx-background-color: rgba(10,12,26,0.86); -fx-background-radius: 18; -fx-border-color: #ffd76a; -fx-border-radius: 18;", "}");
      out.push("{", "type = dialog", "id = 对话框", "x = " + DIALOG_BOX.x, "y = " + DIALOG_BOX.y,
        "width = " + DIALOG_BOX.w, "height = " + DIALOG_BOX.h);
      out.push("text = <<<");
      out.push(b.banner);
      out.push("<<<");
      if (b.target) out.push(`target = ${b.target}`);
      out.push("}");
    }
  } else if (b.kind === "choice") {
    const n = b.buttons.length;
    const y0 = 430 - Math.floor((n - 1) * 34);
    b.buttons.forEach((opt, i) => {
      const y = y0 + i * 68;
      out.push("{", "type = button", `id = 选项${i + 1}`, `x = 360`, `y = ${y}`,
        "width = 560", "height = 56", `text = ${opt.text}`, "action = target",
        `target = ${b.optionScenes[i]}`, "}");
    });
  } else if (b.kind === "logic" || b.kind === "optlogic") {
    if (b.goto) out.push(`slot = 场景进入 | goto | | ${b.goto}`);   // 运算槽已在上面输出
  } else if (b.kind === "tail") {
    out.push("{", "type = text", "x = 240", "y = 280", "width = 800", "height = 120",
      "text = 本章待实现 —— 后续章节接入中", "fontSize = 34", "align = center", "}");
    out.push("{", "type = dialog", "x = " + DIALOG_BOX.x, "y = " + DIALOG_BOX.y,
      "width = " + DIALOG_BOX.w, "height = " + DIALOG_BOX.h,
      "text = <<<", "narr: （本章待实现 —— 敬请期待）", "<<<", "}");
  }
  out.push("");
  L.push(out.join("\n"));
}

// 尾部占位场景（若 goto 到未实现章节）
L.push(`[${TAIL}]`);
L.push("");
L.push("{");
L.push("type = text");
L.push("x = 240");
L.push("y = 280");
L.push("width = 800");
L.push("height = 120");
L.push("text = 本章待实现 —— 后续章节接入中");
L.push("fontSize = 34");
L.push("align = center");
L.push("}");
L.push("");

const text = L.join("\n") + "\n";

// =====================================================================
// 5) 输出 / 校验
// =====================================================================
const summary = `场景 ${beats.length + 1} 个 | flag ${flags.size} 个 | 立绘引用 ${beats.reduce((a, b) => a + b.chars.length, 0)} 处`;

if (CHECK) {
  const old = exists(OUT_FILE) ? fs.readFileSync(OUT_FILE, "utf8") : "";
  if (old === text) { console.log("CHECK OK（重编译零差异） | " + summary); process.exit(0); }
  console.log("CHECK DIFF（产物与现有文件不一致） | " + summary);
  process.exit(3);
}

fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
fs.writeFileSync(OUT_FILE, text, "utf8");
console.log(`已生成 ${path.relative(ROOT, OUT_FILE)} | ${summary}`);
console.log(`  label ${labelScene.size} 个 | 输出 ${text.split("\n").length} 行`);
