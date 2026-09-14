package com.studio.launcher;

import com.studio.saves.GameSaveManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 标题菜单状态机（<b>纯逻辑，零 JavaFX</b>，可脱界面单测）。
 *
 * <p>负责：扫描存档目录、挑出「最新存档」、回答"继续能不能点""开始要不要覆盖确认"，
 * 以及覆盖确认的文案与清档动作。界面（{@code TitleView}）只负责画和转发。</p>
 */
public final class MenuModel {

    /** 菜单项对应的动作 */
    public enum Action {
        /** 开始新游戏（有存档时先覆盖确认） */
        NEW_GAME,
        /** 继续（载入最新存档） */
        CONTINUE,
        /** 回忆收藏馆（本轮为开发中面板） */
        GALLERY,
        /** 设置 */
        SETTINGS,
        /** 退出 */
        QUIT,
        /** 什么都不做（例如点了置灰的项） */
        NONE
    }

    /** 一条存档的摘要 */
    public static final class SaveInfo {
        private final String slot;
        private final long modifiedAt;

        public SaveInfo(String slot, long modifiedAt) {
            this.slot = slot == null ? "" : slot;
            this.modifiedAt = modifiedAt;
        }

        /** 存档名（不含扩展名，如 slot1 / slot_ch0_depart） */
        public String slot() { return slot; }

        /** 最后修改时间（毫秒） */
        public long modifiedAt() { return modifiedAt; }

        /** 界面用的一行文字：{@code slot1 · 09-14 22:10} */
        public String display() {
            return slot + " · " + formatTime(modifiedAt);
        }
    }

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final SaveInfo latest;

    public MenuModel(SaveInfo latest) {
        this.latest = latest;
    }

    /** 无存档的菜单模型 */
    public static MenuModel empty() {
        return new MenuModel(null);
    }

    /** 扫描地图的 saves 目录，取"最新"（按文件最后修改时间）；目录不存在/无档 → 无存档 */
    public static MenuModel scan(File mapDir) {
        List<SaveInfo> all = listSaves(mapDir);
        if (all.isEmpty()) return empty();
        all.sort(Comparator.comparingLong(SaveInfo::modifiedAt).reversed());
        return new MenuModel(all.get(0));
    }

    /** 列出全部存档（按名排序，便于测试断言） */
    public static List<SaveInfo> listSaves(File mapDir) {
        List<SaveInfo> out = new ArrayList<>();
        if (mapDir == null) return out;
        GameSaveManager mgr = new GameSaveManager(mapDir);
        for (String name : mgr.listSaveFiles()) {
            long ms = 0L;
            try {
                File f = mgr.pathOf(name).toFile();
                if (f.isFile()) {
                    FileTime t = Files.getLastModifiedTime(f.toPath());
                    ms = t.toMillis();
                }
            } catch (Exception ignored) {
                // 读不到时间就按 0 处理，不影响菜单可用性
            }
            out.add(new SaveInfo(stripExt(name), ms));
        }
        return out;
    }

    /**
     * 清空全部存档（「开始新游戏」确认后调用）。
     *
     * @return 实际删除的存档数
     */
    public static int clearSaves(File mapDir) {
        if (mapDir == null) return 0;
        GameSaveManager mgr = new GameSaveManager(mapDir);
        int n = 0;
        for (String name : mgr.listSaveFiles()) {
            try {
                if (mgr.delete(name)) n++;
            } catch (Exception ignored) {
                // 单个删除失败不影响其它
            }
        }
        return n;
    }

    private static String stripExt(String name) {
        if (name == null) return "";
        String n = name.trim();
        return n.toLowerCase().endsWith(".txt") ? n.substring(0, n.length() - 4) : n;
    }

    public static String formatTime(long ms) {
        return TIME_FMT.format(Instant.ofEpochMilli(ms));
    }

    // =====================================================================
    // 状态查询
    // =====================================================================

    /** 是否有存档 */
    public boolean hasSave() { return latest != null; }

    /** 最新存档（可能为 null） */
    public SaveInfo latestSave() { return latest; }

    /** 「继续」是否可点（无存档置灰） */
    public boolean canContinue() { return latest != null; }

    /** 「开始」是否需要覆盖确认（有存档才需要） */
    public boolean needsOverwriteConfirm() { return latest != null; }

    /** 菜单上那行存档信息 */
    public String saveInfoText() {
        return latest == null ? "（暂无存档）" : "最新存档：" + latest.display();
    }

    /** 覆盖确认的标题 */
    public static String confirmTitle() { return "开始新游戏"; }

    /** 覆盖确认的正文（文案明确"会覆盖"，避免误删） */
    public static String confirmMessage() {
        return "已有存档。开始新游戏会清空现有存档，此操作无法撤销。\n确定要开始新游戏吗？";
    }

    /** 覆盖确认的两个按钮（顺序：取消在前，默认焦点在取消上） */
    public static String confirmCancelText() { return "取消"; }

    public static String confirmOkText() { return "清空存档并开始"; }

    /**
     * 点击某个菜单项时该做什么。
     *
     * @param action 被点击的项
     * @return 实际要执行的动作（例如无存档时点「继续」→ {@link Action#NONE}）
     */
    public Action resolve(Action action) {
        if (action == Action.CONTINUE && !canContinue()) return Action.NONE;
        return action;
    }
}
