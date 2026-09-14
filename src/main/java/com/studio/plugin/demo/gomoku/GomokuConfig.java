package com.studio.plugin.demo.gomoku;

/**
 * 「五子棋」小游戏的参数与几何布局。
 *
 * <p>所有数值集中在这里，方便剧情侧按章节需要微调（例如要改成对手先手，
 * 把 {@link #setHumanFirst(boolean)} 设为 {@code false} 即可）。</p>
 */
public class GomokuConfig {

    /** 棋盘路数：15 路即 15×15 个交叉点（需求文档推荐值）。 */
    private int boardSize = 15;
    /** 连成多少子获胜。 */
    private int winLength = 5;
    /** 相邻两条线的像素间距。 */
    private double cellSize = 40;
    /** 棋盘外边距（像素），保证最外圈线不贴边。 */
    private double margin = 40;
    /** 棋子半径相对格距的比例。 */
    private double stoneRadiusRatio = 0.42;
    /** AI 落子前的"思考"延迟（秒），让对手看起来像在考虑。 */
    private double aiThinkDelay = 0.45;
    /**
     * 人类是否执黑先行。
     *
     * <p>默认 {@code true}：玩家先手更好上手；接入剧情时若需要对手先手，置为 {@code false}。</p>
     */
    private boolean humanFirst = true;

    /** 画布宽度：外边距 × 2 + 格距 × (路数 − 1)。 */
    public double getViewWidth() {
        return margin * 2 + cellSize * (boardSize - 1);
    }

    /** 画布高度：正方形棋盘。 */
    public double getViewHeight() {
        return getViewWidth();
    }

    /** 第 x 条竖线的像素横坐标。 */
    public double getPixelX(int x) {
        return margin + x * cellSize;
    }

    /** 第 y 条横线的像素纵坐标。 */
    public double getPixelY(int y) {
        return margin + y * cellSize;
    }

    /** 棋子半径（像素）。 */
    public double getStoneRadius() {
        return cellSize * stoneRadiusRatio;
    }

    /** 把像素横坐标换算成最近的路数；超出边界返回 −1。 */
    public int toBoardX(double pixelX) {
        return toBoardIndex((pixelX - margin) / cellSize);
    }

    /** 把像素纵坐标换算成最近的路数；超出边界返回 −1。 */
    public int toBoardY(double pixelY) {
        return toBoardIndex((pixelY - margin) / cellSize);
    }

    private int toBoardIndex(double raw) {
        int index = (int) Math.round(raw);
        return index >= 0 && index < boardSize ? index : -1;
    }

    public int getBoardSize() {
        return boardSize;
    }

    public void setBoardSize(int boardSize) {
        this.boardSize = boardSize;
    }

    public int getWinLength() {
        return winLength;
    }

    public void setWinLength(int winLength) {
        this.winLength = winLength;
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

    public double getAiThinkDelay() {
        return aiThinkDelay;
    }

    public void setAiThinkDelay(double aiThinkDelay) {
        this.aiThinkDelay = aiThinkDelay;
    }

    public boolean isHumanFirst() {
        return humanFirst;
    }

    public void setHumanFirst(boolean humanFirst) {
        this.humanFirst = humanFirst;
    }
}
