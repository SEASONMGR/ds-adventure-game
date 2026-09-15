package com.studio.plugin.demo.sokoban;

import com.studio.plugin.kit.Se;

import com.studio.plugin.GamePlugin;
import com.studio.ui.FxAnim;
import com.studio.util.Logs;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * 「推箱子」小游戏插件（需求清单里的 {@code sokoban}，剧情第 8 章使用）。
 *
 * <p>接入方式与 {@code snake} / {@code memory} 完全一致：地图里写
 * {@code [场景] event = sokoban}，或按钮节点 {@code action = event} + {@code event = sokoban}。</p>
 *
 * <p>规则全部在纯引擎 {@link SokobanGame} 里，关卡在 {@link SokobanConfig} 里，
 * 本类只负责输入、渲染与遮罩。</p>
 */
public final class SokobanPlugin implements GamePlugin {

    private static final double STEP_SECONDS = 1.0 / 60;
    private static final String WINDOW_TITLE = "推箱子  -  方向键/WASD 移动   Z 撤销   R 重开";

    // ---------- 配色：对齐剧情读取器（resources/styles/player.css）----------
    private static final Color FIELD_TOP = Color.web("#0a0c16");
    private static final Color FIELD_BOTTOM = Color.web("#05060d");
    private static final Color FLOOR_A = Color.web("#11162b");
    private static final Color FLOOR_B = Color.web("#141a31");
    private static final Color BOARD_STROKE = Color.web("#ffffff", 0.10);
    private static final Color WALL_FACE = Color.web("#2b3252");
    private static final Color WALL_TOP = Color.web("#3d466f");
    private static final Color WALL_EDGE = Color.web("#1b2038");
    private static final Color TARGET_RING = Color.web("#ffd76a", 0.75);
    private static final Color BOX_FACE = Color.web("#5a6285");
    private static final Color BOX_EDGE = Color.web("#8f98bd");
    private static final Color BOX_DONE_FACE = Color.web("#c9962e");
    private static final Color BOX_DONE_EDGE = Color.web("#ffd76a");
    private static final Color PLAYER_BODY = Color.web("#f2f3ff");
    private static final Color PLAYER_EDGE = Color.web("#ffd76a");
    private static final Color STATUS_TEXT = Color.web("#8a8fb0");
    private static final Color HEADER_BG = Color.web("#171826");
    private static final Color CAPTION_TEXT = Color.web("#d0d4e0");

    // ---------- 几何 ----------
    private static final double WALL_INSET = 3;
    private static final double WALL_ARC = 6;
    private static final double TARGET_RADIUS_RATIO = 0.16;
    private static final double BOX_INSET_RATIO = 0.10;
    private static final double BOX_ARC_RATIO = 0.16;
    private static final double PLAYER_RADIUS_RATIO = 0.30;
    private static final double CHECKER_SPLIT = 0.5;
    private static final double STATUS_BASELINE_OFFSET = 18;
    private static final int STATUS_FONT_SIZE = 15;

    // ---------- 遮罩 ----------
    private static final double OVERLAY_FADE_MS = 180;
    private static final double OVERLAY_SCALE_MS = 220;
    private static final double OVERLAY_ENTER_SCALE = 0.94;
    private static final double DIVIDER_WIDTH = 96;
    private static final double DIVIDER_THICKNESS = 1;
    private static final double HINT_PULSE_MS = 1100;
    private static final double HINT_PULSE_MIN = 0.45;

    private static final String UI_FONT = pickFamily(new String[]{
        "Microsoft YaHei UI", "PingFang SC", "Noto Sans CJK SC", "Microsoft YaHei", "SimHei",
    });
    private static final String FONT_STACK =
            "\"Microsoft YaHei UI\", \"PingFang SC\", \"Noto Sans CJK SC\", sans-serif";

