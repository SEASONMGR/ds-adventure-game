package com.studio.launcher;

import com.studio.ui.FxAssets;
import com.studio.util.AppConfig;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 标题画面（主菜单 F16）。
 *
 * <p>布局基于美术侧交付的 47 件 UI 素材：{@code title_bg} 铺底、{@code title_logo_a} 字标、
 * {@code menu_button_normal/hover} 菜单按钮、{@code dialog_box} 覆盖确认与占位面板。
 * 画面为固定 1280×720（与播放器画布一致），不随窗口缩放，避免拉伸糊图。</p>
 *
 * <p>菜单语义（状态判断全部委托给纯逻辑的 {@link MenuModel}）：</p>
 * <ul>
 *   <li>开始：有存档 → 覆盖确认（默认焦点在「取消」）→ 确认后清档并开局</li>
 *   <li>继续：载入最新存档；<b>无存档置灰不可点</b></li>
 *   <li>回忆收藏馆：本轮为"开发中"面板（下一轮做真馆藏）</li>
 *   <li>设置：交给宿主（{@link Host#onSettings()}），因为设置要作用到正在运行的游戏</li>
 *   <li>退出：交给宿主确认后退出</li>
 * </ul>
 */
public final class TitleView extends Pane {

    /** 宿主（{@code PlayerApp}）回调 */
    public interface Host {
        /** 开始新游戏（宿主负责清档与进入游戏） */
        void onNewGame();

        /** 继续：载入指定存档槽 */
        void onContinue(String slot);

        /** 打开设置 */
        void onSettings();

        /** 退出游戏 */
        void onQuit();
    }

    private static final double W = 1280;
    private static final double H = 720;
    private static final double BTN_W = 208;
    private static final double BTN_H = 52;
    private static final double MENU_X = 940;
    private static final double MENU_Y = 250;
    private static final double MENU_GAP = 62;

    /** logo 变体（a/b/c 均由美术交付，默认 a） */
    private static final String LOGO = "title_logo_a";

    private final File mapDir;
    private final AppConfig config;
    private final Host host;
    private final MenuModel model;

    private final List<StackPane> menuButtons = new ArrayList<>();
    private StackPane overlay;          // 覆盖确认 / 收藏馆占位 / 都挂在这一层

    public TitleView(File mapDir, AppConfig config, Host host) {
        this.mapDir = mapDir;
        this.config = config;
        this.host = host;
        this.model = MenuModel.scan(mapDir);
        getStyleClass().add("title-root");
        // 标题画面 BGM（曲库来自美术侧 bgm_map.json；标题曲非无缝循环，这里强制循环）
        com.studio.util.Bgm.play("title", 0.55, true);
        setPrefSize(W, H);
        setMinSize(W, H);
        setMaxSize(W, H);
        build();
    }

    /** 当前菜单状态（供测试/宿主查询） */
    public MenuModel model() { return model; }

    /** 离开标题（进入游戏/退出）时停掉标题 BGM */
    public void stopMusic() {
        com.studio.util.Bgm.stop();
    }

    // =====================================================================
    // 构建
    // =====================================================================

    private void build() {
        // 底图（16:9，等比铺满）
        ImageView bg = image("title_bg", 0, 0);
        bg.setFitWidth(W);
        bg.setPreserveRatio(true);
        getChildren().add(bg);

        // 暗角，让字标与菜单更清晰
        ImageView vig = image("vignette", 0, 0);
        vig.setFitWidth(W);
        vig.setPreserveRatio(true);
        vig.setOpacity(0.55);
        getChildren().add(vig);

        // 字标（828×420，缩到 0.62 倍后居中偏上）
        ImageView logo = image(LOGO, 0, 0);
        logo.setFitWidth(828 * 0.62);
        logo.setPreserveRatio(true);
        logo.setLayoutX((W - 828 * 0.62) / 2);
        logo.setLayoutY(16);
        getChildren().add(logo);

        // 菜单（右列）
        addMenuItem("开始游戏", MenuModel.Action.NEW_GAME, 0);
        addMenuItem("继续游戏", MenuModel.Action.CONTINUE, 1);
        addMenuItem("回忆收藏馆", MenuModel.Action.GALLERY, 2);
        addMenuItem("设置", MenuModel.Action.SETTINGS, 3);
        addMenuItem("退出", MenuModel.Action.QUIT, 4);
        refreshMenuState();

        // 存档信息（右列下方，右对齐）
        Label saveInfo = new Label(model.saveInfoText());
        saveInfo.getStyleClass().add("title-save-info");
        saveInfo.setPrefWidth(408);
        saveInfo.setAlignment(Pos.CENTER_RIGHT);
        saveInfo.setLayoutX(MENU_X + BTN_W - 408);
        saveInfo.setLayoutY(MENU_Y + 5 * MENU_GAP + 4);
        getChildren().add(saveInfo);

        // 版本 / 说明（左下角，刻意不含任何真实姓名）
        Label ver = new Label("ds-adventure · 剧情试玩版 · " + versionText());
        ver.getStyleClass().add("title-version");
        ver.setLayoutX(24);
        ver.setLayoutY(H - 34);
        getChildren().add(ver);

        // 浮层容器（确认/收藏馆都挂这里）
        overlay = new StackPane();
        overlay.setPrefSize(W, H);
        overlay.setVisible(false);
        overlay.setManaged(false);
        getChildren().add(overlay);
    }

    private String versionText() {
        String v = config == null ? "" : config.get("app.version", "");
        return v == null || v.isBlank() ? "v0.4.0-demo" : v;
    }

    private void addMenuItem(String text, MenuModel.Action action, int index) {
        StackPane btn = menuButton(text);
        btn.setLayoutX(MENU_X);
        btn.setLayoutY(MENU_Y + index * MENU_GAP);
        btn.setOnMouseClicked(e -> onMenuClicked(action));
        menuButtons.add(btn);
        getChildren().add(btn);
    }

    /** 菜单按钮：底图（normal/hover 切换）+ 居中文字 */
    private StackPane menuButton(String text) {
        ImageView bg = image("menu_button_normal", 0, 0);
        bg.setFitWidth(BTN_W);
        bg.setPreserveRatio(true);
        Label label = new Label(text);
        label.getStyleClass().add("menu-label");
        StackPane p = new StackPane(bg, label);
        p.getStyleClass().add("title-menu-btn");
        p.setPrefSize(BTN_W, BTN_H);
        p.setMinSize(BTN_W, BTN_H);
        p.setMaxSize(BTN_W, BTN_H);
        p.setCursor(javafx.scene.Cursor.HAND);
        p.setOnMouseEntered(e -> {
            if (!p.isDisable()) setButtonArt(p, "menu_button_hover");
        });
        p.setOnMouseExited(e -> setButtonArt(p, "menu_button_normal"));
        return p;
    }

    private void setButtonArt(StackPane btn, String name) {
        if (btn.getChildren().isEmpty()) return;
        if (btn.getChildren().get(0) instanceof ImageView iv) {
            iv.setImage(FxAssets.loadRooted(mapDir, "assets/sprites/ui/" + name + ".png"));
        }
    }

    /** 按状态刷新菜单（无存档时「继续」置灰） */
    private void refreshMenuState() {
        int idx = 1;                                  // 继续 = 第 2 项
        StackPane cont = menuButtons.get(idx);
        boolean can = model.canContinue();
        cont.setDisable(!can);
        cont.setOpacity(can ? 1.0 : 0.42);
        cont.setCursor(can ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
    }

    // =====================================================================
    // 交互
    // =====================================================================

    private void onMenuClicked(MenuModel.Action action) {
        switch (model.resolve(action)) {
            case NEW_GAME -> {
                if (model.needsOverwriteConfirm()) showConfirm();
                else host.onNewGame();
            }
            case CONTINUE -> {
                MenuModel.SaveInfo latest = model.latestSave();
                if (latest != null) host.onContinue(latest.slot());
            }
            case GALLERY -> showGalleryPlaceholder();
            case SETTINGS -> host.onSettings();
            case QUIT -> host.onQuit();
            default -> { /* 置灰项：什么都不做 */ }
        }
    }

    /** 覆盖确认（默认焦点在「取消」，文案明确会清档） */
    private void showConfirm() {
        Label title = new Label(MenuModel.confirmTitle());
        title.getStyleClass().add("panel-title");
        Label msg = new Label(MenuModel.confirmMessage());
        msg.getStyleClass().add("panel-text");
        msg.setWrapText(true);
        msg.setMaxWidth(560);

        StackPane cancel = panelButton(MenuModel.confirmCancelText());
        cancel.setOnMouseClicked(e -> hideOverlay());
        StackPane ok = panelButton(MenuModel.confirmOkText());
        ok.setOnMouseClicked(e -> {
            hideOverlay();
            host.onNewGame();
        });

        VBox box = panelBox(title, msg, row(cancel, ok));
        showOverlay(box);
    }

    /** 回忆收藏馆：本轮占位（下一轮做真馆藏：22 张 CG + 5 个结局） */
    private void showGalleryPlaceholder() {
        Label title = new Label("回忆收藏馆");
        title.getStyleClass().add("panel-title");
        Label msg = new Label("收藏馆开发中 —— 将展示 22 张 CG 与 5 个结局的收集进度。\n"
                + "（需要先记录「哪些 CG 被看过」，安排在下一轮）");
        msg.getStyleClass().add("panel-text");
        msg.setWrapText(true);
        msg.setMaxWidth(560);

        StackPane back = panelButton("返回");
        back.setOnMouseClicked(e -> hideOverlay());
        showOverlay(panelBox(title, msg, row(back)));
    }

    private VBox panelBox(Label title, Label msg, javafx.scene.Node buttons) {
        ImageView box = image("dialog_box", 0, 0);
        box.setFitWidth(760);
        box.setPreserveRatio(true);
        VBox content = new VBox(14, title, msg, buttons);
        content.setAlignment(Pos.CENTER);
        content.setMaxSize(700, Region.USE_PREF_SIZE);
        StackPane pane = new StackPane(box, content);
        pane.setMaxSize(760, Region.USE_PREF_SIZE);
        VBox wrap = new VBox(pane);
        wrap.setAlignment(Pos.CENTER);
        wrap.setPrefSize(W, H);
        return wrap;
    }

    private javafx.scene.Node row(javafx.scene.Node... nodes) {
        javafx.scene.layout.HBox hb = new javafx.scene.layout.HBox(16, nodes);
        hb.setAlignment(Pos.CENTER);
        return hb;
    }

    /** 面板内的小按钮（复用菜单按钮底图，尺寸缩小） */
    private StackPane panelButton(String text) {
        ImageView bg = image("menu_button_normal", 0, 0);
        bg.setFitWidth(180);
        bg.setPreserveRatio(true);
        Label label = new Label(text);
        label.getStyleClass().add("menu-label");
        StackPane p = new StackPane(bg, label);
        p.getStyleClass().add("title-menu-btn");
        p.setPrefSize(180, 45);
        p.setMinSize(180, 45);
        p.setMaxSize(180, 45);
        p.setCursor(javafx.scene.Cursor.HAND);
        p.setOnMouseEntered(e -> setButtonArt(p, "menu_button_hover"));
        p.setOnMouseExited(e -> setButtonArt(p, "menu_button_normal"));
        return p;
    }

    private void showOverlay(VBox content) {
        overlay.getChildren().setAll(content);
        overlay.setVisible(true);
        overlay.setManaged(true);
    }

    /** 关掉浮层（宿主在进入游戏前调用，保证画面干净） */
    public void hideOverlay() {
        overlay.getChildren().clear();
        overlay.setVisible(false);
        overlay.setManaged(false);
    }

    private ImageView image(String name, double x, double y) {
        Image img = FxAssets.loadRooted(mapDir, "assets/sprites/ui/" + name + ".png");
        ImageView iv = new ImageView(img);
        iv.setLayoutX(x);
        iv.setLayoutY(y);
        iv.setSmooth(true);
        return iv;
    }
}
