// place_cg_se.mjs —— 把 @cg / @se 触发指令钉进剧本（幂等，可重复执行）
// 用法: node place_cg_se.mjs [--dry]
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:/developing/javaFX/ds-adventure/docs/ds-adventrue/剧本';
const DRY = process.argv.includes('--dry');

// mode: 'before' | 'after'   anchor: 目标行的唯一子串
const rules = [
  // ============ 序章 ============
  { f: '序章_404之夜.txt', ins: '@se se_abacus', mode: 'before', anchor: '柜台后传来算珠轻响' },
  { f: '序章_404之夜.txt', ins: '@se se_shutdown', mode: 'before', anchor: '灯，齐灭' },
  { f: '序章_404之夜.txt', ins: '@cg cg_ch00_scale', mode: 'after', anchor: '灯，齐灭' },
  { f: '序章_404之夜.txt', ins: '@cg cg_ch00_rescue', mode: 'before', anchor: '她转向屏幕' },

  // ============ 第 1 章 ============
  { f: '第1章_思维链大暴走.txt', ins: '@se se_error', mode: 'before', anchor: '它头一回见到不上秤的东西' },
  { f: '第1章_思维链大暴走.txt', ins: '@cg cg_ch01_snake_ride', mode: 'before', anchor: '@minigame snake' },
  { f: '第1章_思维链大暴走.txt', ins: '@se se_tear', mode: 'before', anchor: '撕成两半' },

  // ============ 第 2 章 ============
  { f: '第2章_幻想乡弹幕异变.txt', ins: '@se se_banner', mode: 'before', anchor: '万秤楼冠名赞助' },
  { f: '第2章_幻想乡弹幕异变.txt', ins: '@cg cg_ch02_reimu_danmaku', mode: 'before', anchor: '@minigame plane' },

  // ============ 第 3 章 ============
  { f: '第3章_合鳞礼.txt', ins: '@se se_block_merge', mode: 'before', anchor: '@minigame 2048' },
  { f: '第3章_合鳞礼.txt', ins: '@se se_bell', mode: 'before', anchor: '铜铃' },
  { f: '第3章_合鳞礼.txt', ins: '@cg cg_ch03_merge', mode: 'before', anchor: '【合鳞礼成' },

  // ============ 第 4 章 ============
  { f: '第4章_防火墙拆迁办.txt', ins: '@se se_error', mode: 'before', anchor: '它头一回见到不上秤的东西' },
  { f: '第4章_防火墙拆迁办.txt', ins: '@se se_brick_break', mode: 'before', anchor: '@minigame brick' },
  { f: '第4章_防火墙拆迁办.txt', ins: '@cg cg_ch04_freefire', mode: 'before', anchor: '磷光落进苦力怕掌心' },

  // ============ 第 5 章 ============
  { f: '第5章_上下文溢出.txt', ins: '@se se_tear', mode: 'before', anchor: '她推来第一张牌' },
  { f: '第5章_上下文溢出.txt', ins: '@cg cg_mem_call', mode: 'before', anchor: '她推来第一张牌' },
  { f: '第5章_上下文溢出.txt', ins: '@cg cg_mem_snake', mode: 'before', anchor: '第二张牌翻开' },
  { f: '第5章_上下文溢出.txt', ins: '@cg cg_mem_shrine', mode: 'before', anchor: '第三张牌翻开' },
  { f: '第5章_上下文溢出.txt', ins: '@cg cg_mem_water', mode: 'before', anchor: '第四张牌翻开' },
  { f: '第5章_上下文溢出.txt', ins: '@se se_card_flip', mode: 'before', anchor: '@minigame memory' },
  { f: '第5章_上下文溢出.txt', ins: '@cg cg_ch05_lastcard', mode: 'before', anchor: '最后一张牌翻开' },

  // ============ 第 6 章 ============
  { f: '第6章_歌姬的曲库灾难.txt', ins: '@se se_link', mode: 'before', anchor: '@minigame link' },
  { f: '第6章_歌姬的曲库灾难.txt', ins: '@cg cg_ch06_miku_chorus', mode: 'before', anchor: '初音第一次唱出了副歌' },
  { f: '第6章_歌姬的曲库灾难.txt', ins: '@se se_star', mode: 'before', anchor: '答谢' },

  // ============ 第 7 章 ============
  { f: '第7章_流言沼公关战.txt', ins: '@se se_banner', mode: 'before', anchor: '茶馆街贴着流言沼的边' },
  { f: '第7章_流言沼公关战.txt', ins: '@cg cg_ch07_swamp', mode: 'before', anchor: '@minigame minesweep' },
  { f: '第7章_流言沼公关战.txt', ins: '@se se_mine_boom', mode: 'before', anchor: '@minigame minesweep' },
  { f: '第7章_流言沼公关战.txt', ins: '@se se_shutdown', mode: 'after', anchor: '@label bad_collapse' },
  { f: '第7章_流言沼公关战.txt', ins: '@cg cg_end_collapse', mode: 'after', anchor: '@label bad_collapse' },

  // ============ 第 8 章 ============
  { f: '第8章_无主之物仓.txt', ins: '@se se_box_push', mode: 'before', anchor: '@minigame sokoban' },
  { f: '第8章_无主之物仓.txt', ins: '@cg cg_ch08_warehouse', mode: 'before', anchor: '没写寄件人' },

  // ============ 第 9 章 ============
  { f: '第9章_决战万秤楼.txt', ins: '@se se_abacus', mode: 'before', anchor: '算珠哗啦啦' },
  { f: '第9章_决战万秤楼.txt', ins: '@se se_stone_place', mode: 'before', anchor: '@minigame gomoku' },
  { f: '第9章_决战万秤楼.txt', ins: '@cg cg_ch09_gomoku', mode: 'before', anchor: '@minigame gomoku' },
  { f: '第9章_决战万秤楼.txt', ins: '@cg cg_ch09_ds_only', mode: 'before', anchor: '一片不少' },
  { f: '第9章_决战万秤楼.txt', ins: '@se se_bell', mode: 'before', anchor: '【九鳞归位】' },

  // ============ 终章 ============
  { f: '终章_深度求索.txt', ins: '@se se_bell', mode: 'before', anchor: '九鳞归位' },
  { f: '终章_深度求索.txt', ins: '@cg cg_final_song', mode: 'before', anchor: '歌声落进秤盘' },
  { f: '终章_深度求索.txt', ins: '@cg cg_end_busy', mode: 'after', anchor: '@label bad_busy' },
  { f: '终章_深度求索.txt', ins: '@se se_shutdown', mode: 'after', anchor: '@label bad_busy' },
  { f: '终章_深度求索.txt', ins: '@cg cg_end_temp', mode: 'after', anchor: '@label end_temp' },
  { f: '终章_深度求索.txt', ins: '@cg cg_end_true', mode: 'after', anchor: '@label end_true' },
  { f: '终章_深度求索.txt', ins: '@se se_star', mode: 'after', anchor: '@label end_true' },
  { f: '终章_深度求索.txt', ins: '@cg cg_end_local', mode: 'after', anchor: '@label end_local' },

  // ============ 全篇通用（all: 命中全部）============
  { f: '*', ins: '@se se_busy', mode: 'before', anchor: '【服务器繁忙，请稍后再试】', all: true },
  { f: '*', ins: '@se se_busy', mode: 'before', anchor: '【（小声）请稍后再试', all: true },
  { f: '*', ins: '@se se_scale_ding', mode: 'before', anchor: '【合鳞礼成', all: true },
  { f: '*', ins: '@se se_scale_break', mode: 'before', anchor: '【叮——秤崩簧', all: true },
  { f: '*', ins: '@se se_scale_break', mode: 'before', anchor: '【崩簧——秤不肯认', all: true },
];

