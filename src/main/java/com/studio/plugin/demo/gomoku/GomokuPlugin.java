package com.studio.plugin.demo.gomoku;

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
import javafx.scene.Scene;
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
 * 「五子棋」小游戏插件（需求清单里的 {@code gomoku}，剧情第 9 章使用）。
 *
 * <p>接入方式与 {@code snake} / {@code memory} 完全一致：地图里写
 * {@code [场景] event = gomoku}，或按钮节点 {@code action = event} + {@code event = gomoku}。</p>
 *
 * <p>规则全部在纯引擎 {@link GomokuGame} 里，AI 在 {@link GomokuAi} 里，
 * 本类只负责界面渲染、鼠标/键盘输入，以及 AI 回合的节奏。</p>
 */
public final class GomokuPlugin implements GamePlugin {

    /** 固定步长：60 帧/秒。 */
    private static final double STEP_SECONDS = 1.0 / 60;
    private static final String WINDOW_TITLE = "五子棋  -  鼠标左键落子   空格 开始 / R 重开";

    // ---------- 配色：对齐剧情读取器（resources/styles/player.css）----------
    // 深蓝黑底 + 金色强调 #ffd76a + 白描边，和剧情界面同一套色，嵌进去不生分。
    private static final Color FIELD_TOP = Color.web("#0a0c16");
    private static final Color FIELD_BOTTOM = Color.web("#05060d");
    private static final Color BOARD_BG = Color.web("#4a5375");
    private static final Color BOARD_STROKE = Color.web("#ffffff", 0.20);
    private static final Color GRID_COLOR = Color.web("#ffffff", 0.32);
    private static final Color STAR_COLOR = Color.web("#ffd76a", 0.90);
    private static final Color BLACK_STONE = Color.web("#141726");
    private static final Color BLACK_STONE_RIM = Color.web("#8b93b8");
    private static final Color WHITE_STONE = Color.web("#f7f8ff");
    private static final Color WHITE_STONE_RIM = Color.web("#4c5478");
    private static final Color STONE_SHADOW = Color.web("#000000", 0.50);
    private static final Color LAST_MARK = Color.web("#ffd76a");
    private static final Color WIN_GLOW = Color.web("#ffd76a");
    private static final Color HOVER_STONE = Color.web("#ffffff", 0.22);
    private static final Color STATUS_TEXT = Color.web("#8a8fb0");
    private static final Color HEADER_BG = Color.web("#171826");

    // ---------------- 几何与字号 ----------------
    private static final double BOARD_PAD = 14;
    private static final double BOARD_ARC = 10;
    private static final double STAR_RADIUS = 3;
    private static final double LAST_MARK_RADIUS = 4;
    private static final double WIN_LINE_WIDTH = 4;
    private static final double STATUS_BASELINE_OFFSET = 18;
    private static final int TITLE_FONT_SIZE = 38;
    private static final int HINT_FONT_SIZE = 19;
    private static final int STATUS_FONT_SIZE = 15;
    private static final double OVERLAY_FADE_MS = 180;
    private static final double OVERLAY_SCALE_MS = 220;
    private static final double OVERLAY_ENTER_SCALE = 0.94;
    private static final double DIVIDER_WIDTH = 96;
    private static final double DIVIDER_THICKNESS = 1;
    private static final String DIVIDER_STYLE = "-fx-background-color: rgba(255,255,255,0.14);";
    private static final double HINT_PULSE_MS = 1100;
    private static final double HINT_PULSE_MIN = 0.45;

    /**
     * 界面字体：与剧情读取器一致（player.css 的 .player-root 字体栈）。
     *
     * <p>不用「飞机大战」那套粗黑显示体，也不用书法体 —— 跟着剧情界面的无衬线走，
     * 嵌进剧情里才不会像贴上去的外来物。</p>
     */
    private static final String UI_FONT = pickFamily(new String[]{
        "Microsoft YaHei UI", "PingFang SC", "Noto Sans CJK SC", "Microsoft YaHei", "SimHei",
    });
    /** 剧情读取器的完整字体栈，面板用。 */
    private static final String FONT_STACK =
            "\"Microsoft YaHei UI\", \"PingFang SC\", \"Noto Sans CJK SC\", sans-serif";

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

