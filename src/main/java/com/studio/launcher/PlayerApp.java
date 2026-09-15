package com.studio.launcher;

import com.studio.reader.ReaderView;
import com.studio.util.AppConfig;
import com.studio.util.Logs;
import com.studio.util.MapTemplateFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * 播放器启动类。
 *
 * <p>启动后先显示<b>标题画面</b>（主菜单 F16：开始 / 继续 / 回忆收藏馆 / 设置 / 退出），
 * 选定后再进入 {@link ReaderView}；游戏中按 <b>ESC</b> 可弹出「继续 / 回到标题 / 退出」。</p>
 *
 * <p>地图文件夹解析优先级：</p>
 * <ol>
 *   <li>启动参数（命令行第一个非模式参数 / -Dstudio.map=xxx）；</li>
 *   <li>config.ini 的 map.folder；</li>
 *   <li>都不存在时，在 maps/demo_map 自动生成示例地图并加载（开箱即演示）。</li>
 * </ol>
 */
public class PlayerApp extends Application {

    private Stage stage;
    private Scene scene;
    private StackPane rootHost;      // 场景根：标题 / 游戏都挂在它里面
    private StackPane overlayHost;   // 浮层：设置 / 游戏内 ESC 菜单
    private File map;
    private AppConfig config;
    private ReaderView reader;
    private TitleView title;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.config = AppConfig.loadDefault();

