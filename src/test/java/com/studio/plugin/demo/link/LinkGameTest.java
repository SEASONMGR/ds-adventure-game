package com.studio.plugin.demo.link;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连连看规则引擎回归（{@link LinkGame}，纯逻辑，不依赖 JavaFX）。
 *
 * <p>覆盖：0/1/2 次拐弯可连、被完全围住时不可连、<b>允许绕棋盘外一圈</b>、
 * 图案不同 / 连不通的交互结果、消除计数、通关、死局自动重排、重排用尽判负、限时判负。</p>
 */
class LinkGameTest {

    private static LinkGame board(int[][] grid) {
        return board(grid, 0, 5);
    }

    private static LinkGame board(int[][] grid, int timeLimitSec, int maxShuffles) {
        LinkGame g = new LinkGame();
        g.load(grid, timeLimitSec, maxShuffles);
        return g;
    }

    // ---------------------------------------------------------------- 路径判定

    @Test
    void adjacentSameRowConnects() {
        LinkGame g = board(new int[][]{{1, 1}, {0, 0}});
        assertTrue(g.canConnect(0, 0, 0, 1), "相邻同排应可连（0 次拐弯）");
    }

    @Test
    void oneTurnConnects() {
        LinkGame g = board(new int[][]{{1, 0}, {0, 1}});
        assertTrue(g.canConnect(0, 0, 1, 1), "拐角为空时应可连（1 次拐弯）");
    }

    @Test
    void oneTurnBlockedFails() {
        LinkGame g = board(new int[][]{{1, 2}, {2, 1}});
        assertFalse(g.canConnect(0, 0, 1, 1), "两个拐角都被占，1 次拐弯不可连");
    }

    @Test
    void twoTurnsConnect() {
        LinkGame g = board(new int[][]{
                {1, 2, 0},
                {2, 2, 0},
                {0, 0, 1}});
        assertTrue(g.canConnect(0, 0, 2, 2), "应能拐两次连通");
    }

    @Test
    void outsideRoutingConnects() {
        // 同一行两张同图，中间被占、下方被堵 → 只能从棋盘上方（外圈）绕过去
        LinkGame g = board(new int[][]{
                {1, 2, 1},
                {2, 2, 2}});
        assertTrue(g.canConnect(0, 0, 0, 2), "应允许绕棋盘外一圈连通（标准连连看规则）");
    }

    @Test
    void fullyEnclosedPairCannotConnect() {
        // 两张同图被 2 完全围住：四个方向都出不去 → 连不通
        LinkGame g = board(new int[][]{
                {2, 2, 2, 2, 2},
                {2, 1, 2, 1, 2},
                {2, 2, 2, 2, 2},
                {2, 2, 2, 2, 2},
                {2, 2, 2, 2, 2}});
        assertFalse(g.canConnect(1, 1, 1, 3), "被完全围住的两张牌不可连");
    }

    @Test
    void differentIconsNeverConnect() {
        LinkGame g = board(new int[][]{{1, 2}, {0, 0}});
        assertFalse(g.canConnect(0, 0, 0, 1), "图案不同不可连");
    }

    // ---------------------------------------------------------------- 交互结果

    @Test
    void selectThenDeselect() {
        LinkGame g = board(new int[][]{{1, 1}, {0, 0}});
        assertEquals(LinkGame.Change.SELECTED, g.select(0, 0));
        assertEquals(LinkGame.Change.DESELECTED, g.select(0, 0));
        assertEquals(-1, g.selectedRow(), "取消后不应有选中");
    }

    @Test
    void mismatchMovesSelection() {
        LinkGame g = board(new int[][]{{1, 2}, {0, 0}});
        assertEquals(LinkGame.Change.SELECTED, g.select(0, 0));
        assertEquals(LinkGame.Change.MISMATCH, g.select(0, 1));
        assertEquals(0, g.selectedRow());
        assertEquals(1, g.selectedCol(), "改选后选中应移到新点的那张");
    }

    @Test
    void sameIconButEnclosedReportsNoPath() {
        LinkGame g = board(new int[][]{
                {2, 2, 2, 2, 2},
                {2, 1, 2, 1, 2},
                {2, 2, 2, 2, 2},
                {2, 2, 2, 2, 2},
                {2, 2, 2, 2, 2}});
        assertEquals(LinkGame.Change.SELECTED, g.select(1, 1));
        assertEquals(LinkGame.Change.NO_PATH, g.select(1, 3), "同图但被围住应报 NO_PATH");
    }

    @Test
    void matchRemovesBothTiles() {
        LinkGame g = board(new int[][]{{1, 1}, {0, 0}});
        assertEquals(LinkGame.Change.SELECTED, g.select(0, 0));
        assertEquals(LinkGame.Change.MATCHED, g.select(0, 1));
        assertEquals(0, g.iconAt(0, 0));
        assertEquals(0, g.iconAt(0, 1));
        assertEquals(0, g.remainingTiles());
    }

    @Test
    void emptyCellIgnored() {
        LinkGame g = board(new int[][]{{1, 1}, {0, 0}});
        assertEquals(LinkGame.Change.NONE, g.select(1, 1), "点空位应无反应");
    }

    @Test
    void clearingBoardWins() {
        LinkGame g = board(new int[][]{{1, 1}, {2, 2}});
        g.select(0, 0);
        g.select(0, 1);
        assertFalse(g.isWin(), "还剩一对，未通关");
        g.select(1, 0);
        assertEquals(LinkGame.Change.MATCHED, g.select(1, 1));
        assertTrue(g.isWin(), "清空盘面应通关");
        assertTrue(g.isOver());
    }

    // ---------------------------------------------------------------- 发牌 / 死局 / 限时

    @Test
    void dealsSolvableBoard() {
        LinkGame g = new LinkGame();
        g.reset(LinkConfig.defaults());
        assertEquals(80, g.remainingTiles(), "默认 8×10 = 80 格");
        assertEquals(40, g.remainingPairs());
        assertTrue(g.hasMoves(), "发牌后必定存在可消对");
    }

    @Test
    void matchThatDeadlocksTriggersShuffle() {
        // 消掉 3-3 后只剩一张 1、一张 2 → 无解 → 自动重排
        LinkGame g = board(new int[][]{
                {1, 2},
                {3, 3}}, 0, 5);
        g.select(1, 0);
        assertEquals(LinkGame.Change.MATCHED, g.select(1, 1));
        assertEquals(1, g.shufflesUsed(), "消完发现死局应触发一次重排");
    }

    @Test
    void shuffleExhaustionLoses() {
        LinkGame g = board(new int[][]{{1, 2}, {2, 1}}, 0, 1);
        assertTrue(g.shuffleRemaining(), "第一次重排应被允许");
        assertEquals(1, g.shufflesUsed());
        assertFalse(g.shuffleRemaining(), "达到上限后应拒绝重排");
        assertTrue(g.isLose(), "重排次数用尽判负");
    }

    @Test
    void timeLimitLoses() {
        LinkGame g = board(new int[][]{{1, 1}, {2, 2}}, 2, 5);
        g.tick(1.0);
        assertFalse(g.isOver(), "还剩 1 秒");
        g.tick(1.5);
        assertTrue(g.isLose(), "超时判负");
        assertEquals(0.0, g.remainingSeconds(), 1e-9);
    }

    @Test
    void unlimitedTimeNeverLoses() {
        LinkGame g = board(new int[][]{{1, 1}, {2, 2}}, 0, 5);
        g.tick(9999);
        assertFalse(g.isOver(), "不限时不应因时间判负");
    }
}
