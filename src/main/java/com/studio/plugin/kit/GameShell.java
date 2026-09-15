package com.studio.plugin.kit;

import com.studio.plugin.GamePlugin;
import com.studio.ui.FxAssets;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 小游戏统一外壳（9 个游戏共用），只用美术侧交付的素材，<b>不改引擎</b>：
 *
 * <pre>
 *   assets/sprites/ui/minigames/mg_&lt;game&gt;.png   剧情底图（960×540，含棋盘暗区）
 *   assets/sprites/ui/shell_frame.png           共用外框（1024×600，四角秤星）
 *   assets/sprites/ui/icon_game_&lt;game&gt;.png      游戏图标（48×48）
 *   assets/sprites/ui/shell_badge_{win,lose}.png 结算徽记（160×160）
 *   assets/sprites/ui/dialog_box.png            遮罩底板
 *   assets/sprites/ui/menu_button_*.png         按钮三态
 *   assets/sprites/ui/chapter_banner.png        顶栏标题条
 * </pre>
 *
 * <p>用法：构造 → {@link #boardLayer()} 放棋盘 → {@link #root()} 交回引擎。
 * 遮罩/状态/计数与逐动作音效（{@link #se(String)}）都由外壳负责。</p>
 */
public final class GameShell {

    /** 逻辑画布（与播放器一致） */
    public static final double W = 1280;
    public static final double H = 720;

    private final File mapDir;
    private final String gameId;
    private final String storyTitle;

    private final Pane canvas = new Pane();          // 固定 1280×720
    private final StackPane boardHost = new StackPane();
    private final Label titleLabel = new Label();
    private final Label statusLabel = new Label();
    private final Label counterLabel = new Label();
    private final HBox hudRight = new HBox(10);
    private final StackPane overlay = new StackPane();

    private Consumer<String> seSink;

    public GameShell(File mapDir, Map<String, Object> params, String gameId, String storyTitle) {
        this.mapDir = mapDir;
        this.gameId = gameId == null ? "" : gameId;
        this.storyTitle = storyTitle == null ? "" : storyTitle;
        if (params != null && params.get(GamePlugin.PARAM_SE) instanceof Consumer) {
            this.seSink = (Consumer<String>) params.get(GamePlugin.PARAM_SE);
        }
        build();
    }

    /** 逻辑画布尺寸（1280×720） */
    public Pane root() { return canvas; }

    /** 棋盘层：游戏把自己的盘面放这里（会被居中） */
    public Pane boardLayer() { return boardHost; }

    /** 顶栏右侧（各游戏放自己的计数/按钮/计时） */
    public HBox hudRight() { return hudRight; }

    public void setStatus(String text) { statusLabel.setText(text == null ? "" : text); }

    public void setCounter(String text) { counterLabel.setText(text == null ? "" : text); }

    /** 逐动作音效（缺失静默；引擎侧找不到文件只记日志） */
    public void se(String id) {
        if (seSink != null && id != null && !id.isBlank()) seSink.accept(id);
    }

    // =====================================================================
    // 构建
    // =====================================================================

    private void build() {
        canvas.setPrefSize(W, H);
        canvas.setMinSize(W, H);
        canvas.setMaxSize(W, H);
        canvas.getStyleClass().add("shell-root");

        // ① 剧情底图（960×540 → 铺满 1280×720，16:9 无损）
        ImageView bg = image("minigames/mg_" + gameId, W, true);
        if (bg != null) canvas.getChildren().add(bg);

        // ② 共用外框（按高度贴合，保持比例；垫在棋盘之下）
        ImageView frame = image("shell_frame", 0, false);
        if (frame != null) {
            frame.setFitHeight(H - 24);
            frame.setPreserveRatio(true);
            frame.setLayoutX((W - frame.getFitHeight() * 1024.0 / 600.0) / 2);
            frame.setLayoutY(12);
            frame.setOpacity(0.95);
            canvas.getChildren().add(frame);
        }

        // ③ 棋盘层（居中，压在底图暗区上）
        boardHost.setPrefSize(W, H);
        boardHost.setAlignment(Pos.CENTER);
        boardHost.setPadding(new Insets(74, 0, 26, 0));   // 给顶栏留位
        canvas.getChildren().add(boardHost);

        // ④ 顶栏：图标 + 剧情标题 + 状态 + 右侧（计数/按钮）
        ImageView icon = image("icon_game_" + gameId, 0, false);
        if (icon != null) {
            icon.setFitWidth(34);
            icon.setPreserveRatio(true);
            icon.setSmooth(true);
        }
        titleLabel.setText(storyTitle);
        titleLabel.getStyleClass().add("shell-title");
        statusLabel.getStyleClass().add("shell-status");
        counterLabel.getStyleClass().add("shell-counter");
        counterLabel.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox hud = new HBox(12);
        if (icon != null) hud.getChildren().add(icon);
        hud.getChildren().addAll(titleLabel, statusLabel, spacer, counterLabel, hudRight);
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.getStyleClass().add("shell-hud");
        hud.setPrefWidth(W - 80);
        hud.setLayoutX(40);
        hud.setLayoutY(22);
        canvas.getChildren().add(hud);

        // ⑤ 遮罩层
        overlay.setPrefSize(W, H);
        overlay.setVisible(false);
        overlay.setManaged(false);
        canvas.getChildren().add(overlay);
    }

    // =====================================================================
    // 遮罩（开始 / 过关 / 失败 / 通用）
    // =====================================================================

    /** 开始遮罩 */
    public void showStart(String desc, Runnable onStart) {
        show("开始", desc, new String[]{"开始游戏"}, i -> onStart.run(), null);
    }

    /** 过关遮罩（带胜利徽记） */
    public void showWin(String desc, Runnable onRestart) {
        show(storyTitle + " · 过关", desc, new String[]{"再来一局"}, i -> onRestart.run(), "shell_badge_win");
    }

    /** 失败遮罩（带失败徽记：断开的秤梁） */
    public void showLose(String desc, Runnable onRestart) {
        show(storyTitle + " · 失败", desc, new String[]{"再来一局"}, i -> onRestart.run(), "shell_badge_lose");
    }

    /** 通用遮罩 */
    public void showOverlay(String title, String desc, String[] buttons, java.util.function.IntConsumer onPick) {
        show(title, desc, buttons, onPick, null);
    }

    private void show(String title, String desc, String[] buttons,
                      java.util.function.IntConsumer onPick, String badge) {
        Label t = new Label(title);
        t.getStyleClass().add("shell-overlay-title");
        Label d = new Label(desc == null ? "" : desc);
        d.getStyleClass().add("shell-overlay-text");
        d.setWrapText(true);
        d.setMaxWidth(560);
        d.setAlignment(Pos.CENTER);

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < buttons.length; i++) {
            final int idx = i;
            Button b = new Button(buttons[i]);
            b.getStyleClass().add("story-btn");
            b.setPrefSize(170, 44);
            b.setOnAction(e -> onPick.accept(idx));
            row.getChildren().add(b);
        }

        VBox box = new VBox(12);
        if (badge != null) {
            ImageView bi = image(badge, 0, false);
            if (bi != null) {
                bi.setFitWidth(150);
                bi.setPreserveRatio(true);
                bi.setSmooth(true);
                box.getChildren().add(bi);
            }
        }
        box.getChildren().addAll(t, d, row);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("shell-overlay-panel");
        box.setMaxSize(720, Region.USE_PREF_SIZE);

        StackPane host = new StackPane(box);
        host.getStyleClass().add("shell-overlay-backdrop");
        host.setPrefSize(W, H);
        overlay.getChildren().setAll(host);
        overlay.setVisible(true);
        overlay.setManaged(true);
    }

    public void hideOverlay() {
        overlay.getChildren().clear();
        overlay.setVisible(false);
        overlay.setManaged(false);
    }

    public boolean overlayVisible() { return overlay.isVisible(); }

    // =====================================================================
    // 素材加载
    // =====================================================================

    private ImageView image(String name, double fitWidth, boolean fill) {
        try {
            Image img = FxAssets.loadRooted(mapDir, "assets/sprites/ui/" + name + ".png");
            if (img == null || img.isError()) return null;
            ImageView iv = new ImageView(img);
            if (fill) {
                iv.setFitWidth(W);
                iv.setFitHeight(H);
                iv.setPreserveRatio(false);
            } else if (fitWidth > 0) {
                iv.setFitWidth(fitWidth);
                iv.setPreserveRatio(true);
            }
            iv.setSmooth(true);
            return iv;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
