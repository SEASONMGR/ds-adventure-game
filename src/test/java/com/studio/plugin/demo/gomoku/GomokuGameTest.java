package com.studio.plugin.demo.gomoku;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 五子棋规则引擎与 AI 的单元测试。
 *
 * <p>纯规则、无 JavaFX 依赖，可在无图形环境下运行（对齐 SnakeGameTest 的范例）。</p>
 */
class GomokuGameTest {

    private static final int SIZE = 15;

    private static GomokuConfig config() {
        return new GomokuConfig();
    }

    private static GomokuGame newGame() {
        GomokuGame game = new GomokuGame();
        game.reset(config());
        return game;
    }

    private static int[][] emptyBoard(int size) {
        return new int[size][size];
    }

    /** 黑棋连成五子：先交替落 4 组，最后黑补第 5 子。 */
    private static void playBlackFive(GomokuGame game, int[][] black, int[][] white) {
        for (int i = 0; i < white.length; i++) {
            assertTrue(game.place(black[i][0], black[i][1]), "黑第 " + (i + 1) + " 手");
            assertTrue(game.place(white[i][0], white[i][1]), "白第 " + (i + 1) + " 手");
        }
        assertTrue(game.place(black[4][0], black[4][1]), "黑第 5 手");
    }

    // =====================================================================
    // 基础规则
    // =====================================================================