    /** 按钮：完全照搬 player.css 的 .story-btn。 */
    private static final String BUTTON_STYLE =
            "-fx-background-color: rgba(24, 26, 44, 0.68);"
            + "-fx-background-radius: 12; -fx-border-radius: 12;"
            + "-fx-border-color: rgba(255, 255, 255, 0.30); -fx-border-width: 1;"
            + "-fx-text-fill: #f2f3ff; -fx-font-size: 15px; -fx-font-weight: bold;"
            + "-fx-padding: 7 20 7 20; -fx-cursor: hand;";

    /** .story-btn:hover —— 描边与文字都转成剧情金。 */
    private static final String BUTTON_HOVER_STYLE =
            "-fx-background-color: rgba(255, 215, 106, 0.14);"
            + "-fx-background-radius: 12; -fx-border-radius: 12;"
            + "-fx-border-color: #ffd76a; -fx-border-width: 1;"
            + "-fx-text-fill: #ffd76a; -fx-font-size: 15px; -fx-font-weight: bold;"
            + "-fx-padding: 7 20 7 20; -fx-cursor: hand;";

    private static final String TITLE_STYLE = titleStyle("#ffd76a", "rgba(255,215,106,0.45)");
    private static final String TITLE_STYLE_WIN = titleStyle("#ffe2a6", "rgba(255,226,166,0.45)");
    private static final String TITLE_STYLE_LOSE = titleStyle("#c3c7dd", "rgba(195,199,221,0.35)");
    private static final String TITLE_STYLE_DRAW = titleStyle("#dfe0f2", "rgba(223,224,242,0.35)");

    private static final String HINT_STYLE =
            "-fx-font-size: " + HINT_FONT_SIZE + "px; -fx-font-weight: bold; -fx-text-fill: #ffd76a;";
    private static final String SUBTITLE_STYLE = "-fx-font-size: 13px; -fx-text-fill: #8a8fb0;";
    private static final String RULES_STYLE = "-fx-font-size: 14px; -fx-text-fill: #dfe0f2;";
    /** 遮罩面板：对齐 player.css 的 .dialog-panel（半透明深底 + 18 圆角 + 极淡描边）。 */
    private static final String PANEL_STYLE =
            "-fx-font-family: " + FONT_STACK + ";"
            + "-fx-background-color: rgba(14, 15, 30, 0.86);"
            + "-fx-background-radius: 18;"
            + "-fx-border-color: rgba(255, 255, 255, 0.10);"
            + "-fx-border-radius: 18;";

    // ---------------- 状态 ----------------
    private Stage stage;
    private final GomokuConfig config = new GomokuConfig();
    private final GomokuGame game = new GomokuGame();

    private Canvas canvas;
    private BorderPane root;
    private Label clockLabel;
    private VBox overlay;
    private Label overlayTitle;
    private Label overlayHint;
    private Label overlaySubtitle;
    private Label overlayRules;
    private HBox divider;
    private HBox overlayButtons;
    private Button blackButton;
    private Button whiteButton;
    private Button againButton;
    private Button switchButton;

    private AnimationTimer loop;
    private long lastNanos;
    private double accumulator;
    private double animTime;
    /** AI 还要"思考"多久才落子（秒）。 */
    private double aiCountdown;
    private int hoverX = -1;
    private int hoverY = -1;
    private boolean started;
    private String lastOverlayKey = "";
    private Runnable backCallback;
    private String pluginId = "gomoku";

