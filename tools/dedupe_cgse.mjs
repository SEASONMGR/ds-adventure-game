// dedupe_cgse.mjs —— 清理重复的 @cg / @se 触发行（保留首次出现），避免重跑放置器造成重复
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:/developing/javaFX/ds-adventure/docs/ds-adventrue/剧本';
let total = 0;
for (const f of fs.readdirSync(DIR).filter(x => x.endsWith('.txt')).sort()) {
  const p = path.join(DIR, f);
  const lines = fs.readFileSync(p, 'utf8').split(/\r?\n/);
  const seenCg = new Set(), seenSe = new Set();
  const out = [];
  let removed = 0;
  for (const l of lines) {
    const m = l.match(/^@(cg|se)\s+(\S+)/);
    if (m) {
      const key = m[2];
      const set = m[1] === 'cg' ? seenCg : seenSe;
      if (set.has(key)) { removed++; continue; }   // 同文件内重复 → 丢弃
      set.add(key);
    }
    out.push(l);
  }
  if (removed) { fs.writeFileSync(p, out.join('\n'), 'utf8'); total += removed; console.log(`${f}: 去重 ${removed} 行`); }
}
console.log(`合计去重 ${total} 行`);