        File resolved = resolveMapFolder(getParameters());
        if (resolved == null) {
            // 引导生成示例地图
            try {
                File demo = new File(System.getProperty("user.dir"), "maps/demo_map");
                if (!new File(demo, "scenario.txt").isFile()) {
                    MapTemplateFactory.createMap(demo, true);
                    System.out.println("[Player] 已自动生成示例地图: " + demo.getAbsolutePath());
                }
                resolved = demo;
            } catch (Exception e) {
                Logs.error("示例地图生成失败", e);
            }
        }
        if (resolved == null) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("启动失败");
            a.setHeaderText(null);
            a.setContentText("无法确定要加载的地图文件夹，请检查 config.ini 的 map.folder。");
            a.showAndWait();
            stage.close();
            return;
        }
        this.map = resolved;

        int w = config.getInt("window.width", 1280);
        int h = config.getInt("window.height", 720);

        // 画布固定 1280×720（与播放器一致），窗口可缩放但不拉伸素材
        Pane canvas = new Pane();
        canvas.setPrefSize(1280, 720);
        canvas.setMinSize(1280, 720);
        canvas.setMaxSize(1280, 720);

        rootHost = new StackPane(canvas);
        rootHost.setAlignment(Pos.CENTER);
        overlayHost = new StackPane();
        overlayHost.setVisible(false);
        overlayHost.setManaged(false);
        rootHost.getChildren().add(overlayHost);

        scene = new Scene(rootHost, w, h);
        var css = PlayerApp.class.getResource("/styles/player.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("剧情播放器 Player");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();

        showTitle();
    }

    // =====================================================================
    // 标题 ⇄ 游戏
    // =====================================================================

    /** 显示标题画面（从游戏返回前先清理游戏侧资源） */
    private void showTitle() {
        if (reader != null) {
            reader.disposeForTitle();
            reader = null;
        }
        hideOverlay();
        title = new TitleView(map, config, new TitleView.Host() {
            @Override
            public void onNewGame() {
                int n = MenuModel.clearSaves(map);
                Logs.info("[Title] 开始新游戏（清空存档 " + n + " 份）");
                startGame(null);
            }

            @Override
            public void onContinue(String slot) {
                startGame(slot);
            }

            @Override
            public void onSettings() {
                showSettings();
            }

            @Override
            public void onQuit() {
                confirmThenQuit();
            }
        });
        setCanvas(title);
    }

    /** 进入游戏；{@code slot} 非空时载入该存档（"继续游戏"） */
    private void startGame(String slot) {
        if (title != null) title.stopMusic();   // 标题 BGM 让位给剧情/小游戏 BGM
        reader = new ReaderView(stage, map, config);
        reader.setMasterVolumeScale(config.getDouble("audio.volume", 1.0));
        reader.setTypewriterSpeed(config.getDouble("typewriter.speed", 14));
        reader.setOnEscape(this::showGameMenu);
        setCanvas(reader);
        reader.start();
        if (slot != null && !slot.isBlank()) {
            boolean ok = reader.loadSlot(slot);
            Logs.info("[Title] 继续游戏：" + slot + " → " + (ok ? "已载入" : "载入失败")
                    + "（当前场景 " + reader.currentScene() + "）");
        }
    }

    private void setCanvas(javafx.scene.Node node) {
        Pane canvas = (Pane) rootHost.getChildren().get(0);
        canvas.getChildren().setAll(node);
    }

    // =====================================================================
    // 游戏内菜单（ESC）
    // =====================================================================

    private void showGameMenu() {
        StackPane panel = panel("游戏菜单",
                "随时可以继续，或回到标题重新开始。",
                new String[]{"继续游戏", "回到标题", "退出游戏"},
                which -> {
                    switch (which) {
                        case 0 -> hideOverlay();
                        case 1 -> showTitle();
                        case 2 -> confirmThenQuit();
                        default -> { }
                    }
                });
        showOverlay(panel);
    }

    private void confirmThenQuit() {
        StackPane panel = panel("退出游戏",
                "确定要退出吗？未存档的进度会丢失。",
                new String[]{"取消", "退出"},
                which -> {
                    if (which == 1) {
                        com.studio.util.Bgm.stop();
                        Platform.exit();
                    }
                    else hideOverlay();
                });
        showOverlay(panel);
    }

    // =====================================================================
    // 设置
    // =====================================================================

    private void showSettings() {
        SettingsView sv = new SettingsView(config, new SettingsView.Host() {
            @Override
            public void onVolume(double volume) {
                config.set("audio.volume", String.valueOf(volume));
                if (reader != null) reader.setMasterVolumeScale(volume);
            }

            @Override
            public void onTypewriterSpeed(double msPerChar) {
                config.set("typewriter.speed", String.valueOf(msPerChar));
                if (reader != null) reader.setTypewriterSpeed(msPerChar);
            }

            @Override
            public void onAutoPlay(boolean on) {
                config.set("auto.play", on ? "true" : "false");
            }

            @Override
            public void onSkipRead(boolean on) {
                config.set("skip.read", on ? "true" : "false");
            }

            @Override
            public void onClose() {
                config.save();
                hideOverlay();
            }
        });
        showOverlay(sv);
    }

    // =====================================================================
    // 浮层通用
    // =====================================================================

    private void showOverlay(javafx.scene.Node node) {
        overlayHost.getChildren().setAll(node);
        overlayHost.setVisible(true);
        overlayHost.setManaged(true);
    }

    private void hideOverlay() {
        overlayHost.getChildren().clear();
        overlayHost.setVisible(false);
        overlayHost.setManaged(false);
    }

    /** 简易面板（美术风格：复用 dialog_box 的配色，按钮用 story-btn） */
    private StackPane panel(String title, String desc, String[] buttons, java.util.function.IntConsumer onPick) {
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        Label d = new Label(desc);
        d.getStyleClass().add("panel-text");
        d.setWrapText(true);
        d.setMaxWidth(520);

        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(14);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < buttons.length; i++) {
            final int idx = i;
            javafx.scene.control.Button b = new javafx.scene.control.Button(buttons[i]);
            b.getStyleClass().add("story-btn");
            b.setPrefSize(150, 40);
            b.setOnAction(e -> onPick.accept(idx));
            row.getChildren().add(b);
        }

        VBox box = new VBox(14, t, d, row);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("overlay-panel");
        box.setMaxSize(640, javafx.scene.layout.Region.USE_PREF_SIZE);

        StackPane backdrop = new StackPane(box);
        backdrop.getStyleClass().add("overlay-backdrop");
        backdrop.setPrefSize(1280, 720);
        backdrop.setMaxSize(1280, 720);
        return backdrop;
    }

    /** 解析地图文件夹（参数 / 系统属性 / 配置文件） */
    private static File resolveMapFolder(Application.Parameters params) {
        List<String> raw = params.getRaw();
        for (String a : raw) {
            File f = new File(a);
            if (f.isDirectory() && new File(f, "scenario.txt").isFile()) {
                return f;
            }
        }
        String sys = System.getProperty("studio.map");
        if (sys != null && !sys.isBlank()) {
            File f = new File(sys);
            if (f.isDirectory() && new File(f, "scenario.txt").isFile()) return f;
        }
        AppConfig config = AppConfig.loadDefault();
        String folder = config.get("map.folder");
        if (folder == null || folder.isBlank()) return null;
        File f = new File(folder);
        if (!f.isAbsolute()) f = new File(System.getProperty("user.dir"), folder);
        return f.isDirectory() && new File(f, "scenario.txt").isFile() ? f : null;
    }
}