    @Test
    @DisplayName("开局：棋盘全空、黑先手、无人获胜")
    void freshBoardIsEmpty() {
        GomokuGame game = newGame();
        assertEquals(SIZE, game.getBoardSize(), "默认 15 路");
        assertEquals(GomokuGame.BLACK, game.current(), "黑棋先手");
        assertFalse(game.isOver());
        assertEquals(0, game.moveCount());
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                assertEquals(GomokuGame.EMPTY, game.at(x, y), "(" + x + "," + y + ") 应为空");
            }
        }
    }

    @Test
    @DisplayName("落子：写入棋盘、记录手数、轮到对方")
    void placeWritesStoneAndSwitchesTurn() {
        GomokuGame game = newGame();
        assertTrue(game.place(7, 7));
        assertEquals(GomokuGame.BLACK, game.at(7, 7));
        assertEquals(GomokuGame.WHITE, game.current(), "落子后应轮到白棋");
        assertEquals(1, game.moveCount());
        assertEquals(7, game.lastX());
        assertEquals(7, game.lastY());
    }

    @Test
    @DisplayName("落子：同一格不能重复下")
    void cannotPlaceTwiceOnSamePoint() {
        GomokuGame game = newGame();
        assertTrue(game.place(7, 7));
        assertFalse(game.place(7, 7), "已占用的点应被拒绝");
        assertEquals(1, game.moveCount());
    }

    @Test
    @DisplayName("落子：越界被拒绝")
    void cannotPlaceOutsideBoard() {
        GomokuGame game = newGame();
        assertFalse(game.place(-1, 0));
        assertFalse(game.place(0, -1));
        assertFalse(game.place(SIZE, 0));
        assertFalse(game.place(0, SIZE));
        assertEquals(0, game.moveCount());
    }

    @Test
    @DisplayName("落子：分出胜负后不能再下")
    void cannotPlaceAfterGameOver() {
        GomokuGame game = newGame();
        playBlackFive(game,
                new int[][]{{0, 7}, {1, 7}, {2, 7}, {3, 7}, {4, 7}},
                new int[][]{{0, 9}, {3, 9}, {6, 9}, {9, 9}});
        assertFalse(game.place(10, 10), "已结束的棋局不应再接受落子");
    }

    // =====================================================================
    // 胜负判定：四个方向
    // =====================================================================

    @Test
    @DisplayName("胜负：横向五连获胜")
    void horizontalFiveWins() {
        GomokuGame game = newGame();
        playBlackFive(game,
                new int[][]{{0, 7}, {1, 7}, {2, 7}, {3, 7}, {4, 7}},
                new int[][]{{0, 9}, {3, 9}, {6, 9}, {9, 9}});
        assertTrue(game.isOver());
        assertTrue(game.isWin(), "黑棋（人类）应获胜");
        assertEquals(GomokuGame.BLACK, game.winner());
        assertEquals(5, game.winLine().size(), "应记录成五的五个点");
    }

    @Test
    @DisplayName("胜负：纵向五连获胜")
    void verticalFiveWins() {
        GomokuGame game = newGame();
        playBlackFive(game,
                new int[][]{{7, 0}, {7, 1}, {7, 2}, {7, 3}, {7, 4}},
                new int[][]{{9, 0}, {9, 3}, {9, 6}, {9, 9}});
        assertTrue(game.isWin(), "纵向五连也应获胜");
        assertEquals(5, game.winLine().size());
    }

    @Test
    @DisplayName("胜负：右下斜线五连获胜")
    void diagonalDownWins() {
        GomokuGame game = newGame();
        playBlackFive(game,
                new int[][]{{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}},
                new int[][]{{7, 0}, {9, 0}, {11, 0}, {13, 0}});
        assertTrue(game.isWin(), "右下斜线五连也应获胜");
    }

    @Test
    @DisplayName("胜负：左下斜线五连获胜")
    void diagonalUpWins() {
        GomokuGame game = newGame();
        playBlackFive(game,
                new int[][]{{4, 0}, {3, 1}, {2, 2}, {1, 3}, {0, 4}},
                new int[][]{{7, 0}, {9, 0}, {11, 0}, {13, 0}});
        assertTrue(game.isWin(), "左下斜线五连也应获胜");
    }

    @Test
    @DisplayName("胜负：四连不算获胜")
    void fourInARowIsNotWin() {
        GomokuGame game = newGame();
        int[][] moves = {{0, 7}, {0, 9}, {1, 7}, {3, 9}, {2, 7}, {6, 9}, {3, 7}, {9, 9}};
        for (int[] move : moves) {
            assertTrue(game.place(move[0], move[1]));
        }
        assertFalse(game.isOver(), "只有四连不应判胜");
    }

    @Test
    @DisplayName("胜负：被对方隔断的四连不算获胜")
    void blockedFourIsNotWin() {
        GomokuGame game = newGame();
        int[][] moves = {{0, 7}, {2, 7}, {1, 7}, {5, 9}, {3, 7}, {6, 9}, {4, 7}, {9, 9}};
        for (int[] move : moves) {
            assertTrue(game.place(move[0], move[1]));
        }
        assertFalse(game.isOver(), "被隔断的四连不应判胜");
    }

    @Test
    @DisplayName("胜负：长连（连成六子）同样获胜")
    void overlineAlsoWins() {
        GomokuGame game = newGame();
        int[][] black = {{0, 7}, {1, 7}, {2, 7}, {3, 7}, {5, 7}};
        int[][] white = {{0, 9}, {2, 9}, {4, 9}, {6, 9}};
        for (int i = 0; i < white.length; i++) {
            assertTrue(game.place(black[i][0], black[i][1]));
            assertTrue(game.place(white[i][0], white[i][1]));
        }
        assertTrue(game.place(black[4][0], black[4][1]), "黑第 5 手落在缺口右侧");
        assertFalse(game.isOver(), "中间留着缺口时不应判胜");

        assertTrue(game.place(8, 9), "白垫一手");
        assertTrue(game.place(4, 7), "黑补上缺口");
        assertTrue(game.isWin(), "六连也应获胜");
        assertEquals(6, game.winLine().size(), "应记录六连的六个点");
    }

    // =====================================================================
    // 边界与状态
    // =====================================================================

    @Test
    @DisplayName("和棋：小棋盘下满且无人连成即判和")
    void fullBoardIsDraw() {
        GomokuConfig config = config();
        config.setBoardSize(5);
        config.setWinLength(6); // 5×5 上不可能连成 6 子
        GomokuGame game = new GomokuGame();
        game.reset(config);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                assertTrue(game.place(x, y), "(" + x + "," + y + ") 应可落子");
            }
        }
        assertTrue(game.isOver(), "棋盘下满应结束");
        assertTrue(game.isDraw(), "无人连成应判和棋");
        assertEquals(GomokuGame.DRAW, game.winner());
    }

    @Test
    @DisplayName("计时：随时间累加，分出胜负后停止")
    void clockRunsThenStops() {
        GomokuGame game = newGame();
        game.tick(1.5);
        assertEquals(1.5, game.elapsed(), 0.0001);

        playBlackFive(game,
                new int[][]{{0, 7}, {1, 7}, {2, 7}, {3, 7}, {4, 7}},
                new int[][]{{0, 9}, {3, 9}, {6, 9}, {9, 9}});
        double frozen = game.elapsed();
        game.tick(10);
        assertEquals(frozen, game.elapsed(), 0.0001, "结束后计时不应再累加");
    }

    @Test
    @DisplayName("重开：reset 清空棋盘与计时")
    void resetClearsEverything() {
        GomokuGame game = newGame();
        game.place(7, 7);
        game.tick(3);
        game.reset(config());
        assertEquals(0, game.moveCount());
        assertEquals(0.0, game.elapsed(), 0.0001);
        assertFalse(game.isOver());
        assertEquals(GomokuGame.EMPTY, game.at(7, 7), "重开后棋盘应为空");
        assertEquals(-1, game.lastX(), "重开后不应残留上一手的坐标");
    }

    @Test
    @DisplayName("先手方：humanFirst=false 时 AI 执黑先行")
    void aiCanMoveFirst() {
        GomokuConfig config = config();
        config.setHumanFirst(false);
        GomokuGame game = new GomokuGame();
        game.reset(config);
        assertEquals(GomokuGame.WHITE, game.humanColor(), "人类应执白");
        assertEquals(GomokuGame.BLACK, game.aiColor(), "AI 应执黑");
        assertTrue(game.isAiTurn(), "AI 先手");
        assertFalse(game.isHumanTurn());
    }

    // =====================================================================
    // AI
    // =====================================================================

    @Test
    @DisplayName("AI：空盘时落在天元")
    void aiOpensAtCenter() {
        int[] move = GomokuAi.chooseMove(emptyBoard(SIZE), GomokuGame.WHITE, 5);
        assertNotNull(move);
        assertEquals(SIZE / 2, move[0]);
        assertEquals(SIZE / 2, move[1]);
    }

    @Test
    @DisplayName("AI：自己差一子时立刻连五")
    void aiTakesWinningMove() {
        int[][] board = emptyBoard(SIZE);
        for (int x = 5; x <= 8; x++) {
            board[x][5] = GomokuGame.WHITE;
        }
        int[] move = GomokuAi.chooseMove(board, GomokuGame.WHITE, 5);
        assertNotNull(move);
        assertEquals(5, move[1], "应补在同一行");
        assertTrue(move[0] == 4 || move[0] == 9, "应在四连的某一端补成五连");
    }

    @Test
    @DisplayName("AI：堵住对手的四连")
    void aiBlocksOpponentFour() {
        int[][] board = emptyBoard(SIZE);
        for (int x = 5; x <= 8; x++) {
            board[x][8] = GomokuGame.BLACK;
        }
        int[] move = GomokuAi.chooseMove(board, GomokuGame.WHITE, 5);
        assertNotNull(move);
        assertEquals(8, move[1], "应堵在对手四连所在的那一行");
        assertTrue(move[0] == 4 || move[0] == 9, "应堵在四连的某一端");
    }

    @Test
    @DisplayName("AI：自己能连五时优先于堵对手")
    void aiPrefersOwnWinOverBlocking() {
        int[][] board = emptyBoard(SIZE);
        for (int x = 5; x <= 8; x++) {
            board[x][3] = GomokuGame.WHITE;  // 自己差一子
            board[x][11] = GomokuGame.BLACK; // 对手也差一子
        }
        int[] move = GomokuAi.chooseMove(board, GomokuGame.WHITE, 5);
        assertNotNull(move);
        assertEquals(3, move[1], "应优先自己连五，而不是先去堵对手");
    }

    @Test
    @DisplayName("AI：不会落在已占用的点")
    void aiPicksEmptyPoint() {
        int[][] board = emptyBoard(SIZE);
        board[SIZE / 2][SIZE / 2] = GomokuGame.BLACK;
        int[] move = GomokuAi.chooseMove(board, GomokuGame.WHITE, 5);
        assertNotNull(move);
        assertEquals(GomokuGame.EMPTY, board[move[0]][move[1]], "落点必须是空位");
    }

    @Test
    @DisplayName("AI：棋盘下满时无处可落")
    void aiReturnsNullWhenBoardFull() {
        int[][] board = new int[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                board[x][y] = GomokuGame.BLACK;
            }
        }
        assertNull(GomokuAi.chooseMove(board, GomokuGame.WHITE, 3), "无空位应返回 null");
    }
}
