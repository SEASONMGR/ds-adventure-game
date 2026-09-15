package com.studio.plugin.demo.link;

import com.studio.plugin.GamePlugin;
import com.studio.plugin.MiniGameResult;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 连连看小游戏插件（事件 ID {@code link}，对应剧情第 6 章）。
 *
 * <p>规则见 {@link LinkGame}：<b>明牌</b>、图案相同且拐弯 ≤ 2 次可连即消除、清空棋盘通关、
 * 死局自动重排（上限可配，超限判负）。界面全部代码绘制（emoji 图标），不依赖美术素材；
 * 样式沿用 {@code player.css} 的主题类（{@code .link-*} 与 {@code .story-btn} 同系）。</p>
 *
 * <p>结果回传：通关/失败通过 {@link GamePlugin#PARAM_RESULT_SINK} 回传 {@link MiniGameResult}，
 * 供剧情按 {@code mg.onWin} / {@code mg.onLose} 分支；局中主动退出按需求记为失败。</p>
 */
public class LinkPlugin implements GamePlugin {

    /**
     * 牌面字形（零素材、代码绘制）。
     * <p>刻意用<b>中文字符</b>而不是 emoji：部分较新的 emoji（🪙 🧩 🀄 等）在有些系统字体里缺字形，
     * 会渲染成空白方框；中文单字必定存在，且贴合"鲸 / 鳞 / 秤"的世界观。</p>
     */
    private static final String[] ICONS = {"鲸", "鳞", "秤", "灯", "泉", "梦", "风", "歌", "雨", "云"};

    private LinkConfig config = LinkConfig.defaults();
    private final LinkGame game = new LinkGame();

    private Button[][] cells;
    private com.studio.plugin.kit.GameShell shell;
    private File mapDir;
    private Map<String, Object> params;

    private AnimationTimer loop;
    private long lastNanos;
    private boolean started;
    private boolean reported;

    private Runnable backCallback;
    private Consumer<MiniGameResult> resultSink;

    // =====================================================================
    // GamePlugin
    // =====================================================================

    @Override
    public void execute(Stage stage, Map<String, Object> params) {
        Object back = params == null ? null : params.get(PARAM_BACK_CALLBACK);
        this.backCallback = (back instanceof Runnable) ? (Runnable) back : null;
        Object sink = params == null ? null : params.get(PARAM_RESULT_SINK);
        this.resultSink = (sink instanceof Consumer)
                ? (Consumer<MiniGameResult>) sink : null;
        readConfig(params);
    }

    @Override
    public Parent createEmbeddedView(Map<String, Object> params) {
        this.params = params;
        Object mf = params == null ? null : params.get(PARAM_MAP_FOLDER);
        this.mapDir = (mf instanceof File) ? (File) mf : new File(System.getProperty("user.dir"), "maps/story");
        if (params != null && params.get(PARAM_RESULT_SINK) instanceof Consumer) {
            this.resultSink = (Consumer<MiniGameResult>) params.get(PARAM_RESULT_SINK);
        }
        readConfig(params);
        Parent root = buildRoot();
        startLoop();
        return root;
    }

    @Override
    public String displayName() {
        return "连连看小游戏";
    }

    @Override
    public void onDetach() {
        stopLoop();
        // 局中退出（插件自己的返回键、或引擎外框的"返回剧情"）且尚未回传 → 按失败回传，
        // 否则引擎会把"没结果"当成胜利，玩家中途退出反而算赢。
        if (!reported) {
            reported = true;
            if (resultSink != null) {
                resultSink.accept(MiniGameResult.lose(
                        Math.max(0, config.totalTiles() - game.remainingTiles())));
            }
        }
    }

    // =====================================================================
    // 参数
    // =====================================================================

    private void readConfig(Map<String, Object> params) {
        int rows = intParam(params, "rows", 8);
        int cols = intParam(params, "cols", 10);
        int icons = intParam(params, "iconTypes", 10);
        int limit = intParam(params, "timeLimitSec", 0);
        int shuffles = intParam(params, "maxShuffles", 5);
        config = new LinkConfig(rows, cols, icons, limit, shuffles);
    }

    private static int intParam(Map<String, Object> params, String key, int def) {
        if (params == null) return def;
        Object v = params.get(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // =====================================================================
    // 界面
    // =====================================================================

    private Parent buildRoot() {
        // 统一外壳：剧情底图 mg_link + 共用外框 + 图标顶栏 + 遮罩（素材来自美术侧交付）
        shell = new com.studio.plugin.kit.GameShell(mapDir, params, "link", "连连看");
        shell.setStatus("点两张相同图案；拐弯不超过两次即可消除");

        Button shuffle = new Button("↻ 重排");
        shuffle.getStyleClass().add("story-btn");
        shuffle.setPrefSize(96, 34);
        shuffle.setOnAction(e -> {
            if (!started || game.isOver()) return;
            if (game.shuffleRemaining()) {
                shell.se("se_hint");
                shell.setStatus("无可消对：已重排（" + game.shufflesUsed() + "/" + config.maxShuffles + "）");
            } else {
                shell.setStatus("重排次数用尽");
            }
            refresh();
            checkOver();
        });
        shell.hudRight().getChildren().add(shuffle);

        GridPane board = new GridPane();
        board.setHgap(4);
        board.setVgap(4);
        board.getStyleClass().add("link-board");
        board.setPadding(new Insets(10));
        board.setAlignment(javafx.geometry.Pos.CENTER);   // 棋盘居中压在底图暗区上

        cells = new Button[config.rows][config.cols];
        for (int r = 0; r < config.rows; r++) {
            for (int c = 0; c < config.cols; c++) {
                Button b = new Button();
                b.getStyleClass().add("link-tile");
                b.setPrefSize(52, 52);
                b.setMinSize(52, 52);
                b.setMaxSize(52, 52);
                b.setFocusTraversable(false);
                final int rr = r, cc = c;
                b.setOnAction(e -> onTileClicked(rr, cc));
                cells[r][c] = b;
                board.add(b, c, r);
            }
        }
        shell.boardLayer().getChildren().add(board);

        game.reset(config);
        started = false;
        refresh();
        shell.showStart("在 " + config.rows + "×" + config.cols + " 的盘面上，点选两张相同图案；\n"
                + "只要连线的拐弯不超过两次就能消除。清空盘面即通关。", this::startOrRestart);
        return shell.root();
    }

    private void onTileClicked(int r, int c) {
        if (!started || game.isOver()) return;
        LinkGame.Change ch = game.select(r, c);
        switch (ch) {
            case MATCHED -> {
                shell.se("se_link");
                shell.setStatus(hintFor(ch));
            }
            case SELECTED, DESELECTED, MISMATCH -> shell.setStatus(hintFor(ch));
            case NO_PATH -> {
                shell.se("se_error");
                shell.setStatus("这两张连不上（拐弯超过两次或被挡住）");
                flashMismatch();
            }
            default -> { }
        }
        if (ch == LinkGame.Change.MATCHED && game.isOver()) {
            shell.setStatus("盘面已清空");
        }
        refresh();
        checkOver();
    }

    private String hintFor(LinkGame.Change ch) {
        return switch (ch) {
            case SELECTED -> "已选中第一张，再点一张相同的";
            case DESELECTED -> "已取消选中";
            case MATCHED -> "消除成功！";
            case MISMATCH -> "图案不同，已改为选中新的一张";
            default -> "";
        };
    }

    /** 失配时让两张牌轻微抖动一下（纯样式反馈，不引动画依赖） */
    private void flashMismatch() {
        for (int r = 0; r < config.rows; r++) {
            for (int c = 0; c < config.cols; c++) {
                Button b = cells[r][c];
                if (b.getStyleClass().contains("link-tile-bad")) {
                    b.getStyleClass().remove("link-tile-bad");
                }
            }
        }
        if (game.selectedRow() >= 0) {
            cells[game.selectedRow()][game.selectedCol()].getStyleClass().add("link-tile-bad");
        }
    }

    // =====================================================================
    // 状态刷新 / 循环 / 结算
    // =====================================================================

    private void refresh() {
        for (int r = 0; r < config.rows; r++) {
            for (int c = 0; c < config.cols; c++) {
                Button b = cells[r][c];
                int icon = game.iconAt(r, c);
                boolean empty = icon == 0;
                b.setText(empty ? "" : ICONS[Math.floorMod(icon - 1, ICONS.length)]);
                b.setDisable(empty);
                b.getStyleClass().removeAll("link-tile-sel", "link-tile-empty");
                if (empty) {
                    b.getStyleClass().add("link-tile-empty");
                } else if (game.isSelected(r, c)) {
                    b.getStyleClass().add("link-tile-sel");
                }
            }
        }
        String timer = config.timeLimitSec > 0
                ? String.format("　⏱ %02d:%02d", (int) game.remainingSeconds() / 60, (int) game.remainingSeconds() % 60)
                : "";
        shell.setCounter("剩余 " + game.remainingPairs() + " 对" + timer);
    }

    private void startLoop() {
        stopLoop();
        lastNanos = System.nanoTime();
        loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double dt = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;
                if (started && !game.isOver()) {
                    game.tick(Math.min(dt, 0.25));
                    refresh();
                    checkOver();
                }
            }
        };
        loop.start();
    }

    private void stopLoop() {
        if (loop != null) {
            loop.stop();
            loop = null;
        }
    }

    private void checkOver() {
        if (!game.isOver() || reported) return;
        finish(game.isWin());
    }

    /** 结算并回传结果（每局只回传一次） */
    private void finish(boolean win) {
        if (reported) return;
        reported = true;
        int cleared = config.totalTiles() - game.remainingTiles();
        int score = cleared * 10 + (config.timeLimitSec > 0 ? (int) game.remainingSeconds() : 0);
        if (resultSink != null) {
            resultSink.accept(win ? MiniGameResult.win(score) : MiniGameResult.lose(score));
        }
        stopLoop();
        String desc = "消除 " + (cleared / 2) + " 对 · 重排 " + game.shufflesUsed() + " 次 · 得分 " + score;
        // 零重排通关视为"满评价"（美术侧 §5.3 的 se_perfect）
        shell.se(win ? (game.shufflesUsed() == 0 ? "se_perfect" : "se_win") : "se_lose");
        if (win) shell.showWin(desc, this::startOrRestart);
        else shell.showLose(desc, this::startOrRestart);
    }

    private void startOrRestart() {
        game.reset(config);
        reported = false;
        started = true;
        shell.se("se_start");
        shell.hideOverlay();
        shell.setStatus("点两张相同图案；拐弯不超过两次即可消除");
        refresh();
        startLoop();
    }

    private void leaveToStory() {
        stopLoop();
        // 回传交给 onDetach（无论从哪个入口退出，都只回传一次、且都算失败）
        if (backCallback != null) {
            backCallback.run();
        }
    }

}
