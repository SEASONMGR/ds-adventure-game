package com.studio.plugin.demo.sokoban;

/**
 * 「推箱子」小游戏的关卡与几何布局。
 *
 * <p>关卡用字符画描述，一眼能看出地形，改起来也方便：</p>
 * <pre>
 *   #  墙          @  玩家        $  箱子
 *   .  目标点      *  箱子已在目标上   +  玩家站在目标上
 *   空格 地板
 * </pre>
 *
 * <p>所有内置关卡都由 {@code SokobanGameTest} 里的 BFS 求解器验证**必定有解**，
 * 改关卡后跑测试即可，不用手工试。</p>
 */
public class SokobanConfig {

    /** 内置关卡（剧情第 8 章只需要第一关，其余用于演示与自测）。 */
    public static final String[][] LEVELS = {
        {
            "#######",
            "#     #",
            "# @$. #",
            "#     #",
            "#######",
        },
        {
            "#######",
            "#     #",
            "#  .  #",
            "#     #",
            "#  $  #",
            "# @   #",
            "#######",
        },
        {
            "#########",
            "#       #",
            "#  . .  #",
            "#       #",
            "#  $ $  #",
            "#   @   #",
            "#########",
        },
    };

    /** 当前是第几关（0 基）。 */
    private int levelIndex;
    /** 每格边长（像素）。 */
    private double cellSize = 54;
    /** 画布外边距（像素）。 */
    private double margin = 30;

    /** 关卡数。 */
    public int getLevelCount() {
        return LEVELS.length;
    }

    public int getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(int levelIndex) {
        this.levelIndex = Math.max(0, Math.min(LEVELS.length - 1, levelIndex));
    }

    /** 当前关卡的字符画。 */
    public String[] getCurrentLevel() {
        return LEVELS[levelIndex];
    }

    /** 所有关卡里最宽的一关有几列 —— 画布按它定尺寸，切关时不会跳。 */
    public int getMaxCols() {
        int max = 0;
        for (String[] level : LEVELS) {
            for (String row : level) {
                max = Math.max(max, row.length());
            }
        }
        return max;
    }

    /** 所有关卡里最高的一关有几行。 */
    public int getMaxRows() {
        int max = 0;
        for (String[] level : LEVELS) {
            max = Math.max(max, level.length);
        }
        return max;
    }

    public double getViewWidth() {
        return margin * 2 + getMaxCols() * cellSize;
    }

    public double getViewHeight() {
        return margin * 2 + getMaxRows() * cellSize;
    }

    /** 把一关居中摆进画布：算出左上角像素偏移。 */
    public double getOffsetX(int cols) {
        return margin + (getMaxCols() - cols) * cellSize / 2.0;
    }

    public double getOffsetY(int rows) {
        return margin + (getMaxRows() - rows) * cellSize / 2.0;
    }

    /** 第 x 列、第 y 行那格左上角的像素坐标。 */
    public double getPixelX(int x, int cols) {
        return getOffsetX(cols) + x * cellSize;
    }

    public double getPixelY(int y, int rows) {
        return getOffsetY(rows) + y * cellSize;
    }

    public double getCellSize() {
        return cellSize;
    }

    public void setCellSize(double cellSize) {
        this.cellSize = cellSize;
    }

    public double getMargin() {
        return margin;
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }
}
