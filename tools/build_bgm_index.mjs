/**
 * build_bgm_index.mjs —— 把美术侧的 bgm_map.json 压成 Java 好读的扁平索引。
 *
 * 输入：src/main/resources/assets/sounds/bgm/bgm_map.json
 * 输出：src/main/resources/assets/sounds/bgm/bgm_index.txt
 *   每行：类别|文件名|role|loop(1/0)|来源
 *
 * 为什么要有这一步：项目没有 JSON 依赖（不引 Jackson 等），而 bgm_map.json 是美术侧
 * 手写维护的交付物。生成一张扁平表给 Java 读，既避免手写 JSON 解析器，又保留
 * "改曲只改素材、不改代码"的性质（改完 bgm_map.json 重跑本脚本即可）。
 *
 * 用法：node tools/build_bgm_index.mjs [--check]
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1")), "..");
const BGM_DIR = path.join(ROOT, "src", "main", "resources", "assets", "sounds", "bgm");
const MAP_FILE = path.join(BGM_DIR, "bgm_map.json");
const OUT_FILE = path.join(BGM_DIR, "bgm_index.txt");
const CHECK = process.argv.includes("--check");

if (!fs.existsSync(MAP_FILE)) {
  console.error("✗ 找不到 " + MAP_FILE);
  process.exit(1);
}
const map = JSON.parse(fs.readFileSync(MAP_FILE, "utf8"));

const lines = [];
lines.push("# BGM 索引（由 tools/build_bgm_index.mjs 从 bgm_map.json 生成，请勿手改）");
lines.push("# 格式：类别|文件名|role|loop(1/0)|来源");
const cats = Object.keys(map);
let missing = 0;
for (const cat of cats) {
  const entry = map[cat] || {};
  const files = Array.isArray(entry.files) ? entry.files : [];
  for (const f of files) {
    const file = String(f.file || "").trim();
    if (!file) continue;
    const role = String(f.role || "选").trim();
    const loop = f.loop ? "1" : "0";
    const source = String(f.source || "").trim();
    lines.push(`${cat}|${file}|${role}|${loop}|${source}`);
    if (!fs.existsSync(path.join(BGM_DIR, file))) {
      missing++;
      console.warn("⚠ 索引里有但磁盘缺失：" + file);
    }
  }
}
const text = lines.join("\n") + "\n";

if (CHECK) {
  const old = fs.existsSync(OUT_FILE) ? fs.readFileSync(OUT_FILE, "utf8") : "";
  if (old === text) {
    console.log(`CHECK OK（索引与 bgm_map.json 一致）| ${cats.length} 类 / ${lines.length - 2} 首`);
    process.exit(0);
  }
  console.error("CHECK DIFF（索引已过期，请重跑本脚本）");
  process.exit(3);
}

fs.writeFileSync(OUT_FILE, text, "utf8");
console.log(`已生成 ${path.relative(ROOT, OUT_FILE)} | ${cats.length} 类 / ${lines.length - 2} 首`
  + (missing ? ` | ⚠ 磁盘缺失 ${missing} 首` : ""));
for (const cat of cats) {
  const files = (map[cat].files || []);
  const picked = files.filter((f) => f.role === "选").length;
  console.log(`  ${cat.padEnd(12)} 共 ${String(files.length).padStart(2)} 首（首选 ${picked}）  ${map[cat].cn || ""}`);
}
