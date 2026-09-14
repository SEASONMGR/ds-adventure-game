package com.studio.launcher;

import com.studio.ui.FxAssets;
import com.studio.util.AppConfig;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;

/**
 * 设置面板（标题页「设置」）。
 *
 * <p>底板用美术交付的 {@code settings_panel.png}；三项可调：<b>音量</b>（全局倍率，立即生效）、
 * <b>打字机速度</b>（毫秒/字符，立即生效）、<b>自动播放 / 跳过已读</b>（本轮只做开关持久化，
 * 行为在下一轮接）。改动即时回调宿主写入 {@code config.ini}。</p>
 */
public final class SettingsView extends StackPane {

    /** 宿主回调：每项改动即时生效 + 关闭时落盘 */
    public interface Host {
        void onVolume(double volume);

        void onTypewriterSpeed(double msPerChar);

        void onAutoPlay(boolean on);

        void onSkipRead(boolean on);

        void onClose();
    }

    /** 默认值（与引擎取值的缺省一致） */
    public static final double DEFAULT_VOLUME = 1.0;
    public static final double DEFAULT_SPEED = 14.0;

    private final AppConfig config;
    private final Host host;
    private final File mapDir;

    private final Slider volume = new Slider(0, 1, DEFAULT_VOLUME);
    private final Slider speed = new Slider(4, 40, DEFAULT_SPEED);
    private final Label volumeText = new Label();
    private final Label speedText = new Label();
    private final ImageView autoToggle = new ImageView();
    private final ImageView skipToggle = new ImageView();
    private boolean autoPlay;
    private boolean skipRead;

    public SettingsView(AppConfig config, Host host) {
        this.config = config;
        this.host = host;
        this.mapDir = new File(System.getProperty("user.dir"), config.get("map.folder", "maps/story"));
        getStyleClass().add("overlay-backdrop");
        setPrefSize(1280, 720);
        setMaxSize(1280, 720);
        build();
    }

    // =====================================================================
    // 构建
    // =====================================================================

    private void build() {
        volume.setValue(config.getDouble("audio.volume", DEFAULT_VOLUME));
        speed.setValue(config.getDouble("typewriter.speed", DEFAULT_SPEED));
        autoPlay = bool(config, "auto.play", false);
        skipRead = bool(config, "skip.read", false);

        volume.getStyleClass().add("settings-slider");
        speed.getStyleClass().add("settings-slider");
        volume.setPrefWidth(260);
        speed.setPrefWidth(260);
        volume.valueProperty().addListener((o, a, b) -> {
            volumeText.setText(percent(b.doubleValue()));
            host.onVolume(b.doubleValue());
        });
        speed.valueProperty().addListener((o, a, b) -> {
            speedText.setText(ms((int) Math.round(b.doubleValue())));
            host.onTypewriterSpeed(b.doubleValue());
        });
        volumeText.setText(percent(volume.getValue()));
        speedText.setText(ms((int) Math.round(speed.getValue())));

        autoToggle.setImage(toggleArt(autoPlay));
        autoToggle.setCursor(javafx.scene.Cursor.HAND);
        autoToggle.setOnMouseClicked(e -> {
            autoPlay = !autoPlay;
            autoToggle.setImage(toggleArt(autoPlay));
            host.onAutoPlay(autoPlay);
        });
        skipToggle.setImage(toggleArt(skipRead));
        skipToggle.setCursor(javafx.scene.Cursor.HAND);
        skipToggle.setOnMouseClicked(e -> {
            skipRead = !skipRead;
            skipToggle.setImage(toggleArt(skipRead));
            host.onSkipRead(skipRead);
        });

        Label title = new Label("设置");
        title.getStyleClass().add("panel-title");

        VBox rows = new VBox(18,
                row("音量", volume, volumeText),
                row("打字机速度（越小越快）", speed, speedText),
                toggleRow("自动播放", autoToggle, "（开关已保存，行为下一轮接）"),
                toggleRow("跳过已读", skipToggle, "（开关已保存，行为下一轮接）"));
        rows.setAlignment(Pos.CENTER_LEFT);
        rows.setMaxWidth(520);

        Button reset = new Button("恢复默认");
        reset.getStyleClass().add("story-btn");
        reset.setPrefSize(150, 38);
        reset.setOnAction(e -> {
            volume.setValue(DEFAULT_VOLUME);
            speed.setValue(DEFAULT_SPEED);
            autoPlay = false;
            autoToggle.setImage(toggleArt(false));
            skipRead = false;
            skipToggle.setImage(toggleArt(false));
            host.onAutoPlay(false);
            host.onSkipRead(false);
        });
        Button back = new Button("返回");
        back.getStyleClass().add("story-btn");
        back.setPrefSize(150, 38);
        back.setOnAction(e -> host.onClose());
        HBox buttons = new HBox(14, reset, back);
        buttons.setAlignment(Pos.CENTER);

        VBox content = new VBox(16, title, rows, buttons);
        content.setAlignment(Pos.CENTER);
        content.setMaxSize(560, Region.USE_PREF_SIZE);

        ImageView panel = new ImageView(art("settings_panel"));
        panel.setFitWidth(640);
        panel.setPreserveRatio(true);
        panel.setSmooth(true);
        StackPane box = new StackPane(panel, content);
        box.setMaxSize(640, Region.USE_PREF_SIZE);
        getChildren().add(box);
        setAlignment(Pos.CENTER);
    }

    private HBox row(String label, Slider slider, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("panel-text");
        l.setPrefWidth(200);
        value.getStyleClass().add("panel-text");
        value.setPrefWidth(110);
        HBox hb = new HBox(12, l, slider, value);
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private HBox toggleRow(String label, ImageView toggle, String hint) {
        Label l = new Label(label);
        l.getStyleClass().add("panel-text");
        l.setPrefWidth(200);
        Label h = new Label(hint);
        h.getStyleClass().add("settings-hint");
        HBox hb = new HBox(12, l, toggle, h);
        hb.setAlignment(Pos.CENTER_LEFT);
        return hb;
    }

    private Image toggleArt(boolean on) {
        return art(on ? "toggle_on" : "toggle_off");
    }

    private Image art(String name) {
        return FxAssets.loadRooted(mapDir, "assets/sprites/ui/" + name + ".png");
    }

    /** 从 config.ini 读布尔（AppConfig 只有字符串/数字读取） */
    private static boolean bool(AppConfig c, String key, boolean def) {
        String v = c.get(key);
        if (v == null || v.isBlank()) return def;
        String t = v.trim();
        return t.equalsIgnoreCase("true") || t.equals("1") || t.equals("是") || t.equals("开");
    }

    static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    static String ms(int v) {
        return v + " ms/字符";
    }
}
