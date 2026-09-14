package com.studio.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标题菜单状态机回归（{@link MenuModel}，纯逻辑，不依赖 JavaFX）。
 *
 * <p>覆盖：无存档时「继续」不可点、有存档时取最新（按 mtime）、覆盖确认的触发条件、
 * 清档、以及菜单信息的文案。</p>
 */
class MenuModelTest {

    private static File save(File mapDir, String name, long mtime) throws IOException {
        File dir = new File(mapDir, "saves");
        assertTrue(dir.mkdirs() || dir.isDirectory(), "创建 saves 目录");
        File f = new File(dir, name);
        Files.write(f.toPath(), ("scene = ch0_start\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(f.setLastModified(mtime), "设置 mtime");
        return f;
    }

    @Test
    void emptyWhenNoSaveDirectory(@TempDir File mapDir) {
        MenuModel m = MenuModel.scan(mapDir);
        assertFalse(m.hasSave(), "没有 saves 目录 → 无存档");
        assertFalse(m.canContinue(), "无存档时「继续」不可点");
        assertFalse(m.needsOverwriteConfirm(), "无存档时「开始」不需要覆盖确认");
        assertEquals("（暂无存档）", m.saveInfoText());
    }

    @Test
    void scanPicksNewestByModifiedTime(@TempDir File mapDir) throws IOException {
        long base = 1_700_000_000_000L;
        save(mapDir, "slot1.txt", base);
        save(mapDir, "slot2.txt", base + 60_000);   // 更新
        MenuModel m = MenuModel.scan(mapDir);
        assertTrue(m.hasSave());
        assertTrue(m.canContinue());
        assertNotNull(m.latestSave());
        assertEquals("slot2", m.latestSave().slot(), "应取 mtime 最新的存档");
    }

    @Test
    void listSavesStripsExtensionAndSortsByName(@TempDir File mapDir) throws IOException {
        save(mapDir, "slot2.txt", 1L);
        save(mapDir, "slot1.txt", 2L);
        List<MenuModel.SaveInfo> all = MenuModel.listSaves(mapDir);
        assertEquals(2, all.size());
        assertEquals("slot1", all.get(0).slot(), "按文件名排序且去掉 .txt");
        assertEquals("slot2", all.get(1).slot());
    }

    @Test
    void overwriteConfirmOnlyWhenSaveExists(@TempDir File mapDir) throws IOException {
        assertFalse(MenuModel.scan(mapDir).needsOverwriteConfirm());
        save(mapDir, "slot1.txt", System.currentTimeMillis());
        assertTrue(MenuModel.scan(mapDir).needsOverwriteConfirm(), "有存档时开始新游戏要先确认");
    }

    @Test
    void resolveBlocksContinueWithoutSave(@TempDir File mapDir) {
        MenuModel m = MenuModel.scan(mapDir);
        assertEquals(MenuModel.Action.NONE, m.resolve(MenuModel.Action.CONTINUE),
                "无存档时点「继续」应为空操作");
        assertEquals(MenuModel.Action.NEW_GAME, m.resolve(MenuModel.Action.NEW_GAME));
        assertEquals(MenuModel.Action.GALLERY, m.resolve(MenuModel.Action.GALLERY));
        assertEquals(MenuModel.Action.SETTINGS, m.resolve(MenuModel.Action.SETTINGS));
        assertEquals(MenuModel.Action.QUIT, m.resolve(MenuModel.Action.QUIT));
    }

    @Test
    void resolveAllowsContinueWithSave(@TempDir File mapDir) throws IOException {
        save(mapDir, "slot1.txt", System.currentTimeMillis());
        MenuModel m = MenuModel.scan(mapDir);
        assertEquals(MenuModel.Action.CONTINUE, m.resolve(MenuModel.Action.CONTINUE));
    }

    @Test
    void clearSavesRemovesEverything(@TempDir File mapDir) throws IOException {
        save(mapDir, "slot1.txt", 1L);
        save(mapDir, "slot2.txt", 2L);
        save(mapDir, "slot_ch0_depart.txt", 3L);
        int n = MenuModel.clearSaves(mapDir);
        assertEquals(3, n, "应删除 3 份存档");
        assertFalse(MenuModel.scan(mapDir).hasSave(), "清档后不应再有存档");
    }

    @Test
    void saveInfoTextShowsSlotAndTime(@TempDir File mapDir) throws IOException {
        save(mapDir, "slot1.txt", 1_700_000_000_000L);
        MenuModel m = MenuModel.scan(mapDir);
        String text = m.saveInfoText();
        assertTrue(text.startsWith("最新存档：slot1 · "), "文案应含存档名，实际：" + text);
    }

    @Test
    void formatTimeMatchesPattern() {
        String t = MenuModel.formatTime(1_700_000_000_000L);
        assertTrue(t.matches("\\d{2}-\\d{2} \\d{2}:\\d{2}"), "时间格式应为 MM-dd HH:mm，实际：" + t);
    }

    @Test
    void confirmTextsAreExplicit() {
        assertTrue(MenuModel.confirmMessage().contains("清空"), "确认文案要明确会清档");
        assertEquals("取消", MenuModel.confirmCancelText(), "取消排在前面（默认焦点）");
        assertTrue(MenuModel.confirmOkText().contains("清空"));
    }
}
