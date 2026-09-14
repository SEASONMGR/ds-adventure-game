package com.studio.plugin.demo.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 连连看规则引擎（纯逻辑，<b>不依赖 JavaFX</b>，可脱界面单测）。
 *
 * <p><b>玩法</b>：棋盘上全是<b>明牌</b>（与"记忆翻牌"不同，牌面始终可见）。
 * 选中两张<b>图案相同</b>且能用一条<b>拐弯不超过 2 次</b>、且路径上无其它牌的折线连起来的牌，
 * 即可消除；清空棋盘即通关。无解时自动重排剩余牌（可设次数上限）。</p>
 *
 * <p><b>路径判定</b>：棋盘外一圈视为可通行（标准连连看允许绕边），因此
 * 越界坐标在 {@link #empty(int, int)} 中返回 true。</p>
 */
public final class LinkGame {

    /** 一次点击产生的结果（界面据此给反馈） */
    public enum Change {
        /** 空位 / 越界 / 已消除：忽略 */
        NONE,
        /** 选中了第一张 */
        SELECTED,
        /** 点同一张：取消选中 */
        DESELECTED,
        /** 配对成功并消除 */
        MATCHED,
        /** 图案不同：改为选中新点的那张 */
        MISMATCH,
        /** 图案相同但连不通（路径被挡或拐弯超过 2 次） */
        NO_PATH
    }

    private LinkConfig config = LinkConfig.defaults();
    private int[][] grid;                 // 0 = 空（已消除）；>0 = 图标 id
    private int selR = -1;
    private int selC = -1;
    private double remainSec;
    private boolean win;
    private boolean over;
    private int shufflesUsed;
    private final Random random = new Random();

    // =====================================================================
    // 生命周期
    // =====================================================================

    public void reset(LinkConfig cfg) {
        this.config = cfg == null ? LinkConfig.defaults() : cfg;
        this.remainSec = config.timeLimitSec;
        this.win = false;
        this.over = false;
        this.shufflesUsed = 0;
        this.selR = -1;
        this.selC = -1;
        dealBoard();
    }

    /**
     * 用<b>给定盘面</b>开局（图标 0 = 空位）。供关卡设计与单测摆放固定局面使用，
     * 不做"成对铺满"的发牌，也不检查是否有解。
     */
    public void load(int[][] initialGrid, int timeLimitSec, int maxShuffles) {
        int rows = initialGrid.length;
        int cols = initialGrid[0].length;
        int maxIcon = 0;
        for (int[] row : initialGrid) {
            for (int v : row) {
                maxIcon = Math.max(maxIcon, v);
            }
        }
        this.config = LinkConfig.exact(rows, cols, Math.max(1, maxIcon), timeLimitSec, maxShuffles);
        this.grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(initialGrid[r], 0, grid[r], 0, cols);
        }
        this.remainSec = config.timeLimitSec;
        this.win = false;
        this.over = false;
        this.shufflesUsed = 0;
        clearSelection();
    }

    /** 发牌：每种图标成对铺满，洗牌后若一开始就无解则重洗 */
    private void dealBoard() {
        int rows = config.rows, cols = config.cols;
        int total = rows * cols;
        List<Integer> icons = new ArrayList<>(total);
        for (int i = 0; i < total / 2; i++) {
            int type = (i % config.iconTypes) + 1;   // 图标从 1 开始（0 保留给空位）
            icons.add(type);
            icons.add(type);
        }
        for (int guard = 0; guard < 64; guard++) {
            Collections.shuffle(icons, random);
            grid = new int[rows][cols];
            int k = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    grid[r][c] = icons.get(k++);
                }
            }
            if (hasMoves()) return;
        }
    }

    public void tick(double dt) {
        if (over || dt <= 0) return;
        if (config.timeLimitSec > 0) {
            remainSec -= dt;
            if (remainSec <= 0) {
                remainSec = 0;
                over = true;
                win = false;
            }
        }
    }

    // =====================================================================
    // 交互
    // =====================================================================

    /** 点击一格：返回本次点击的结果 */
    public Change select(int r, int c) {
        if (over) return Change.NONE;
        if (r < 0 || r >= config.rows || c < 0 || c >= config.cols) return Change.NONE;
        if (grid[r][c] == 0) return Change.NONE;

        if (selR == r && selC == c) {           // 再点一次 = 取消
            clearSelection();
            return Change.DESELECTED;
        }
        if (selR < 0) {                          // 第一张
            selR = r;
            selC = c;
            return Change.SELECTED;
        }
        if (grid[selR][selC] != grid[r][c]) {    // 图案不同 → 改选新点的那张
            selR = r;
            selC = c;
            return Change.MISMATCH;
        }
        if (!canConnect(selR, selC, r, c)) {     // 图案相同但连不通
            return Change.NO_PATH;
        }
        grid[selR][selC] = 0;                     // 消除
        grid[r][c] = 0;
        clearSelection();
        if (remainingPairs() == 0) {
            win = true;
            over = true;
        } else if (!hasMoves()) {
            shuffleRemaining();                   // 死局自动重排
        }
        return Change.MATCHED;
    }

    private void clearSelection() {
        selR = -1;
        selC = -1;
    }

    // =====================================================================
    // 路径判定（拐弯 ≤ 2 次）
    // =====================================================================

    /** 棋盘外一圈可通行；空位可通行 */
    private boolean empty(int r, int c) {
        if (r < 0 || r >= config.rows || c < 0 || c >= config.cols) return true;
        return grid[r][c] == 0;
    }

    /** 两点同在一条直线且中间全空（0 次拐弯） */
    private boolean linkStraight(int r1, int c1, int r2, int c2) {
        if (r1 == r2) {
            int a = Math.min(c1, c2), b = Math.max(c1, c2);
            for (int c = a + 1; c < b; c++) {
                if (!empty(r1, c)) return false;
            }
            return true;
        }
        if (c1 == c2) {
            int a = Math.min(r1, r2), b = Math.max(r1, r2);
            for (int r = a + 1; r < b; r++) {
                if (!empty(r, c1)) return false;
            }
            return true;
        }
        return false;
    }

    /** 一次拐弯：拐角必须为空，两段直线都通 */
    private boolean linkOneTurn(int r1, int c1, int r2, int c2) {
        if (empty(r1, c2) && linkStraight(r1, c1, r1, c2) && linkStraight(r1, c2, r2, c2)) {
            return true;
        }
        return empty(r2, c1) && linkStraight(r1, c1, r2, c1) && linkStraight(r2, c1, r2, c2);
    }

    private boolean linkStraightOrOneTurn(int r1, int c1, int r2, int c2) {
        return linkStraight(r1, c1, r2, c2) || linkOneTurn(r1, c1, r2, c2);
    }

    /** 两次拐弯：从起点沿四个方向走过空位，任一点能与终点"直线或一次拐弯"相连即可 */
    private boolean linkTwoTurn(int r1, int c1, int r2, int c2) {
        for (int c = c1 - 1; c >= -1; c--) {
            if (!empty(r1, c)) break;
            if (linkStraightOrOneTurn(r1, c, r2, c2)) return true;
        }
        for (int c = c1 + 1; c <= config.cols; c++) {
            if (!empty(r1, c)) break;
            if (linkStraightOrOneTurn(r1, c, r2, c2)) return true;
        }
        for (int r = r1 - 1; r >= -1; r--) {
            if (!empty(r, c1)) break;
            if (linkStraightOrOneTurn(r, c1, r2, c2)) return true;
        }
        for (int r = r1 + 1; r <= config.rows; r++) {
            if (!empty(r, c1)) break;
            if (linkStraightOrOneTurn(r, c1, r2, c2)) return true;
        }
        return false;
    }

    /** 两张同图牌能否连通（拐弯 ≤ 2 次、路径无阻挡、可绕棋盘外一圈） */
    public boolean canConnect(int r1, int c1, int r2, int c2) {
        if (r1 == r2 && c1 == c2) return false;
        if (r1 < 0 || r1 >= config.rows || c1 < 0 || c1 >= config.cols) return false;
        if (r2 < 0 || r2 >= config.rows || c2 < 0 || c2 >= config.cols) return false;
        if (grid[r1][c1] == 0 || grid[r2][c2] == 0) return false;
        if (grid[r1][c1] != grid[r2][c2]) return false;
        return linkStraight(r1, c1, r2, c2)
                || linkOneTurn(r1, c1, r2, c2)
                || linkTwoTurn(r1, c1, r2, c2);
    }

    /** 当前是否还存在可消除的一对 */
    public boolean hasMoves() {
        for (int r1 = 0; r1 < config.rows; r1++) {
            for (int c1 = 0; c1 < config.cols; c1++) {
                if (grid[r1][c1] == 0) continue;
                for (int r2 = r1; r2 < config.rows; r2++) {
                    int startC = (r2 == r1) ? c1 + 1 : 0;
                    for (int c2 = startC; c2 < config.cols; c2++) {
                        if (grid[r2][c2] == 0) continue;
                        if (grid[r1][c1] != grid[r2][c2]) continue;
                        if (canConnect(r1, c1, r2, c2)) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 把剩余牌的图标重新打乱铺回原位（保持图标多重集不变）。
     * 若次数已达上限则不再重排并判负（避免无限重排刷时间）。
     *
     * @return 是否真的重排了
     */
    public boolean shuffleRemaining() {
        if (config.maxShuffles > 0 && shufflesUsed >= config.maxShuffles) {
            over = true;
            win = false;
            return false;
        }
        List<Integer> left = new ArrayList<>();
        List<int[]> slots = new ArrayList<>();
        for (int r = 0; r < config.rows; r++) {
            for (int c = 0; c < config.cols; c++) {
                if (grid[r][c] != 0) {
                    left.add(grid[r][c]);
                    slots.add(new int[]{r, c});
                }
            }
        }
        if (left.size() < 2) return false;
        for (int guard = 0; guard < 64; guard++) {
            Collections.shuffle(left, random);
            for (int i = 0; i < slots.size(); i++) {
                grid[slots.get(i)[0]][slots.get(i)[1]] = left.get(i);
            }
            if (hasMoves()) break;
        }
        clearSelection();
        shufflesUsed++;
        return true;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    public LinkConfig config() { return config; }

    public int iconAt(int r, int c) {
        if (r < 0 || r >= config.rows || c < 0 || c >= config.cols) return 0;
        return grid[r][c];
    }

    public boolean isSelected(int r, int c) { return selR == r && selC == c; }

    public int selectedRow() { return selR; }

    public int selectedCol() { return selC; }

    public int remainingTiles() {
        int n = 0;
        for (int r = 0; r < config.rows; r++) {
            for (int c = 0; c < config.cols; c++) {
                if (grid[r][c] != 0) n++;
            }
        }
        return n;
    }

    public int remainingPairs() { return remainingTiles() / 2; }

    public double remainingSeconds() { return remainSec; }

    public int shufflesUsed() { return shufflesUsed; }

    public boolean isWin() { return win; }

    public boolean isLose() { return over && !win; }

    public boolean isOver() { return over; }
}