    private static final String TITLE_STYLE =
            "-fx-font-family: " + FONT_STACK + ";"
            + "-fx-font-size: 36px; -fx-font-weight: bold; -fx-text-fill: #ffd76a;"
            + "-fx-effect: dropshadow(gaussian, rgba(255,215,106,0.45), 14, 0.30, 0, 0);";
    private static final String SUBTITLE_STYLE = "-fx-font-size: 13px; -fx-text-fill: #8a8fb0;";
    private static final String RULES_STYLE = "-fx-font-size: 14px; -fx-text-fill: #dfe0f2;";
    private static final String HINT_STYLE =
            "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #ffd76a;";
    private static final String DIVIDER_STYLE = "-fx-background-color: rgba(255,255,255,0.14);";
    private static final String PANEL_STYLE =
            "-fx-font-family: " + FONT_STACK + ";"
            + "-fx-background-color: rgba(14, 15, 30, 0.86);"
            + "-fx-background-radius: 18;"
            + "-fx-border-color: rgba(255, 255, 255, 0.10);"
            + "-fx-border-radius: 18;";

    /** 按钮：照搬 player.css 的 .story-btn。 */
    private static final String BUTTON_STYLE =
            "-fx-background-color: rgba(24, 26, 44, 0.68);"
            + "-fx-background-radius: 12; -fx-border-radius: 12;"
            + "-fx-border-color: rgba(255, 255, 255, 0.30); -fx-border-width: 1;"
            + "-fx-text-fill: #f2f3ff; -fx-font-size: 15px; -fx-font-weight: bold;"
            + "-fx-padding: 7 20 7 20; -fx-cursor: hand;";
    private static final String BUTTON_HOVER_STYLE =
            "-fx-background-color: rgba(255, 215, 106, 0.14);"
            + "-fx-background-radius: 12; -fx-border-radius: 12;"
            + "-fx-border-color: #ffd76a; -fx-border-width: 1;"
            + "-fx-text-fill: #ffd76a; -fx-font-size: 15px; -fx-font-weight: bold;"
            + "-fx-padding: 7 20 7 20; -fx-cursor: hand;";

    private final SokobanConfig config = new SokobanConfig();
    private final SokobanGame game = new SokobanGame();

    private Canvas canvas;
    private BorderPane root;
    private Label statsLabel;
    private VBox overlay;
    private Label overlayTitle;
    private Label overlaySubtitle;
    private Label overlayRules;
    private Label overlayHint;
    private Button startButton;
    private Button nextButton;
    private Button retryButton;
    private Button againButton;

    private AnimationTimer loop;
    private long lastNanos;
    private double accumulator;
    private boolean started;
    private String lastOverlayKey = "";
    private Runnable backCallback;
    private String pluginId = "sokoban";