    // =====================================================================
    // GamePlugin
    // =====================================================================

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        this.stage = stage;
        // 嵌入模式：不创建任何窗口，界面由 createEmbeddedView 提供（CONTRIBUTING §2.2）
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        readParams(params);
        game.reset(config);
        aiCountdown = config.getAiThinkDelay();
        buildUi();
        startLoop();
        Platform.runLater(this::refocus);
        Logs.plugin(pluginId, "createEmbeddedView：构建嵌入界面（back.callback="
                + (backCallback != null) + "）");
        return root;
    }

    @Override
    public String displayName() {
        return "五子棋";
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
        root.setFocusTraversable(true);
        bindInput();
        render();
    }

    private HBox buildHeader() {
        Label caption = new Label("五子棋");
        caption.setTextFill(Color.web("#d0d4e0"));
        caption.setFont(Font.font(UI_FONT, FontWeight.BOLD, 16));
        caption.setMinWidth(Region.USE_PREF_SIZE); // 标题永不压缩

        clockLabel = new Label();
        clockLabel.setTextFill(STATUS_TEXT);
        clockLabel.setFont(Font.font(UI_FONT, 14));
        clockLabel.setMinWidth(0); // 窄窗口时由它先让位

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, caption, clockLabel, spacer, styledButton("↻ 重新开局"));
        ((Button) header.getChildren().get(3)).setOnAction(e -> restart());
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

        divider = new HBox(10, dividerLine(), dividerLine());
        divider.setAlignment(Pos.CENTER);

        overlayRules = new Label();
        overlayRules.setStyle(RULES_STYLE);
        overlayRules.setTextAlignment(TextAlignment.CENTER);

        overlayHint = new Label();
        overlayHint.setStyle(HINT_STYLE);
        playHintPulse();

        blackButton = styledButton("执黑先行");
        blackButton.setOnAction(e -> startGame(true));
        whiteButton = styledButton("执白后行");
        whiteButton.setOnAction(e -> startGame(false));
        againButton = styledButton("再来一局");
        againButton.setOnAction(e -> restart());
        switchButton = styledButton("换边再战");
        switchButton.setOnAction(e -> backToChoice());

        overlayButtons = new HBox(12, blackButton, whiteButton, againButton, switchButton);
        overlayButtons.setAlignment(Pos.CENTER);

        overlay = new VBox(13, overlayTitle, overlaySubtitle, divider, overlayRules,
                overlayButtons, overlayHint);
        overlay.setAlignment(Pos.CENTER);
        overlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        overlay.setPadding(new Insets(30, 48, 28, 48));
        overlay.setStyle(PANEL_STYLE);
        VBox.setMargin(overlayButtons, new Insets(6, 0, 2, 0));
        return overlay;
    }

    private static Region dividerLine() {
        Region line = new Region();
        line.setPrefSize(DIVIDER_WIDTH, DIVIDER_THICKNESS);
        line.setStyle(DIVIDER_STYLE);
        return line;
    }

    /** 提示文字轻微呼吸，把注意力引到"按空格"上。 */
    private void playHintPulse() {
        FadeTransition pulse = new FadeTransition(Duration.millis(HINT_PULSE_MS), overlayHint);
        pulse.setFromValue(1);
        pulse.setToValue(HINT_PULSE_MIN);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    /**
     * 统一按钮外观：渐变底 + 圆角描边 + 粗体；悬停提亮，并叠加 FxAnim 的缩放动效。
     *
     * <p><b>关键</b>：按钮必须 {@code setFocusTraversable(false)}。JavaFX 里按钮一旦获得焦点，
     * 空格会去触发这个按钮而不是游戏逻辑。</p>
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
        canvas.setOnMouseMoved(e -> {
            hoverX = config.toBoardX(e.getX());
            hoverY = config.toBoardY(e.getY());
            render();
        });
        canvas.setOnMouseExited(e -> {
            hoverX = -1;
            hoverY = -1;
            render();
        });
        canvas.setOnMouseClicked(e -> onBoardClick(e.getX(), e.getY()));
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                if (game.isOver()) {
                    restart();
                } else if (!started) {
                    startGame(true); // 空格 = 默认执黑先行
                }
            } else if (event.getCode() == KeyCode.R && started) {
                restart();
            }
        });
    }

    private void onBoardClick(double pixelX, double pixelY) {
        if (isWaitingForStart()) {
            return; // 选边请点下方按钮（或按空格默认执黑）
        }
        if (!game.isHumanTurn()) {
            return;
        }
        int x = config.toBoardX(pixelX);
        int y = config.toBoardY(pixelY);
        if (x < 0 || y < 0 || !game.place(x, y)) {
            return;
        }
        aiCountdown = config.getAiThinkDelay();
        refocus();
        render();
    }

    // =====================================================================
    // 流程
    // =====================================================================

    private boolean isWaitingForStart() {
        return !started || game.isOver();
    }

    /**
     * 选边开局。
     *
     * @param humanFirst true = 玩家执黑先行，false = 玩家执白后行
     */
    private void startGame(boolean humanFirst) {
        config.setHumanFirst(humanFirst);
        beginRound();
    }

    /** 同色再来一局。 */
    private void restart() {
        beginRound();
    }

    /** 回到选边界面（换边再战）。 */
    private void backToChoice() {
        started = false;
        game.reset(config);
        hoverX = -1;
        hoverY = -1;
        updateClock();
        render();
        refocus();
    }

    /** 用当前选定的颜色开一局。 */
    private void beginRound() {
        game.reset(config);
        started = true;
        aiCountdown = config.getAiThinkDelay();
        accumulator = 0;
        lastNanos = System.nanoTime();
        hoverX = -1;
        hoverY = -1;
        updateClock();
        render();
        refocus();
    }

    /** 把键盘焦点还给根节点：避免按键被某个控件吃掉。 */
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
                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                advance(Math.min(dt, 0.25));
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

    /** 按固定步长推进：计时、AI 回合、界面动效。 */
    private void advance(double dt) {
        accumulator += dt;
        while (accumulator >= STEP_SECONDS) {
            animTime += STEP_SECONDS;
            if (started && !game.isOver()) {
                game.tick(STEP_SECONDS);
                updateAiTurn(STEP_SECONDS);
            }
            accumulator -= STEP_SECONDS;
        }
        updateClock();
        render();
    }

    /** AI 的"思考"倒计时，归零就落子。 */
    private void updateAiTurn(double dt) {
        if (!game.isAiTurn()) {
            return;
        }
        aiCountdown -= dt;
        if (aiCountdown > 0) {
            return;
        }
        int[] move = GomokuAi.chooseMove(game.snapshot(), game.aiColor(), config.getWinLength());
        if (move != null) {
            game.place(move[0], move[1]);
            Se.play("se_stone_place");
        }
        aiCountdown = config.getAiThinkDelay();
    }

    private void updateClock() {
        if (clockLabel == null) {
            return;
        }
        int total = (int) game.elapsed();
        clockLabel.setText(String.format("手数%d · %02d:%02d",
                game.moveCount(), total / 60, total % 60));
    }

    // =====================================================================
    // 渲染
    // =====================================================================

    private void render() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        drawField(g);
        drawBoard(g);
        drawStones(g);
        drawWinLine(g);
        drawHover(g);
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
        double margin = config.getMargin();
        double span = config.getCellSize() * (config.getBoardSize() - 1);
        g.setFill(BOARD_BG);
        g.fillRoundRect(margin - BOARD_PAD, margin - BOARD_PAD, span + BOARD_PAD * 2,
                span + BOARD_PAD * 2, BOARD_ARC, BOARD_ARC);
        g.setStroke(BOARD_STROKE);
        g.setLineWidth(1);
        g.strokeRoundRect(margin - BOARD_PAD, margin - BOARD_PAD, span + BOARD_PAD * 2,
                span + BOARD_PAD * 2, BOARD_ARC, BOARD_ARC);

        g.setStroke(GRID_COLOR);
        g.setLineWidth(1);
        for (int i = 0; i < config.getBoardSize(); i++) {
            double px = config.getPixelX(i);
            double py = config.getPixelY(i);
            g.strokeLine(px, margin, px, margin + span);
            g.strokeLine(margin, py, margin + span, py);
        }
        drawStarPoints(g);
    }

    /** 传统五子棋的星位（含天元）。 */
    private void drawStarPoints(GraphicsContext g) {
        int last = config.getBoardSize() - 1;
        int mid = last / 2;
        int edge = config.getBoardSize() >= 15 ? 3 : 2;
        int[][] points = {
            {edge, edge}, {last - edge, edge}, {mid, mid},
            {edge, last - edge}, {last - edge, last - edge},
        };
        g.setFill(STAR_COLOR);
        for (int[] point : points) {
            g.fillOval(config.getPixelX(point[0]) - STAR_RADIUS,
                    config.getPixelY(point[1]) - STAR_RADIUS,
                    STAR_RADIUS * 2, STAR_RADIUS * 2);
        }
    }

    private void drawStones(GraphicsContext g) {
        double radius = config.getStoneRadius();
        for (int x = 0; x < config.getBoardSize(); x++) {
            for (int y = 0; y < config.getBoardSize(); y++) {
                int color = game.at(x, y);
                if (color != GomokuGame.EMPTY) {
                    drawStone(g, config.getPixelX(x), config.getPixelY(y), radius,
                            color == GomokuGame.BLACK);
                }
            }
        }
        drawLastMark(g);
    }

    /** 最后一手用红点标出来，方便看清对手走在哪里。 */
    private void drawLastMark(GraphicsContext g) {
        int x = game.lastX();
        int y = game.lastY();
        if (x < 0 || y < 0 || game.at(x, y) == GomokuGame.EMPTY) {
            return;
        }
        g.setFill(LAST_MARK);
        g.fillOval(config.getPixelX(x) - LAST_MARK_RADIUS, config.getPixelY(y) - LAST_MARK_RADIUS,
                LAST_MARK_RADIUS * 2, LAST_MARK_RADIUS * 2);
    }

    private void drawStone(GraphicsContext g, double cx, double cy, double radius, boolean black) {
        g.setFill(STONE_SHADOW);
        g.fillOval(cx - radius + 1.5, cy - radius + 2.5, radius * 2, radius * 2);
        g.setFill(black ? BLACK_STONE : WHITE_STONE);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setStroke(black ? BLACK_STONE_RIM : WHITE_STONE_RIM);
        g.setLineWidth(1);
        g.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawWinLine(GraphicsContext g) {
        List<int[]> line = game.winLine();
        if (line.size() < 2) {
            return;
        }
        int[] first = line.get(0);
        int[] last = line.get(line.size() - 1);
        g.setStroke(WIN_GLOW);
        g.setLineWidth(WIN_LINE_WIDTH);
        g.strokeLine(config.getPixelX(first[0]), config.getPixelY(first[1]),
                config.getPixelX(last[0]), config.getPixelY(last[1]));
        g.setLineWidth(1);
    }

    private void drawHover(GraphicsContext g) {
        if (!game.isHumanTurn() || hoverX < 0 || hoverY < 0 || !game.isLegal(hoverX, hoverY)) {
            return;
        }
        double radius = config.getStoneRadius();
        g.setFill(HOVER_STONE);
        g.fillOval(config.getPixelX(hoverX) - radius, config.getPixelY(hoverY) - radius,
                radius * 2, radius * 2);
    }

    private void drawStatus(GraphicsContext g) {
        if (isWaitingForStart()) {
            return;
        }
        g.setFont(Font.font(UI_FONT, FontWeight.BOLD, STATUS_FONT_SIZE));
        g.setFill(game.isOver() ? WIN_GLOW : STATUS_TEXT);
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(statusText(), config.getViewWidth() / 2,
                config.getViewHeight() - STATUS_BASELINE_OFFSET);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private String statusText() {
        if (game.isWin()) {
            return "五子连珠 —— 你赢了";
        }
        if (game.isLose()) {
            return "对手连成五子 —— 再战一局";
        }
        if (game.isDraw()) {
            return "棋盘下满 —— 和棋";
        }
        return game.isAiTurn() ? "对手思考中…" : "轮到你落子（鼠标左键）";
    }

    // =====================================================================
    // 遮罩
    // =====================================================================

    private String overlayKey() {
        if (game.isWin()) {
            return "win";
        }
        if (game.isLose()) {
            return "lose";
        }
        if (game.isDraw()) {
            return "draw";
        }
        return started ? "" : "start";
    }

    private void drawOverlayVeil(GraphicsContext g) {
        if (overlayKey().isEmpty()) {
            return;
        }
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#000000", 0.20)),
                new Stop(0.30, Color.web("#000000", 0.66)),
                new Stop(0.70, Color.web("#000000", 0.66)),
                new Stop(1.00, Color.web("#000000", 0.20))));
        g.fillRect(0, 0, config.getViewWidth(), config.getViewHeight());
    }

    /** 遮罩四态：开始 / 获胜 / 失败 / 和棋。 */
    private void refreshOverlay() {
        String key = overlayKey();
        if (key.isEmpty()) {
            hideOverlay();
            return;
        }
        switch (key) {
            case "start" -> {
                overlayTitle.setText("五子棋");
                overlayTitle.setStyle(TITLE_STYLE);
                overlayRules.setText("15 × 15 棋盘　·　先连成五子者获胜");
                overlayHint.setText("按 空格 默认执黑先行");
                setSubtitleVisible(false);
                setChoosingSide(true);
            }
            case "win" -> {
                overlayTitle.setText("五子连珠");
                overlayTitle.setStyle(TITLE_STYLE_WIN);
                overlaySubtitle.setText(resultLine());
                overlayRules.setText("你连成五子，赢了这一局");
                overlayHint.setText(settleHint());
                setSubtitleVisible(true);
                setChoosingSide(false);
            }
            case "draw" -> {
                overlayTitle.setText("和棋");
                overlayTitle.setStyle(TITLE_STYLE_DRAW);
                overlaySubtitle.setText(resultLine());
                overlayRules.setText("棋盘下满，无人成五");
                overlayHint.setText(settleHint());
                setSubtitleVisible(true);
                setChoosingSide(false);
            }
            default -> {
                overlayTitle.setText("惜败");
                overlayTitle.setStyle(TITLE_STYLE_LOSE);
                overlaySubtitle.setText(resultLine());
                overlayRules.setText("对手先连成了五子");
                overlayHint.setText(settleHint());
                setSubtitleVisible(true);
                setChoosingSide(false);
            }
        }
        boolean changed = !key.equals(lastOverlayKey);
        lastOverlayKey = key;
        showOverlay(changed);
    }

    /** 副标题只在结算界面出现（战绩行）。 */
    private void setSubtitleVisible(boolean visible) {
        overlaySubtitle.setVisible(visible);
        overlaySubtitle.setManaged(visible);
    }

    /** start 态显示「执黑 / 执白」，其它态显示「再来一局 / 换边再战」。 */
    private void setChoosingSide(boolean choosing) {
        setVisible(blackButton, choosing);
        setVisible(whiteButton, choosing);
        setVisible(againButton, !choosing);
        setVisible(switchButton, !choosing);
    }

    private static void setVisible(Button button, boolean visible) {
        button.setVisible(visible);
        button.setManaged(visible);
    }

    /** 结算界面的提示：说明这局玩家执什么颜色。 */
    private String settleHint() {
        return "按 空格 用" + colorName() + "棋再来一局";
    }

    /** 结算信息：执什么颜色、多少手、用时。 */
    private String resultLine() {
        int total = (int) game.elapsed();
        return "执" + colorName() + "　·　共 " + game.moveCount() + " 手　·　用时 "
                + String.format("%02d:%02d", total / 60, total % 60);
    }

    private String colorName() {
        return config.isHumanFirst() ? "黑" : "白";
    }

    /**
     * 标题样式：<b>楷体实心色 + 暖色柔光</b>。
     *
     * <p>不用「飞机大战」那套纵向渐变 + 冷色外发光 —— 那是它的招牌，
     * 五子棋另起一套，两者一眼能区分。</p>
     */
    private static String titleStyle(String textColor, String glow) {
        return "-fx-font-family: " + FONT_STACK + ";"
                + "-fx-font-size: " + TITLE_FONT_SIZE + "px; -fx-font-weight: bold;"
                + "-fx-text-fill: " + textColor + ";"
                + "-fx-effect: dropshadow(gaussian, " + glow + ", 14, 0.30, 0, 0);";
    }

    /**
     * 显示遮罩；状态切换时播放「由小到大淡入」的入场动画。
     *
     * @param animate true 表示这是状态切换（需要动画）
     */
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

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
