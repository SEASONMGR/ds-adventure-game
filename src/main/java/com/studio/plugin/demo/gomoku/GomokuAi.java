package com.studio.plugin.demo.gomoku;

/**
 * 五子棋 AI —— 按「威胁打分」给每个空点估值，取最高分落子。
 *
 * <p>思路：假设在某个空点落子，统计四个方向上的棋形（连子数 + 两头是否空），
 * 查表得出进攻分；对对手做同样的事得到防守分。进攻分 + 防守分 × 权重即为该点总分。</p>
 *
 * <p>因为"连成必胜"的分值远大于其它棋形，所以自然形成优先级：
 * <b>自己能赢 → 堵对手的四 → 做自己的活三 → 堵对手的活三 → …</b></p>
 *
 * <p>15×15 只有 225 个点，直接暴力枚举即可，不需要搜索树。</p>
 */
public final class GomokuAi {

    /** 四个扫描方向：横、竖、两条斜线。 */
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /** 连成必胜的分值，压过所有其它棋形。 */
    private static final int SCORE_WIN = 10_000_000;
    /** 防守权重：略低于进攻，所以"自己能赢"优先于"堵对手"。 */
    private static final double DEFENSE_WEIGHT = 0.9;
    /** 同分时倾向中心的加权上限，仅用于打破僵局。 */
    private static final int CENTER_BONUS = 12;

    private GomokuAi() {
    }

    /**
     * 选出 AI 的落点。
     *
     * @param board     棋盘快照（{@code board[x][y]}，元素取值见 {@link GomokuGame}）
     * @param color     AI 的棋子颜色
     * @param winLength 连成多少子获胜
     * @return 落点 {@code {x, y}}；棋盘已满返回 {@code null}
     */
    public static int[] chooseMove(int[][] board, int color, int winLength) {
        int size = board.length;
        int opponent = color == GomokuGame.BLACK ? GomokuGame.WHITE : GomokuGame.BLACK;
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (board[x][y] != GomokuGame.EMPTY) {
                    continue;
                }
                int score = evaluate(board, x, y, color, opponent, winLength);
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** 某个空点的总分 = 进攻 + 防守 × 权重 + 中心倾向。 */
    private static int evaluate(int[][] board, int x, int y, int color, int opponent, int winLength) {
        int attack = scoreAt(board, x, y, color, winLength);
        int defend = scoreAt(board, x, y, opponent, winLength);
        return attack + (int) (defend * DEFENSE_WEIGHT) + centerBonus(x, y, board.length);
    }

    /** 假设在 (x, y) 落 color 子，四个方向的棋形总分。 */
    private static int scoreAt(int[][] board, int x, int y, int color, int winLength) {
        int total = 0;
        for (int[] dir : DIRECTIONS) {
            int run = 1
                    + runLength(board, x, y, dir[0], dir[1], color)
                    + runLength(board, x, y, -dir[0], -dir[1], color);
            int open = openEnds(board, x, y, dir[0], dir[1], color)
                    + openEnds(board, x, y, -dir[0], -dir[1], color);
            total += patternScore(run, open, winLength);
        }
        return total;
    }

    /** 沿 (dx, dy) 方向数连续同色子的个数（不含起点）。 */
    private static int runLength(int[][] board, int x, int y, int dx, int dy, int color) {
        int size = board.length;
        int count = 0;
        int cx = x + dx;
        int cy = y + dy;
        while (inBounds(cx, cy, size) && board[cx][cy] == color) {
            count++;
            cx += dx;
            cy += dy;
        }
        return count;
    }

    /** 该方向连续同色子之后的第一格是不是空位（即这一头"活着"）。 */
    private static int openEnds(int[][] board, int x, int y, int dx, int dy, int color) {
        int size = board.length;
        int cx = x + dx;
        int cy = y + dy;
        while (inBounds(cx, cy, size) && board[cx][cy] == color) {
            cx += dx;
            cy += dy;
        }
        return inBounds(cx, cy, size) && board[cx][cy] == GomokuGame.EMPTY ? 1 : 0;
    }

    /**
     * 棋形打分。
     *
     * @param run       连子数（含假设落下的这一子）
     * @param open      两头空位的数量（0 / 1 / 2）
     * @param winLength 获胜所需连子数
     */
    private static int patternScore(int run, int open, int winLength) {
        if (open == 0) {
            return 0;
        }
        if (run >= winLength) {
            return SCORE_WIN;
        }
        if (run == winLength - 1) {
            return open == 2 ? 500_000 : 60_000;
        }
        if (run == winLength - 2) {
            return open == 2 ? 40_000 : 3_000;
        }
        if (run == winLength - 3) {
            return open == 2 ? 2_000 : 300;
        }
        if (run == winLength - 4) {
            return open == 2 ? 150 : 40;
        }
        return open == 2 ? 10 : 2;
    }

    /** 越靠中心越优先，仅用于打破同分僵局。 */
    private static int centerBonus(int x, int y, int size) {
        int mid = size / 2;
        return Math.max(0, CENTER_BONUS - (Math.abs(x - mid) + Math.abs(y - mid)));
    }

    private static boolean inBounds(int x, int y, int size) {
        return x >= 0 && x < size && y >= 0 && y < size;
    }
}
