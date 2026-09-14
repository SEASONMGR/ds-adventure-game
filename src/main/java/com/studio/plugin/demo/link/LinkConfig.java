package com.studio.plugin.demo.link;

/** 连连看玩法参数（纯数据，无 JavaFX 依赖） */
public final class LinkConfig {

    /** 棋盘行数（默认 8） */
    public final int rows;
    /** 棋盘列数（默认 10；行列必须都是偶数，保证每种图标成对出现） */
    public final int cols;
    /** 图标种类数（默认 10；种类越多越难） */
    public final int iconTypes;
    /** 限时秒数（0 = 不限时） */
    public final int timeLimitSec;
    /** 死局自动重排的次数上限（0 = 不限制；>0 时用尽即判负） */
    public final int maxShuffles;

    public LinkConfig(int rows, int cols, int iconTypes, int timeLimitSec, int maxShuffles) {
        this.rows = Math.max(2, rows - rows % 2);
        this.cols = Math.max(2, cols - cols % 2);
        int pairs = (this.rows * this.cols) / 2;
        this.iconTypes = Math.max(1, Math.min(iconTypes, pairs));
        this.timeLimitSec = Math.max(0, timeLimitSec);
        this.maxShuffles = Math.max(0, maxShuffles);
    }

    /** 精确构造：<b>不做行列取偶</b>（供关卡设计与单测摆固定盘面用） */
    static LinkConfig exact(int rows, int cols, int iconTypes, int timeLimitSec, int maxShuffles) {
        return new LinkConfig(rows, cols, iconTypes, timeLimitSec, maxShuffles, true);
    }

    private LinkConfig(int rows, int cols, int iconTypes, int timeLimitSec, int maxShuffles, boolean exact) {
        this.rows = exact ? Math.max(2, rows) : Math.max(2, rows - rows % 2);
        this.cols = exact ? Math.max(2, cols) : Math.max(2, cols - cols % 2);
        int pairs = (this.rows * this.cols) / 2;
        this.iconTypes = Math.max(1, Math.min(iconTypes, Math.max(1, pairs)));
        this.timeLimitSec = Math.max(0, timeLimitSec);
        this.maxShuffles = Math.max(0, maxShuffles);
    }

    public static LinkConfig defaults() {
        return new LinkConfig(8, 10, 10, 0, 5);
    }

    /** 总格数 */
    public int totalTiles() {
        return rows * cols;
    }

    /** 总对数 */
    public int totalPairs() {
        return totalTiles() / 2;
    }
}
