// check_cgse.mjs —— CG / SE 触发覆盖校验
// ① 22 张 CG 是否都被剧本触发 ② 20 个 SE 是否都有着落（3 个 UI 音为全局，允许 0 处）
// ③ 同一文件内同一 CG 是否重复 ④ 引用的 id 是否都在交付清单内
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:/developing/javaFX/ds-adventure/docs/ds-adventrue/剧本';
const CG_DIR = 'D:/developing/javaFX/ds-adventure/src/main/resources/assets/cg';
const SE_DIR = 'D:/developing/javaFX/ds-adventure/src/main/resources/assets/sounds';

// 交付清单（素材交接文档 §五 / §七）
const CG_IDS = ['cg_ch00_rescue', 'cg_ch00_scale', 'cg_ch01_snake_ride', 'cg_ch02_reimu_danmaku', 'cg_ch03_merge', 'cg_ch04_freefire', 'cg_ch05_lastcard', 'cg_ch06_miku_chorus', 'cg_ch07_swamp', 'cg_ch08_warehouse', 'cg_ch09_ds_only', 'cg_ch09_gomoku', 'cg_end_busy', 'cg_end_collapse', 'cg_end_local', 'cg_end_temp', 'cg_end_true', 'cg_final_song', 'cg_mem_call', 'cg_mem_shrine', 'cg_mem_snake', 'cg_mem_water'];
const SE_IDS = ['se_abacus', 'se_banner', 'se_bell', 'se_block_merge', 'se_box_push', 'se_brick_break', 'se_busy', 'se_card_flip', 'se_click', 'se_error', 'se_hover', 'se_link', 'se_mine_boom', 'se_scale_break', 'se_scale_ding', 'se_select', 'se_shutdown', 'se_star', 'se_stone_place', 'se_tear'];
const UI_GLOBAL_SE = ['se_click', 'se_hover', 'se_select']; // 全局 UI 音，由播放器硬编码，不在剧本触发

const files = fs.readdirSync(DIR).filter(f => f.endsWith('.txt')).sort();
const cgUse = {}, seUse = {}, perFile = {}, unknown = [];
for (const f of files) {
  const lines = fs.readFileSync(path.join(DIR, f), 'utf8').split(/\r?\n/);
  const seenCg = {};
  let cgN = 0, seN = 0;
  for (const l of lines) {
    let m = l.match(/^@cg\s+(\S+)/);
    if (m) {
      const id = m[1].replace(/\|.*$/, '');
      cgN++; cgUse[id] = (cgUse[id] || 0) + 1; seenCg[id] = (seenCg[id] || 0) + 1;
      if (!CG_IDS.includes(id)) unknown.push(`${f}: 未知 CG「${id}」`);
    }
    m = l.match(/^@se\s+(\S+)/);
    if (m) {
      const id = m[1].replace(/\|.*$/, '');
      seN++; seUse[id] = (seUse[id] || 0) + 1;
      if (!SE_IDS.includes(id)) unknown.push(`${f}: 未知 SE「${id}」`);
    }
  }
  perFile[f] = { cgN, seN, dup: Object.entries(seenCg).filter(([, n]) => n > 1).map(([k, n]) => `${k}×${n}`) };
}

console.log('=== 每章触发数 ===');
for (const [f, v] of Object.entries(perFile)) {
  console.log(`${f.padEnd(30)} @cg ${String(v.cgN).padStart(2)}  @se ${String(v.seN).padStart(2)}${v.dup.length ? '   同章重复: ' + v.dup.join(', ') : ''}`);
}

const missCg = CG_IDS.filter(id => !cgUse[id]);
const missSe = SE_IDS.filter(id => !seUse[id] && !UI_GLOBAL_SE.includes(id));
console.log(`\n=== CG 覆盖 ===\n使用 ${Object.keys(cgUse).length}/${CG_IDS.length} 张${missCg.length ? '，未触发: ' + missCg.join(', ') : '（全覆盖）'}`);
console.log(`=== SE 覆盖 ===\n使用 ${Object.keys(seUse).length}/${SE_IDS.length} 个${missSe.length ? '，未触发: ' + missSe.join(', ') : '（除 UI 全局音外全覆盖）'}`);
console.log(`UI 全局音（播放器硬编码，允许 0 处）: ${UI_GLOBAL_SE.join(', ')} → 剧本内使用 ${UI_GLOBAL_SE.filter(x => seUse[x]).length} 处`);

// 磁盘核对
const cgFiles = fs.existsSync(CG_DIR) ? fs.readdirSync(CG_DIR).filter(x => x.endsWith('.png')).map(x => x.replace(/\.png$/, '')) : [];
const seFiles = fs.existsSync(SE_DIR) ? fs.readdirSync(SE_DIR).map(x => x.replace(/\.[a-z0-9]+$/i, '')) : [];
const notOnDisk = [...CG_IDS.filter(id => !cgFiles.includes(id)), ...SE_IDS.filter(id => !seFiles.includes(id))];
console.log(`\n=== 磁盘核对 ===\nCG 磁盘 ${cgFiles.length} / SE 磁盘 ${seFiles.length}${notOnDisk.length ? '，缺文件: ' + notOnDisk.join(', ') : '（清单内文件齐备）'}`);
if (unknown.length) console.log(`\n!!! 未知 id：\n${unknown.join('\n')}`);
