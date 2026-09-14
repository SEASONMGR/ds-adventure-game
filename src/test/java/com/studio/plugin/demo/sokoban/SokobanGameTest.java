package com.studio.plugin.demo.sokoban;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推箱子规则引擎单元测试。
 *
 * <p>纯规则、无 JavaFX 依赖，可在无图形环境下运行（对齐 SnakeGameTest 的范例）。</p>
 *
 * <p>最要紧的一条是 {@link #allLevelsAreSolvable()}：内置关卡由 BFS 穷举验证**必定有解**，
 * 改了关卡只要跑测试就知道有没有改坏，不用手工试。</p>
 */
class SokobanGameTest {

    /** 单箱子、一推即中的最小关。 */
    private static final String[] SIMPLE = {
        "#######",
        "#     #",
        "# @$. #",
        "#     #",
        "#######",
    };

    /** 推一格不会过关的关（目标点在更远处），用来测撤销与计数。 */
    private static final String[] PUSHABLE = {
        "########",
        "#      #",
        "# @$  .#",
        "#      #",
        "########",
    };

    /** 玩家贴着墙，用来测撞墙。 */
    private static final String[] CORNER = {
        "#####",
        "#@  #",
        "#####",
    };

    private static SokobanGame newGame(String[] level) {
        SokobanGame game = new SokobanGame();
        game.loadLevel(level);
        return game;
    }

    // =====================================================================
    // 解析与基本规则
    // =====================================================================

    @Test
    @DisplayName("解析：尺寸、玩家位置、箱子数与目标点数")
    void parseLevel() {
        SokobanGame game = newGame(SIMPLE);
        assertEquals(7, game.getWidth());
        assertEquals(5, game.getHeight());
        assertEquals(2, game.getPlayerX());
        assertEquals(2, game.getPlayerY());
        assertEquals(1, game.getBoxCount());
        assertTrue(game.isWall(0, 0), "角落应是墙");
        assertTrue(game.isTarget(4, 2), "目标点在 (4,2)");
        assertTrue(game.hasBox(3, 2), "箱子在 (3,2)");
        assertTrue(game.isPlayer(2, 2));
        assertFalse(game.isWin(), "开局还没过关");
    }

    @Test
    @DisplayName("移动：撞墙走不动，也不计入步数")
    void cannotWalkIntoWall() {
        SokobanGame game = newGame(CORNER);
        assertFalse(game.move(0, -1), "上方是墙");
        assertFalse(game.move(-1, 0), "左方是墙");
        assertEquals(0, game.getMoveCount());
        assertEquals(1, game.getPlayerX(), "位置不应变化");
        assertEquals(1, game.getPlayerY());
    }

    @Test
    @DisplayName("推箱：推得动就箱子和人一起走")
    void pushWorks() {
        SokobanGame game = newGame(SIMPLE);
        assertTrue(game.move(1, 0), "向右推箱");
        assertEquals(3, game.getPlayerX());
        assertEquals(2, game.getPlayerY());
        assertTrue(game.hasBox(4, 2), "箱子应被推到 (4,2)");
        assertFalse(game.hasBox(3, 2), "原位置应空出来");
        assertEquals(1, game.getMoveCount());
        assertEquals(1, game.getPushCount());
    }

    @Test
    @DisplayName("推箱：箱子抵墙就推不动")
    void cannotPushBoxIntoWall() {
        SokobanGame game = newGame(new String[]{
            "#####",
            "#@$##",
            "#####",
        });
        assertFalse(game.move(1, 0), "箱子后面是墙");
        assertEquals(1, game.getPlayerX(), "位置不应变化");
        assertEquals(0, game.getPushCount());
        assertEquals(0, game.getMoveCount());
    }

    @Test
    @DisplayName("推箱：箱子抵箱子也推不动")
    void cannotPushBoxIntoBox() {
        SokobanGame game = newGame(new String[]{
            "######",
            "#@$$ #",
            "######",
        });
        assertFalse(game.move(1, 0), "两个箱子顶在一起");
        assertEquals(0, game.getPushCount());
    }

    @Test
    @DisplayName("过关：把所有箱子推上目标点")
    void winWhenAllBoxesOnTargets() {
        SokobanGame game = newGame(SIMPLE);
        assertFalse(game.isWin());
        assertTrue(game.move(1, 0));
        assertTrue(game.isWin(), "箱子推到目标点上应过关");
        assertEquals(1, game.boxesOnTarget());
    }

    @Test
    @DisplayName("过关：只推好一个箱子不算过关")
    void partialBoxesAreNotWin() {
        SokobanGame game = newGame(SokobanConfig.LEVELS[2]);
        assertTrue(game.getBoxCount() > 1, "第三关有多个箱子");
        // 先把左边那只推上去
        assertTrue(game.move(-1, 0), "走到左边箱子下方");
        assertTrue(game.move(0, -1));
        assertTrue(game.move(0, -1));
        assertEquals(1, game.boxesOnTarget());
        assertFalse(game.isWin(), "还有一个箱子没归位");
    }

    @Test
    @DisplayName("过关后不再接受操作")
    void cannotMoveAfterWin() {
        SokobanGame game = newGame(SIMPLE);
        assertTrue(game.move(1, 0));
        assertTrue(game.isWin());
        assertFalse(game.move(-1, 0), "过关后不应再能走");
        assertFalse(game.undo(), "过关后不应再能撤销");
    }

    // =====================================================================
    // 撤销
    // =====================================================================

    @Test
    @DisplayName("撤销：人走回去、箱子也退回去")
    void undoRestoresBoxAndPlayer() {
        SokobanGame game = newGame(PUSHABLE);
        assertTrue(game.move(1, 0));
        assertFalse(game.isWin(), "这一推不应过关（目标点还在更远处）");
        assertTrue(game.canUndo());
        assertTrue(game.undo());
        assertEquals(2, game.getPlayerX(), "玩家应退回原位");
        assertTrue(game.hasBox(3, 2), "箱子应退回原位");
        assertFalse(game.hasBox(4, 2));
        assertEquals(0, game.getMoveCount());
        assertEquals(0, game.getPushCount());
    }

    @Test
    @DisplayName("撤销：撤销推箱会减推动数，撤销普通移动不会")
    void undoCountsPushesSeparately() {
        SokobanGame game = newGame(SokobanConfig.LEVELS[1]);
        assertTrue(game.move(1, 0), "向右挪一格");
        assertEquals(1, game.getMoveCount());
        assertEquals(0, game.getPushCount());
        assertTrue(game.move(0, -1), "向上推箱");
        assertEquals(2, game.getMoveCount());
        assertEquals(1, game.getPushCount());

        assertTrue(game.undo(), "撤销这次推箱");
        assertEquals(1, game.getMoveCount());
        assertEquals(0, game.getPushCount(), "推动数应一起退回去");

        assertTrue(game.undo(), "撤销普通移动");
        assertEquals(0, game.getMoveCount());
    }

    @Test
    @DisplayName("撤销：过关之后就不允许再撤销了")
    void cannotUndoAfterWin() {
        SokobanGame game = newGame(SIMPLE);
        assertTrue(game.move(1, 0));
        assertTrue(game.isWin());
        assertFalse(game.canUndo(), "已过关，视为定局");
        assertFalse(game.undo());
    }

    @Test
    @DisplayName("撤销：没有历史时返回 false")
    void undoWithoutHistory() {
        SokobanGame game = newGame(SIMPLE);
        assertFalse(game.canUndo());
        assertFalse(game.undo());
    }

    @Test
    @DisplayName("撤销：一系列操作能完全还原")
    void undoEverythingReturnsToStart() {
        SokobanGame game = newGame(PUSHABLE);
        assertTrue(game.move(1, 0));
        assertTrue(game.move(1, 0));
        assertFalse(game.isWin(), "推两格仍未到目标点");
        assertEquals(2, game.getPushCount());
        while (game.canUndo()) {
            game.undo();
        }
        assertEquals(0, game.getMoveCount());
        assertEquals(0, game.getPushCount());
        assertEquals(2, game.getPlayerX(), "回到初始位置");
        assertEquals(2, game.getPlayerY());
        assertTrue(game.hasBox(3, 2), "箱子回到初始位置");
        assertEquals(0, game.boxesOnTarget());
    }

    // =====================================================================
    // 计时与关卡切换
    // =====================================================================

    @Test
    @DisplayName("计时：随时间累加，过关后停止")
    void clockRunsThenStops() {
        SokobanGame game = newGame(SIMPLE);
        game.tick(2.5);
        assertEquals(2.5, game.getElapsed(), 0.0001);
        game.move(1, 0);
        assertTrue(game.isWin());
        double frozen = game.getElapsed();
        game.tick(10);
        assertEquals(frozen, game.getElapsed(), 0.0001, "过关后计时不应再走");
    }

    @Test
    @DisplayName("关卡：能进下一关，最后一关不能再进")
    void nextLevelWalksThroughAllLevels() {
        SokobanConfig config = new SokobanConfig();
        SokobanGame game = new SokobanGame();
        game.reset(config);
        for (int i = 0; i < config.getLevelCount() - 1; i++) {
            assertTrue(game.hasNextLevel(), "第 " + (i + 1) + " 关后面还有");
            assertTrue(game.nextLevel());
        }
        assertEquals(config.getLevelCount() - 1, game.getLevelIndex());
        assertFalse(game.hasNextLevel(), "最后一关后面没有了");
        assertFalse(game.nextLevel());
    }

    @Test
    @DisplayName("几何：关卡在画布里居中")
    void levelIsCentered() {
        SokobanConfig config = new SokobanConfig();
        double margin = config.getMargin();
        assertEquals(margin, config.getOffsetX(config.getMaxCols()), 0.0001,
                "最大关卡正好占满，偏移就是外边距");
        assertEquals(margin, config.getOffsetY(config.getMaxRows()), 0.0001);
        assertTrue(config.getOffsetX(5) > margin, "窄关卡应再往右偏一点");
        assertNotEquals(0.0, config.getViewWidth());
    }

    // =====================================================================
    // 关卡质量：由 BFS 穷举保证有解
    // =====================================================================

    @Test
    @DisplayName("关卡：每关都恰好一个玩家，且箱子数不超过目标点数")
    void levelsAreWellFormed() {
        for (int i = 0; i < SokobanConfig.LEVELS.length; i++) {
            String[] level = SokobanConfig.LEVELS[i];
            int players = 0;
            int boxes = 0;
            int targets = 0;
            for (String row : level) {
                for (char c : row.toCharArray()) {
                    if (c == '@' || c == '+') {
                        players++;
                    }
                    if (c == '$' || c == '*') {
                        boxes++;
                    }
                    if (c == '.' || c == '*' || c == '+') {
                        targets++;
                    }
                }
            }
            assertEquals(1, players, "第 " + (i + 1) + " 关应恰好一个玩家");
            assertTrue(boxes > 0, "第 " + (i + 1) + " 关应有箱子");
            assertTrue(boxes <= targets, "第 " + (i + 1) + " 关箱子不应多于目标点");
        }
    }

    @Test
    @DisplayName("关卡：所有内置关卡都被 BFS 验证为可解")
    void allLevelsAreSolvable() {
        for (int i = 0; i < SokobanConfig.LEVELS.length; i++) {
            int steps = solveMinMoves(SokobanConfig.LEVELS[i]);
            assertTrue(steps > 0,
                    "第 " + (i + 1) + " 关解不出来（BFS 已穷举），请检查关卡设计");
        }
    }

    @Test
    @DisplayName("关卡：第一关的题面信息量足够（至少一步）")
    void firstLevelNeedsAtLeastOneMove() {
        assertEquals(1, solveMinMoves(SIMPLE), "最小关正好一步");
    }

    // =====================================================================
    // BFS 求解器（仅测试使用）
    // =====================================================================

    /**
     * 广度优先穷举求解：返回最少**步数**；不可解返回 {@code -1}。
     *
     * <p>状态 = (玩家位置, 所有箱子的位图)。地图很小（最大 9×7=63 格），
     * 用 {@code long} 当位图正好。</p>
     */
    private static int solveMinMoves(String[] rows) {
        int height = rows.length;
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        boolean[][] wall = new boolean[width][height];
        long targetBits = 0L;
        long boxBits = 0L;
        int playerX = -1;
        int playerY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                char c = rows[y].charAt(x);
                wall[x][y] = c == '#';
                if (c == '.' || c == '*' || c == '+') {
                    targetBits |= bit(x, y, width);
                }
                if (c == '$' || c == '*') {
                    boxBits |= bit(x, y, width);
                }
                if (c == '@' || c == '+') {
                    playerX = x;
                    playerY = y;
                }
            }
        }

        Set<String> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        Deque<Long> masks = new ArrayDeque<>();
        queue.add(new int[]{playerX, playerY, 0});
        masks.add(boxBits);
        seen.add(playerX + "," + playerY + "," + boxBits);

        while (!queue.isEmpty()) {
            int[] head = queue.poll();
            long mask = masks.poll();
            if (allOnTarget(mask, targetBits)) {
                return head[2];
            }
            for (int[] dir : SokobanGame.DIRECTIONS) {
                int nextX = head[0] + dir[0];
                int nextY = head[1] + dir[1];
                if (outOfBounds(nextX, nextY, width, height) || wall[nextX][nextY]) {
                    continue;
                }
                long nextMask = mask;
                if ((mask & bit(nextX, nextY, width)) != 0L) {
                    int boxX = nextX + dir[0];
                    int boxY = nextY + dir[1];
                    if (outOfBounds(boxX, boxY, width, height) || wall[boxX][boxY]
                            || (mask & bit(boxX, boxY, width)) != 0L) {
                        continue;
                    }
                    nextMask = (mask & ~bit(nextX, nextY, width)) | bit(boxX, boxY, width);
                }
                String key = nextX + "," + nextY + "," + nextMask;
                if (seen.add(key)) {
                    queue.add(new int[]{nextX, nextY, head[2] + 1});
                    masks.add(nextMask);
                }
            }
        }
        return -1;
    }

    private static boolean allOnTarget(long boxBits, long targetBits) {
        return (boxBits & ~targetBits) == 0L;
    }

    private static long bit(int x, int y, int width) {
        return 1L << (y * width + x);
    }

    private static boolean outOfBounds(int x, int y, int width, int height) {
        return x < 0 || x >= width || y < 0 || y >= height;
    }

    /** 保留给以后可能的求解器测试扩展。 */
    private static List<int[]> unusedDirections() {
        return new ArrayList<>();
    }
}