const allFiles = fs.readdirSync(DIR).filter(f => f.endsWith('.txt'));
const byFile = {};
for (const r of rules) {
  const targets = r.f === '*' ? allFiles : [r.f];
  for (const t of targets) (byFile[t] ||= []).push(r);
}

let hit = 0, miss = 0, dup = 0;
const problems = [];
for (const [fname, rs] of Object.entries(byFile)) {
  const p = path.join(DIR, fname);
  if (!fs.existsSync(p)) { problems.push(`✗ 文件不存在: ${fname}`); miss += rs.length; continue; }
  const lines = fs.readFileSync(p, 'utf8').split(/\r?\n/);
  for (const r of rs) {
    const idxs = [];
    lines.forEach((l, i) => { if (l.includes(r.anchor)) idxs.push(i); });
    if (!idxs.length) { problems.push(`✗ 未命中「${r.anchor}」→ ${r.ins}`); miss++; continue; }
    const targets = r.all ? [...idxs].sort((a, b) => b - a) : [idxs[0]];
    for (const at of targets) {
      const insIdx = r.mode === 'before' ? at : at + 1;
      if (lines[insIdx - 1] === r.ins || lines[insIdx] === r.ins) { dup++; continue; }
      lines.splice(insIdx, 0, r.ins);
      hit++;
    }
  }
  if (!DRY) fs.writeFileSync(p, lines.join('\n'), 'utf8');
}

console.log(problems.length ? problems.join('\n') : '（全部锚点命中）');
console.log(`\n插入 ${hit} 处 / 已存在跳过 ${dup} 处 / 未命中 ${miss} 处${DRY ? '（dry-run）' : ''}`);
