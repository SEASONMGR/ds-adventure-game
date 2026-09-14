// verify_handover.mjs —— 交接文档校验：附录完整性 + 源文件未被改动
// 用法: node verify_handover.mjs
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const DOC = 'D:/developing/javaFX/ds-adventure/docs/ds-adventrue';
const OUT = path.join(DOC, '剧情-游戏逻辑交接文档.md');
const doc = fs.readFileSync(OUT, 'utf8');

const ORDER = [
  '序章_404之夜.txt',
  '第1章_思维链大暴走.txt', '第2章_幻想乡弹幕异变.txt', '第3章_合鳞礼.txt',
  '第4章_防火墙拆迁办.txt', '第5章_上下文溢出.txt', '第6章_歌姬的曲库灾难.txt',
  '第7章_流言沼公关战.txt', '第8章_无主之物仓.txt', '第9章_决战万秤楼.txt',
  '终章_深度求索.txt',
];
const TARGETS = [
  { label: 'A1.1', file: path.join(DOC, '剧情大纲.md') },
  { label: 'A2.1', file: path.join(DOC, '人物设定集.md') },
  { label: 'A3.1', file: path.join(DOC, '资产清单.md') },
  ...ORDER.map((f, i) => ({ label: `A4.${i + 1}`, file: path.join(DOC, '剧本', f) })),
  ...ORDER.map((f, i) => ({ label: `A5.${i + 1}`, file: path.join(DOC, '剧本-原声', f) })),
];

const trim = s => s.replace(/\s+$/, '');
let ok = 0, fail = 0;
const rows = [];
for (const t of TARGETS) {
  const src = trim(fs.readFileSync(t.file, 'utf8'));
  const name = path.basename(t.file);
  // 定位：标题行 → 紧随的 4 反引号围栏
  const idx = doc.indexOf(`### ${t.label} `);
  if (idx < 0) { fail++; rows.push([t.label, name, '标题缺失', '—']); continue; }
  const fenceStart = doc.indexOf('````\n', idx);
  const fenceEnd = doc.indexOf('\n````', fenceStart + 5);
  const block = doc.slice(fenceStart + 5, fenceEnd);
  const same = trim(block) === src;
  const srcLines = src.split('\n').length;
  const blkLines = block.split('\n').length;
  if (same) ok++; else fail++;
  rows.push([t.label, name, same ? 'OK' : '**DIFF**', `${blkLines}/${srcLines}`]);
}

// 源文件哈希（供回归比对）
const hashOf = p => crypto.createHash('sha1').update(fs.readFileSync(p)).digest('hex').slice(0, 12);
const srcHashes = TARGETS.map(t => `${t.label} ${path.basename(t.file)} ${hashOf(t.file)}`).join('\n');

console.log('| 附录 | 文件 | 一致性 | 行数(文档/源) |');
console.log('|---|---|---|---|');
for (const r of rows) console.log(`| ${r[0]} | ${r[1]} | ${r[2]} | ${r[3]} |`);
console.log(`\n结果: ${ok} 一致 / ${fail} 不一致（共 ${TARGETS.length} 个附录文件）`);
console.log(`文档: ${doc.split('\n').length} 行 / ${Math.round(fs.statSync(OUT).size / 1024)} KB`);
fs.writeFileSync(path.join(DOC, '_snapshot_srchash.txt'), srcHashes + '\n', 'utf8');
console.log('源文件哈希快照: docs/ds-adventrue/_snapshot_srchash.txt');
