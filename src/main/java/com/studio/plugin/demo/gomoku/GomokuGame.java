package com.studio.plugin.demo.gomoku;

import java.util.ArrayList;
import java.util.List;

/**
 * 「五子棋」纯规则引擎 —— <b>零 JavaFX 依赖</b>，可脱离界面做单元测试
 * （对齐 {@code SnakeGame} 的范例，见 CONTRIBUTING §2.7）。
 *
 * <p>只负责：落子合法性、胜负判定、计时。界面渲染与 AI 思考节奏都不在这里。</p>
 *
 * <p>坐标系：{@code board[x][y]}，x 是列、y 是行，左上角为 (0, 0)。</p>
 */
public class GomokuGame {

    /** 空点。 */
    public static final int EMPTY = 0;
    /** 黑子（先手）。 */
    public static final int BLACK = 1;
    /** 白子（后手）。 */
    public static final int WHITE = 2;
    /** {@link #winner()} 的取值：尚未分出胜负。 */
    public static final int NO_WINNER = 0;
    /** {@link #winner()} 的取值：和棋（棋盘下满且无人连成 {@code winLength} 子）。 */
    public static final int DRAW = 3;

    /** 四个扫描方向：横、竖、两条斜线。 */
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private GomokuConfig config;
    private int[][] board;
    private int current;
    private int winner = NO_WINNER;
    private int moveCount;
    private double elapsed;
    private int lastX = -1;
    private int lastY = -1;
    private final List<int[]> winLine = new ArrayList<>();
    private int humanColor = BLACK;
    private int aiColor = WHITE;

    /** 开新局：清空棋盘，按配置决定谁先手。 */
    public void reset(GomokuConfig config) {
        this.config = config;
        int size = config.getBoardSize();
        board = new int[size][size];
        humanColor = config.isHumanFirst() ? BLACK : WHITE;
        aiColor = other(humanColor);
        current = BLACK;
        winner = NO_WINNER;
        moveCount = 0;
        elapsed = 0;
        lastX = -1;
        lastY = -1;
        winLine.clear();
    }

    /** 当前是不是轮到 AI。 */
    public boolean isAiTurn() {
        return !isOver() && current == aiColor;
    }

    /** 当前是不是轮到人类。 */
    public boolean isHumanTurn() {
        return !isOver() && current == humanColor;
    }

    /** 该点是否可以落子。 */
    public boolean isLegal(int x, int y) {
        return !isOver() && inBounds(x, y) && board[x][y] == EMPTY;
    }

    /**
     * 在 (x, y) 落一子（当前方）。
     *
     * @return 落子成功返回 {@code true}；越界、已占用或已分出胜负返回 {@code false}
     */
    public boolean place(int x, int y) {
        if (!isLegal(x, y)) {
            return false;
        }
        int color = current;
        board[x][y] = color;
        lastX = x;
        lastY = y;
        moveCount++;
        if (collectWinLine(x, y, color)) {
            winner = color;
        } else if (moveCount >= config.getBoardSize() * config.getBoardSize()) {
            winner = DRAW;
        } else {
            current = other(current);
        }
        return true;
    }

    /**
     * 从刚落下的子出发，四个方向数连续同色。
     *
     * @return 连成 {@code winLength} 子返回 {@code true}，并记录成五的那条线
     */
    private boolean collectWinLine(int x, int y, int color) {
        for (int[] dir : DIRECTIONS) {
            List<int[]> line = new ArrayList<>();
            line.add(new int[]{x, y});
            countSide(line, x, y, dir[0], dir[1], color);
            countSide(line, x, y, -dir[0], -dir[1], color);
            if (line.size() >= config.getWinLength()) {
                winLine.clear();
                winLine.addAll(line);
                return true;
            }
        }
        return false;
    }

    /** 沿 (dx, dy) 方向收集连续同色点，直到越界或遇到异色。 */
    private void countSide(List<int[]> line, int x, int y, int dx, int dy, int color) {
        int size = config.getBoardSize();
        int cx = x + dx;
        int cy = y + dy;
        while (cx >= 0 && cx < size && cy >= 0 && cy < size && board[cx][cy] == color) {
            line.add(new int[]{cx, cy});
            cx += dx;
            cy += dy;
        }
    }

    /** 推进计时；分出胜负后不再累加。 */
    public void tick(double dt) {
        if (!isOver()) {
            elapsed += dt;
        }
    }

    public boolean isOver() {
        return winner != NO_WINNER;
    }

    public boolean isWin() {
        return winner == humanColor;
    }

    public boolean isLose() {
        return winner == aiColor;
    }

    public boolean isDraw() {
        return winner == DRAW;
    }

    public int winner() {
        return winner;
    }

    public int current() {
        return current;
    }

    public int at(int x, int y) {
        return inBounds(x, y) ? board[x][y] : EMPTY;
    }

    public int getBoardSize() {
        return config.getBoardSize();
    }

    public int moveCount() {
        return moveCount;
    }

    public double elapsed() {
        return elapsed;
    }

    public int lastX() {
        return lastX;
    }

    public int lastY() {
        return lastY;
    }

    public int humanColor() {
        return humanColor;
    }

    public int aiColor() {
        return aiColor;
    }

    /** 成五的那条线（每个元素是 {x, y}）；没有人为空。 */
    public List<int[]> winLine() {
        return winLine;
    }

    /**
     * 棋盘快照（副本）。
     *
     * <p>返回副本而不是内部数组，避免调用方无意中改动棋局；AI 与渲染都用它。</p>
     */
    public int[][] snapshot() {
        int size = config.getBoardSize();
        int[][] copy = new int[size][size];
        for (int x = 0; x < size; x++) {
            copy[x] = board[x].clone();
        }
        return copy;
    }

    private boolean inBounds(int x, int y) {
        int size = config.getBoardSize();
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    private static int other(int color) {
        return color == BLACK ? WHITE : BLACK;
    }
}