    // =====================================================================
    // GamePlugin
    // =====================================================================

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        // 嵌入模式：不创建任何窗口，界面由 createEmbeddedView 提供（CONTRIBUTING §2.2）
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        readParams(params);
        game.reset(config);
        buildUi();
        startLoop();
        Platform.runLater(this::refocus);
        Logs.plugin(pluginId, "createEmbeddedView：构建嵌入界面（back.callback="
                + (backCallback != null) + "）");
        return root;
    }

    @Override
    public String displayName() {
        return "推箱子";
    }

    @Override
    public void onDetach() {
        stopLoop();
    }

    // =====================================================================
    // 装配
    // =====================================================================

    private void readParams(Map<String, Object> params) {
        if (params == null) {
            return;
        }
        Object id = params.get(GamePlugin.PARAM_PLUGIN_ID);
        if (id != null) {
            pluginId = String.valueOf(id);
        }
        Object back = params.get(GamePlugin.PARAM_BACK_CALLBACK);
        if (back instanceof Runnable runnable) {
            backCallback = runnable;
        }
    }

    private void buildUi() {
        canvas = new Canvas(config.getViewWidth(), config.getViewHeight());
        root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(new StackPane(canvas, buildOverlay()));
        // 画布比窗口窄时，四周用剧情底色兜住，避免露出浅色背景
        root.setStyle("-fx-background-color: " + toHex(FIELD_BOTTOM) + ";");
        root.setFocusTraversable(true);
        bindInput();
        updateStats();
        render();
    }

    private HBox buildHeader() {
        Label caption = new Label("推箱子");
        caption.setTextFill(CAPTION_TEXT);
        caption.setFont(Font.font(UI_FONT, FontWeight.BOLD, 16));
        // 标题永不压缩：宁可压缩右边的状态文字
        caption.setMinWidth(Region.USE_PREF_SIZE);

        statsLabel = new Label();
        statsLabel.setTextFill(STATUS_TEXT);
        statsLabel.setFont(Font.font(UI_FONT, 14));
        // 状态文字是唯一可以牺牲的：窗口窄时让它先缩
        statsLabel.setMinWidth(0);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button restart = styledButton("↻ 重玩本关");
        restart.setOnAction(e -> restart());

        HBox header = new HBox(12, caption, statsLabel, spacer, restart);
        if (backCallback != null) {
            Button back = styledButton("← 返回剧情");
            back.setOnAction(e -> leaveToStory());
            header.getChildren().add(back);
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 16, 10, 16));
        header.setStyle("-fx-background-color: " + toHex(HEADER_BG) + ";");
        return header;
    }

    private VBox buildOverlay() {
        overlayTitle = new Label();
        overlayTitle.setStyle(TITLE_STYLE);

        overlaySubtitle = new Label();
        overlaySubtitle.setStyle(SUBTITLE_STYLE);

        overlayRules = new Label();
        overlayRules.setStyle(RULES_STYLE);
        overlayRules.setTextAlignment(TextAlignment.CENTER);

        startButton = styledButton("开始游戏");
        startButton.setOnAction(e -> startGame());
        nextButton = styledButton("下一关");
        nextButton.setOnAction(e -> goNextLevel());
        retryButton = styledButton("重玩本关");
        retryButton.setOnAction(e -> restart());
        againButton = styledButton("从头再来");
        againButton.setOnAction(e -> restartFromFirstLevel());

        HBox row = new HBox(14, startButton, nextButton, retryButton, againButton);
        row.setAlignment(Pos.CENTER);

        HBox divider = new HBox(10, dividerLine(), dividerLine());
        divider.setAlignment(Pos.CENTER);

        overlayHint = new Label();
        overlayHint.setStyle(HINT_STYLE);
        playHintPulse();

        overlay = new VBox(13, overlayTitle, overlaySubtitle, divider, overlayRules, row, overlayHint);
        overlay.setAlignment(Pos.CENTER);
        overlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        overlay.setPadding(new Insets(30, 48, 28, 48));
        overlay.setStyle(PANEL_STYLE);
        VBox.setMargin(row, new Insets(6, 0, 2, 0));
        return overlay;
    }

    private static Region dividerLine() {
        Region line = new Region();
        line.setPrefSize(DIVIDER_WIDTH, DIVIDER_THICKNESS);
        line.setStyle(DIVIDER_STYLE);
        return line;
    }

    private void playHintPulse() {
        FadeTransition pulse = new FadeTransition(Duration.millis(HINT_PULSE_MS), overlayHint);
        pulse.setFromValue(1);
        pulse.setToValue(HINT_PULSE_MIN);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    /**
     * 统一按钮外观。按钮必须 {@code setFocusTraversable(false)} —— 否则方向键与空格
     * 会被按钮吃掉，游戏里就动不了。
     */
    private static Button styledButton(String text) {
        Button button = new Button(text);
        button.setFocusTraversable(false);
        // 不被 HBox 压窄 —— 压窄了 JavaFX 会把文字截成「…」
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setStyle(BUTTON_STYLE);
        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> button.setStyle(BUTTON_HOVER_STYLE));
        button.addEventHandler(MouseEvent.MOUSE_EXITED, e -> button.setStyle(BUTTON_STYLE));
        FxAnim.makeInteractive(button);
        return button;
    }

    // =====================================================================
    // 输入
    // =====================================================================

    private void bindInput() {
        canvas.setOnMouseClicked(e -> {
            if (!started) {
                startGame();
            }
        });
        root.setOnKeyPressed(event -> onKey(event.getCode()));
    }

    private void onKey(KeyCode code) {
        if (game.isWin()) {
            // 过关界面：空格 = 下一关（已是最后一关则从头再来）
            if (code == KeyCode.SPACE) {
                if (game.hasNextLevel()) {
                    goNextLevel();
                } else {
                    restartFromFirstLevel();
                }
            }
            return;
        }
        if (!started) {
            if (code == KeyCode.SPACE) {
                startGame();
            }
            return;
        }
        switch (code) {
            case UP, W -> doMove(0, -1);
            case DOWN, S -> doMove(0, 1);
            case LEFT, A -> doMove(-1, 0);
            case RIGHT, D -> doMove(1, 0);
            case Z -> doUndo();
            case R -> restart();
            default -> { }
        }
    }

    private void doMove(int dx, int dy) {
        if (game.move(dx, dy)) {
            Se.play("se_box_push");
            updateStats();
            render();
        }
    }

    private void doUndo() {
        if (game.undo()) {
            Se.play("se_hint");
            updateStats();
            render();
        }
    }

    // =====================================================================
    // 流程
    // =====================================================================

    private boolean isWaitingForStart() {
        return !started || game.isWin();
    }

    private void startGame() {
        started = true;
        accumulator = 0;
        lastNanos = System.nanoTime();
        updateStats();
        render();
        refocus();
    }

    private void restart() {
        game.loadLevel(config.getCurrentLevel());
        started = true;
        accumulator = 0;
        lastNanos = System.nanoTime();
        updateStats();
        render();
        refocus();
    }

    private void goNextLevel() {
        if (game.nextLevel()) {
            started = true;
            accumulator = 0;
            lastNanos = System.nanoTime();
            updateStats();
            render();
            refocus();
        }
    }

    private void restartFromFirstLevel() {
        config.setLevelIndex(0);
        restart();
    }

    private void refocus() {
        if (root != null) {
            root.requestFocus();
        }
    }

    /** 结束本局并返回剧情：先停循环收尾，再调引擎回调（CONTRIBUTING §2.6 第 4 条）。 */
    private void leaveToStory() {
        stopLoop();
        if (backCallback != null) {
            backCallback.run();
        }
    }

    // =====================================================================
    // 主循环
    // =====================================================================

    private void startLoop() {
        if (loop != null) {
            return;
        }
        lastNanos = System.nanoTime();
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.25);
                lastNanos = now;
                advance(dt);
            }
        };
        loop.start();
    }

    private void stopLoop() {
        if (loop != null) {
            loop.stop();
            loop = null;
            Logs.plugin(pluginId, "onDetach：游戏循环已停止");
        }
    }

    private void advance(double dt) {
        accumulator += dt;
        while (accumulator >= STEP_SECONDS) {
            if (started && !game.isWin()) {
                game.tick(STEP_SECONDS);
            }
            accumulator -= STEP_SECONDS;
        }
        updateStats();
        render();
    }

    private void updateStats() {
        if (statsLabel == null) {
            return;
        }
        int total = (int) game.getElapsed();
        // 用半角空格而非全角，宽度省一半，窄窗口下也不容易被截断
        statsLabel.setText(String.format("%d/%d关 · 箱%d/%d · %d步 · %02d:%02d",
                game.getLevelIndex() + 1, game.getLevelCount(),
                game.boxesOnTarget(), game.getBoxCount(),
                game.getMoveCount(), total / 60, total % 60));
    }

    // =====================================================================
    // 渲染
    // =====================================================================

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        drawField(g);
        drawBoard(g);
        drawStatus(g);
        drawOverlayVeil(g);
        refreshOverlay();
    }

    private void drawField(GraphicsContext g) {
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, FIELD_TOP), new Stop(1, FIELD_BOTTOM)));
        g.fillRect(0, 0, config.getViewWidth(), config.getViewHeight());
    }

    private void drawBoard(GraphicsContext g) {
        double size = config.getCellSize();
        int cols = game.getWidth();
        int rows = game.getHeight();
        g.setStroke(BOARD_STROKE);
        g.setLineWidth(1);
        g.strokeRoundRect(config.getOffsetX(cols) - 8, config.getOffsetY(rows) - 8,
                cols * size + 16, rows * size + 16, 12, 12);

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double px = config.getPixelX(x, cols);
                double py = config.getPixelY(y, rows);
                if (game.isWall(x, y)) {
                    drawWall(g, px, py, size);
                    continue;
                }
                g.setFill((x + y) % 2 == 0 ? FLOOR_A : FLOOR_B);
                g.fillRect(px, py, size, size);
                if (game.isTarget(x, y)) {
                    drawTarget(g, px, py, size);
                }
                if (game.hasBox(x, y)) {
                    drawBox(g, px, py, size, game.isTarget(x, y));
                }
                if (game.isPlayer(x, y)) {
                    drawPlayer(g, px, py, size);
                }
            }
        }
    }

    private void drawWall(GraphicsContext g, double px, double py, double size) {
        g.setFill(WALL_EDGE);
        g.fillRoundRect(px + WALL_INSET, py + WALL_INSET + 3, size - WALL_INSET * 2,
                size - WALL_INSET * 2, WALL_ARC, WALL_ARC);
        g.setFill(WALL_FACE);
        g.fillRoundRect(px + WALL_INSET, py + WALL_INSET, size - WALL_INSET * 2,
                size - WALL_INSET * 2, WALL_ARC, WALL_ARC);
        g.setFill(WALL_TOP);
        g.fillRoundRect(px + WALL_INSET, py + WALL_INSET, size - WALL_INSET * 2,
                (size - WALL_INSET * 2) * CHECKER_SPLIT * 0.5, WALL_ARC, WALL_ARC);
    }

    private void drawTarget(GraphicsContext g, double px, double py, double size) {
        double radius = size * TARGET_RADIUS_RATIO;
        g.setStroke(TARGET_RING);
        g.setLineWidth(2.5);
        g.strokeOval(px + size / 2 - radius, py + size / 2 - radius, radius * 2, radius * 2);
        g.setLineWidth(1);
    }

    private void drawBox(GraphicsContext g, double px, double py, double size, boolean done) {
        double inset = size * BOX_INSET_RATIO;
        double arc = size * BOX_ARC_RATIO;
        g.setFill(done ? BOX_DONE_FACE : BOX_FACE);
        g.fillRoundRect(px + inset, py + inset, size - inset * 2, size - inset * 2, arc, arc);
        g.setStroke(done ? BOX_DONE_EDGE : BOX_EDGE);
        g.setLineWidth(2);
        g.strokeRoundRect(px + inset, py + inset, size - inset * 2, size - inset * 2, arc, arc);
        double half = (size - inset * 2) * 0.22;
        g.setLineWidth(1.5);
        g.strokeLine(px + size / 2 - half, py + size / 2, px + size / 2 + half, py + size / 2);
        g.strokeLine(px + size / 2, py + size / 2 - half, px + size / 2, py + size / 2 + half);
        g.setLineWidth(1);
    }

    private void drawPlayer(GraphicsContext g, double px, double py, double size) {
        double radius = size * PLAYER_RADIUS_RATIO;
        g.setFill(PLAYER_BODY);
        g.fillOval(px + size / 2 - radius, py + size / 2 - radius, radius * 2, radius * 2);
        g.setStroke(PLAYER_EDGE);
        g.setLineWidth(2);
        g.strokeOval(px + size / 2 - radius, py + size / 2 - radius, radius * 2, radius * 2);
        g.setLineWidth(1);
    }

    private void drawStatus(GraphicsContext g) {
        if (isWaitingForStart()) {
            return;
        }
        g.setFont(Font.font(UI_FONT, FontWeight.BOLD, STATUS_FONT_SIZE));
        g.setFill(STATUS_TEXT);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(game.canUndo() ? "方向键 / WASD 移动　·　Z 撤销　·　R 重玩本关"
                        : "方向键 / WASD 移动　·　R 重玩本关",
                config.getViewWidth() / 2, config.getViewHeight() - STATUS_BASELINE_OFFSET);
        g.setTextAlign(TextAlignment.LEFT);
    }

    // =====================================================================
    // 遮罩
    // =====================================================================

    private String overlayKey() {
        if (game.isWin()) {
            return game.hasNextLevel() ? "levelWin" : "allWin";
        }
        return started ? "" : "start";
    }

    private void drawOverlayVeil(GraphicsContext g) {
        if (overlayKey().isEmpty()) {
            return;
        }
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#000000", 0.24)),
                new Stop(0.30, Color.web("#000000", 0.62)),
                new Stop(0.70, Color.web("#000000", 0.62)),
                new Stop(1.00, Color.web("#000000", 0.24))));
        g.fillRect(0, 0, config.getViewWidth(), config.getViewHeight());
    }

    /** 遮罩三态：开始 / 过关 / 全部通关。 */
    private void refreshOverlay() {
        String key = overlayKey();
        if (key.isEmpty()) {
            hideOverlay();
            return;
        }
        switch (key) {
            case "levelWin" -> {
                overlayTitle.setText("过关！");
                overlaySubtitle.setText(resultLine());
                overlayRules.setText("箱子全部归位 —— 下一关更要动脑子");
                overlayHint.setText("按 空格 进入下一关");
            }
            case "allWin" -> {
                overlayTitle.setText("全部通关！");
                overlaySubtitle.setText(resultLine());
                overlayRules.setText("三关全部推完，仓库清空");
                overlayHint.setText("按 空格 从头再来");
            }
            default -> {
                overlayTitle.setText("推箱子");
                overlaySubtitle.setText("仓库管理员的第一课");
                overlayRules.setText("把箱子推到目标点　·　人能推不能拉");
                overlayHint.setText("按 空格 开始游戏");
            }
        }
        setButtonMode(key);
        boolean changed = !key.equals(lastOverlayKey);
        lastOverlayKey = key;
        showOverlay(changed);
    }

    private void setButtonMode(String key) {
        boolean start = "start".equals(key);
        boolean levelWin = "levelWin".equals(key);
        setVisible(startButton, start);
        setVisible(nextButton, levelWin);
        setVisible(retryButton, levelWin);
        setVisible(againButton, "allWin".equals(key));
    }

    private static void setVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    /** 结算行：第几关、多少步、推了多少下、用时。 */
    private String resultLine() {
        int total = (int) game.getElapsed();
        return "第 " + (game.getLevelIndex() + 1) + " 关　·　步数 " + game.getMoveCount()
                + "　·　推动 " + game.getPushCount() + "　·　用时 "
                + String.format("%02d:%02d", total / 60, total % 60);
    }

    private void showOverlay(boolean animate) {
        overlay.setVisible(true);
        overlay.setManaged(true);
        if (!animate) {
            return;
        }
        overlay.setOpacity(0);
        overlay.setScaleX(OVERLAY_ENTER_SCALE);
        overlay.setScaleY(OVERLAY_ENTER_SCALE);
        FadeTransition fade = new FadeTransition(Duration.millis(OVERLAY_FADE_MS), overlay);
        fade.setToValue(1);
        ScaleTransition scale = new ScaleTransition(Duration.millis(OVERLAY_SCALE_MS), overlay);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, scale).play();
    }

    private void hideOverlay() {
        if (overlay != null && overlay.isVisible()) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
        lastOverlayKey = "";
    }

    /** 按候选顺序挑一个系统里存在的字体，都没有就退回默认字体。 */
    private static String pickFamily(String[] candidates) {
        List<String> families = Font.getFamilies();
        for (String candidate : candidates) {
            if (families.contains(candidate)) {
                return candidate;
            }
        }
        return Font.getDefault().getFamily();
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
