package com.studio.plugin.demo.sokoban;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 「推箱子」纯规则引擎 —— <b>零 JavaFX 依赖</b>，可脱离界面做单元测试
 * （对齐 {@code SnakeGame} 的范例，见 CONTRIBUTING §2.7）。
 *
 * <p>规矩就一条：人能**推**不能**拉**，一次推一个；箱子抵墙或抵别的箱子就推不动。</p>
 *
 * <p>坐标系：{@code (x, y)}，x 是列、y 是行，左上角为 (0, 0)。</p>
 */
public class SokobanGame {

    /** 四个方向：上、右、下、左。 */
    public static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    /** 玩家在字符画里的记法。 */
    private static final char CH_WALL = '#';
    private static final char CH_PLAYER = '@';
    private static final char CH_BOX = '$';
    private static final char CH_TARGET = '.';
    private static final char CH_BOX_ON_TARGET = '*';
    private static final char CH_PLAYER_ON_TARGET = '+';

    /** 撤销栈里的一步：玩家从哪来，箱子从哪到哪（没有推箱时箱子坐标为 −1）。 */
    private record Move(int playerX, int playerY,
                        int boxFromX, int boxFromY, int boxToX, int boxToY) {
    }

    private SokobanConfig config;
    private boolean[][] wall;
    private boolean[][] target;
    private boolean[][] box;
    private int width;
    private int height;
    private int playerX;
    private int playerY;
    private int boxCount;
    private int moveCount;
    private int pushCount;
    private double elapsed;
    private final Deque<Move> history = new ArrayDeque<>();

    /** 载入配置里指定的那一关。 */
    public void reset(SokobanConfig config) {
        this.config = config;
        loadLevel(config.getCurrentLevel());
    }

    /** 解析字符画并重置计数。 */
    public void loadLevel(String[] rows) {
        height = rows.length;
        width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        wall = new boolean[width][height];
        target = new boolean[width][height];
        box = new boolean[width][height];
        history.clear();
        boxCount = 0;
        moveCount = 0;
        pushCount = 0;
        elapsed = 0;

        for (int y = 0; y < height; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                wall[x][y] = c == CH_WALL;
                target[x][y] = c == CH_TARGET || c == CH_BOX_ON_TARGET || c == CH_PLAYER_ON_TARGET;
                box[x][y] = c == CH_BOX || c == CH_BOX_ON_TARGET;
                if (c == CH_BOX || c == CH_BOX_ON_TARGET) {
                    boxCount++;
                }
                if (c == CH_PLAYER || c == CH_PLAYER_ON_TARGET) {
                    playerX = x;
                    playerY = y;
                }
            }
        }
    }

    /**
     * 走一步（需要的话顺势推箱子）。
     *
     * @param dx 横向增量（−1 / 0 / 1）
     * @param dy 纵向增量（−1 / 0 / 1）
     * @return 走成功返回 {@code true}；撞墙、推不动或已过关返回 {@code false}
     */
    public boolean move(int dx, int dy) {
        if (isWin()) {
            return false;
        }
        int nextX = playerX + dx;
        int nextY = playerY + dy;
        if (isWall(nextX, nextY)) {
            return false;
        }
        if (box[nextX][nextY]) {
            int boxX = nextX + dx;
            int boxY = nextY + dy;
            if (isWall(boxX, boxY) || box[boxX][boxY]) {
                return false;
            }
            box[nextX][nextY] = false;
            box[boxX][boxY] = true;
            history.push(new Move(playerX, playerY, nextX, nextY, boxX, boxY));
            pushCount++;
        } else {
            history.push(new Move(playerX, playerY, -1, -1, -1, -1));
        }
        playerX = nextX;
        playerY = nextY;
        moveCount++;
        return true;
    }

    /** 撤销上一步；没有可撤销的返回 {@code false}。 */
    public boolean undo() {
        if (history.isEmpty() || isWin()) {
            return false;
        }
        Move last = history.pop();
        if (last.boxFromX() >= 0) {
            box[last.boxToX()][last.boxToY()] = false;
            box[last.boxFromX()][last.boxFromY()] = true;
            pushCount--;
        }
        playerX = last.playerX();
        playerY = last.playerY();
        moveCount--;
        return true;
    }

    public boolean canUndo() {
        return !history.isEmpty() && !isWin();
    }

    /** 计时；过关后不再累加。 */
    public void tick(double dt) {
        if (!isWin()) {
            elapsed += dt;
        }
    }

    /** 所有箱子都推到目标点上就算过关。 */
    public boolean isWin() {
        if (boxCount == 0) {
            return false;
        }
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (box[x][y] && !target[x][y]) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 已归位的箱子数（界面用来显示进度）。 */
    public int boxesOnTarget() {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (box[x][y] && target[x][y]) {
                    count++;
                }
            }
        }
        return count;
    }

    public boolean isWall(int x, int y) {
        return !inBounds(x, y) || wall[x][y];
    }

    public boolean isTarget(int x, int y) {
        return inBounds(x, y) && target[x][y];
    }

    public boolean hasBox(int x, int y) {
        return inBounds(x, y) && box[x][y];
    }

    public boolean isPlayer(int x, int y) {
        return x == playerX && y == playerY;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getPlayerX() {
        return playerX;
    }

    public int getPlayerY() {
        return playerY;
    }

    public int getBoxCount() {
        return boxCount;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public int getPushCount() {
        return pushCount;
    }

    public double getElapsed() {
        return elapsed;
    }

    public int getLevelIndex() {
        return config.getLevelIndex();
    }

    public int getLevelCount() {
        return config.getLevelCount();
    }

    public boolean hasNextLevel() {
        return config.getLevelIndex() < config.getLevelCount() - 1;
    }

    /** 进下一关；已是最后一关返回 {@code false}。 */
    public boolean nextLevel() {
        if (!hasNextLevel()) {
            return false;
        }
        config.setLevelIndex(config.getLevelIndex() + 1);
        loadLevel(config.getCurrentLevel());
        return true;
    }
}
