// check_expressions.mjs —— 表情合规校验器
// 校验：① 表情 id 是否属于该角色的既定菜单 ② 同角色是否连续同表情 ③ 每章密度（主角≥4、客串≥3）
// 用法: node check_expressions.mjs [剧本目录]
import fs from 'node:fs';
import path from 'node:path';

const DIR = process.argv[2] || 'D:/developing/javaFX/ds-adventure/docs/ds-adventrue/剧本';

// 《人物设定集》§10.2 表情菜单（改设定集时同步这里）
const MENU = {
  ds: ['whale_cute', 'normal', 'cute', 'cry', 'whale_cry', 'smug', 'panic', 'fierce', 'weary', 'smile', 'serious', 'surprised', 'sad'],
  snake_expert: ['smug', 'normal', 'proud', 'panic', 'cry'],
  '秤主': ['smug', 'polite', 'surprised', 'weary', 'defeated'],
  '灯官': ['normal', 'delighted', 'panic'],
  '契官': ['normal', 'apologetic', 'panic'],
  '戏官': ['normal', 'delighted', 'panic'],
  sclerk: ['silhouette', 'panic'],
  glm: ['normal', 'smug', 'angry', 'worried'],
  qianwen: ['normal', 'worried', 'happy', 'panic'],
  kimi: ['gentle', 'sad', 'smile', 'serious'],
  reimu: ['normal', 'angry', 'smug', 'surprised', 'serious'],
  paimon: ['cute', 'panic', 'proud', 'cry'],
  creeper: ['ash', 'normal', 'fierce', 'happy'],
  miku: ['cry', 'normal', 'smile', 'joy'],
  amiya: ['normal', 'serious', 'worried', 'firm'],
  '怪力': ['normal', 'proud', 'moved', 'determined'],
  '皮卡丘': ['cute', 'excited', 'proud'],
  sai: ['normal', 'gentle', 'serious', 'moved'],
  bugs: ['normal', 'smug', 'panic'],
};

const files = fs.readdirSync(DIR).filter(f => f.endsWith('.txt')).sort();
let issues = 0;
const rows = [];

for (const f of files) {
  const lines = fs.readFileSync(path.join(DIR, f), 'utf8').split(/\r?\n/);
  const perChar = {};
  const seq = {};
  const bad = [];
  lines.forEach((l, i) => {
    const m = l.match(/^@enter\s+(\S+)\s+(\S+)/);
    if (!m) return;
    const [, ch, expr] = m;
    perChar[ch] = (perChar[ch] || 0) + 1;
    const menu = MENU[ch];
    if (!menu) { bad.push(`L${i + 1} 未知角色「${ch}」`); return; }
    if (!menu.includes(expr)) bad.push(`L${i + 1} ${ch} 用了菜单外表情「${expr}」（允许：${menu.join('/')}）`);
    if (seq[ch] === expr) bad.push(`L${i + 1} ${ch} 连续同表情「${expr}」`);
    seq[ch] = expr;
  });
  // 密度：ds ≥4；台词数 ≥6 的客串 ≥3
  const speech = {};
  for (const l of lines) {
    const m = l.match(/^(\S+?):\s/);
    if (m && m[1] !== 'narr' && m[1] !== 'st') speech[m[1]] = (speech[m[1]] || 0) + 1;
  }
  for (const [ch, n] of Object.entries(speech)) {
    if (n < 6) continue;
    if (ch === 'sclerk') continue; // 司秤吏是柜台后的纯声优角色，剪影可见即可，不强制表情切换
    const got = perChar[ch] || 0;
    const need = ch === 'ds' ? 4 : 3;
    if (got < need) bad.push(`${ch} 台词 ${n} 句但只有 ${got} 次表情切换（要求 ≥${need}）`);
  }
  if (bad.length) issues += bad.length;
  rows.push({ file: f, switches: Object.values(perChar).reduce((a, b) => a + b, 0), chars: Object.keys(perChar).length, bad });
}

for (const r of rows) {
  console.log(`${r.file}  切换 ${String(r.switches).padStart(3)} 次 / ${r.chars} 角色`);
  for (const b of r.bad) console.log(`    ✗ ${b}`);
}
console.log(`\n合计问题: ${issues}`);
