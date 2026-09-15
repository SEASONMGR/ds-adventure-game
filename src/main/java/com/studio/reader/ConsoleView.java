package com.studio.reader;

import com.studio.model.GameOption;
import com.studio.model.SaveVarDef;
import com.studio.model.StoryNode;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 播放器<b>控制台</b>：调试/演出用的浮层，包含场景列表、变量列表、存档与指令输入。
 *
 * <p>开关与快捷键由地图的 {@code [option]} 决定（{@code console} / {@code consoleKey}，旧地图默认关闭、默认键 {@code `}）；
 * 在编辑器里「在播放器中测试」时也会自动允许打开（方便调试图层）。</p>
 *
 * <p>指令分两类：控制台自带的（help / scenes / goto / vars / set / save / load / saves / env / plugin / clear / close），
 * 以及外部通过 {@link ConsoleRegistry#register} 注册的插件指令。</p>
 */
public final class ConsoleView extends StackPane {

    private final ConsoleRegistry.Context ctx;
    private final GameOption option;

    private final ObservableList<String> sceneItems = FXCollections.observableArrayList();
    private final ListView<String> sceneList = new ListView<>(sceneItems);
    private final TextField sceneFilter = new TextField();

    private final ObservableList<VarRow> varItems = FXCollections.observableArrayList();
    private final TableView<VarRow> varTable = new TableView<>(varItems);
    private Button showEnvButton;
    /** 「显示扩展变量」开关：打开时把扩展变量作为只读行追加进变量表 */
    private boolean showExtVars = false;

    private final ObservableList<String> saveItems = FXCollections.observableArrayList();
    private final ListView<String> saveList = new ListView<>(saveItems);
    private final TextField saveName = new TextField();

    private final TextArea log = new TextArea();
    private final TextField input = new TextField();
    private final Label status = new Label();

    private List<String> history = new ArrayList<>();
    private int historyPos = 0;

    /** 变量表的一行 */
    public static final class VarRow {
        private final javafx.beans.property.SimpleStringProperty name;
        private final javafx.beans.property.SimpleStringProperty value;
        private final javafx.beans.property.SimpleStringProperty type;
        /** 扩展变量行：只读，不允许在表格里编辑 */
        private final boolean readOnly;

        VarRow(String name, String value, String type) { this(name, value, type, false); }

        VarRow(String name, String value, String type, boolean readOnly) {
            this.name = new javafx.beans.property.SimpleStringProperty(name);
            this.value = new javafx.beans.property.SimpleStringProperty(value);
            this.type = new javafx.beans.property.SimpleStringProperty(type);
            this.readOnly = readOnly;
        }

        public javafx.beans.property.SimpleStringProperty nameProperty() { return name; }
        public javafx.beans.property.SimpleStringProperty valueProperty() { return value; }
        public javafx.beans.property.SimpleStringProperty typeProperty() { return type; }
        public String getName() { return name.get(); }
        public String getValue() { return value.get(); }
        public boolean isReadOnly() { return readOnly; }
    }

    public ConsoleView(ConsoleRegistry.Context ctx, GameOption option) {
        this.ctx = ctx;
        this.option = option;
        getStyleClass().add("console-root");
        setStyle("-fx-background-color: rgba(4,8,16,0.78);");
        setAlignment(Pos.CENTER);
        setPickOnBounds(true);      // 挡住鼠标，避免点到下面的剧情

        BorderPane panel = new BorderPane();
        panel.getStyleClass().add("console-panel");
        panel.setMaxSize(980, 620);
        panel.setPrefSize(980, 620);
        panel.setStyle("-fx-background-color: rgba(10,14,26,0.98);"
                + "-fx-background-radius: 12; -fx-border-color: #3e6fa8; -fx-border-width: 1.5;"
                + "-fx-border-radius: 12;");
        panel.setTop(buildHeader());
        panel.setCenter(buildBody());
        panel.setBottom(buildInputBar());
        getChildren().add(panel);

        refreshScenes();
        refreshVars();
        refreshSaves();
        println("控制台已打开。输入 help 查看指令；快捷键 " + option.consoleKey() + " 可开关本窗口。");
    }

    // =====================================================================
    // 布局
    // =====================================================================

    private Region buildHeader() {
        Label title = new Label("🖥 控制台");
        title.setStyle("-fx-text-fill: #9fd8ff; -fx-font-size: 16px; -fx-font-weight: bold;");
        Label hint = new Label("场景跳转 / 变量查看与修改 / 存档读档 / 指令输入　（Esc 关闭）");
        hint.setStyle("-fx-text-fill: #9fb0c8; -fx-font-size: 12px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("✕ 关闭");
        close.setOnAction(e -> close());
        HBox box = new HBox(12, title, hint, spacer, close);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 12, 10, 12));
        return box;
    }

    private Region buildBody() {
        SplitPane split = new SplitPane(buildScenePane(), buildRightTabs());
        split.setDividerPositions(0.32);
        SplitPane.setResizableWithParent(split.getItems().get(0), Boolean.FALSE);
        return split;
    }

    /** 左：场景列表（可过滤、可选中、可跳转） */
    private Region buildScenePane() {
        Label title = new Label("📄 场景列表");
        title.setStyle("-fx-text-fill: #cfe6ff; -fx-font-weight: bold;");
        sceneFilter.setPromptText("过滤场景名…");
        sceneFilter.textProperty().addListener((o, a, b) -> refreshScenes());
        Button jump = new Button("跳转 ▶");
        jump.setMaxWidth(Double.MAX_VALUE);
        jump.setOnAction(e -> jumpToSelectedScene());
        sceneList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) jumpToSelectedScene();
        });
        VBox.setVgrow(sceneList, Priority.ALWAYS);
        VBox box = new VBox(6, title, sceneFilter, sceneList, jump);
        box.setPadding(new Insets(10));
        return box;
    }

    /** 右：变量 / 存档 / 指令 三个页签 */
    private Region buildRightTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("变量", buildVarsPane()),
                new Tab("存档", buildSavesPane()),
                new Tab("指令", buildLogPane()));
        return tabs;
    }

    private Region buildVarsPane() {
        TableColumn<VarRow, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cd -> cd.getValue().nameProperty());
        nameCol.setPrefWidth(190);
        TableColumn<VarRow, String> valCol = new TableColumn<>("值（双击可改）");
        valCol.setCellValueFactory(cd -> cd.getValue().valueProperty());
        valCol.setPrefWidth(240);
        valCol.setEditable(true);
        valCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valCol.setOnEditCommit(e -> {
            VarRow row = e.getRowValue();
            if (row.isReadOnly()) {
                // 扩展变量是只读的：把改回去的原值刷回来，并说明原因
                println("「" + row.getName() + "」是只读的扩展变量（由插件/宿主提供），不能在这里改");
                refreshVars();
                return;
            }
            String v = e.getNewValue() == null ? "" : e.getNewValue();
            ctx.setVariable(row.getName(), v);
            row.valueProperty().set(v);
            int n = ctx.refreshAfterVariableChange();
            println("[已设置] " + row.getName() + " = " + v + "　（重绘了 " + n + " 个引用变量的节点）");
            if (n == 0) println("  提示：若该地图用槽把变量映射到节点，改用 emit <信号名> 触发那条槽");
            refreshVars();
        });
        TableColumn<VarRow, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(cd -> cd.getValue().typeProperty());
        typeCol.setPrefWidth(90);
        varTable.getColumns().addAll(nameCol, valCol, typeCol);
        varTable.setEditable(true);
        varTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        varTable.setPlaceholder(new Label("当前没有变量（[option] 里声明 savevar 后这里会出现）"));

        Button refresh = new Button("↻ 刷新变量");
        refresh.setOnAction(e -> { refreshVars(); println("已刷新变量（" + varItems.size() + " 个）"); });
        showEnvButton = new Button("🔌 显示扩展变量");
        showEnvButton.setTooltip(new javafx.scene.control.Tooltip(
                "把插件/宿主通过 ConsoleRegistry.registerEnvProvider(分组, 提供者) 注册的只读信息\n"
                        + "直接列进这张变量表（类型列标「扩展·只读」，不可编辑）。\n"
                        + "它不是操作系统环境变量；地图自己的变量请直接双击上面的行改。"));
        showEnvButton.setOnAction(e -> toggleExtVars());
        HBox bar = new HBox(8, refresh, showEnvButton);

        VBox box = new VBox(8, varTable, bar);
        box.setPadding(new Insets(10));
        VBox.setVgrow(varTable, Priority.ALWAYS);
        return box;
    }

    private Region buildSavesPane() {
        Label title = new Label("💾 存档（读取已有存档 / 存档 / 读档）");
        title.setStyle("-fx-text-fill: #cfe6ff; -fx-font-weight: bold;");
        saveName.setPromptText("存档名（留空则用选中项；例如 slot1 或 slot_ch0_depart）");
        Button refresh = new Button("↻ 刷新列表");
        refresh.setOnAction(e -> { refreshSaves(); println("已有存档 " + saveItems.size() + " 个"); });
        Button doSave = new Button("💾 存档");
        doSave.setOnAction(e -> {
            String slot = slotName();
            if (slot.isBlank()) { println("[存档失败] 没有指定存档名"); return; }
            boolean ok = ctx.save(slot);
            println((ok ? "[存档成功] " : "[存档失败] ") + slot);
            refreshSaves();
        });
        Button doLoad = new Button("📂 读档");
        doLoad.setOnAction(e -> {
            String slot = slotName();
            if (slot.isBlank()) { println("[读档失败] 没有指定存档名"); return; }
            boolean ok = ctx.load(slot);
            println((ok ? "[读档成功] " : "[读档失败] ") + slot);
            if (ok) { refreshVars(); refreshScenes(); }
        });
        saveList.setOnMouseClicked(e -> {
            if (saveList.getSelectionModel().getSelectedItem() != null) {
                saveName.setText(saveList.getSelectionModel().getSelectedItem());
            }
        });
        HBox bar = new HBox(8, refresh, doSave, doLoad);
        VBox.setVgrow(saveList, Priority.ALWAYS);
        VBox box = new VBox(8, title, saveList, saveName, bar);
        box.setPadding(new Insets(10));
        return box;
    }

    private Region buildLogPane() {
        log.setEditable(false);
        log.setWrapText(true);
        log.setStyle("-fx-font-family: 'Consolas','Microsoft YaHei UI',monospace; -fx-font-size: 12px;");
        VBox.setVgrow(log, Priority.ALWAYS);
        VBox box = new VBox(6, log);
        box.setPadding(new Insets(10));
        return box;
    }

    private Region buildInputBar() {
        input.setPromptText("输入指令后回车（help 查看全部指令）");
        input.setOnAction(e -> runInput());
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.UP && !history.isEmpty()) {
                historyPos = Math.max(0, historyPos - 1);
                input.setText(history.get(historyPos));
                input.positionCaret(input.getText().length());
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN && !history.isEmpty()) {
                historyPos = Math.min(history.size(), historyPos + 1);
                input.setText(historyPos >= history.size() ? "" : history.get(historyPos));
                input.positionCaret(input.getText().length());
                e.consume();
            }
        });
        Button exec = new Button("执行 ⏎");
        exec.setOnAction(e -> runInput());
        HBox.setHgrow(input, Priority.ALWAYS);
        status.setStyle("-fx-text-fill: #8f9fbc; -fx-font-size: 11px;");
        HBox bar = new HBox(8, new Label("›"), input, exec);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 4, 12));
        VBox box = new VBox(2, bar, status);
        box.setPadding(new Insets(0, 8, 8, 8));
        return box;
    }

    // =====================================================================
    // 刷新数据
    // =====================================================================

    private void refreshScenes() {
        String filter = sceneFilter.getText() == null ? "" : sceneFilter.getText().trim().toLowerCase(Locale.ROOT);
        String current = ctx.currentScene();
        sceneItems.clear();
        for (String s : ctx.scenes()) {
            if (filter.isEmpty() || s.toLowerCase(Locale.ROOT).contains(filter)) sceneItems.add(s);
        }
        if (current != null) {
            for (String s : sceneItems) {
                if (s.equals(current)) { sceneList.getSelectionModel().select(s); break; }
            }
        }
        status.setText("场景 " + sceneItems.size() + " 个" + (current == null ? "" : "　当前：" + current));
    }

    private void refreshVars() {
        Map<String, String> vars = ctx.variables();
        Map<String, String> types = new LinkedHashMap<>();
        if (option != null) {
            for (SaveVarDef d : option.saveVars()) types.put(d.getName(), String.valueOf(d.getType()));
        }
        varItems.clear();
        for (Map.Entry<String, String> e : vars.entrySet()) {
            varItems.add(new VarRow(e.getKey(), e.getValue(), types.getOrDefault(e.getKey(), "运行时")));
        }
        // 打开开关时把扩展变量（只读）追加在同一张表里 —— 点按钮就能直接看到，不用去指令页翻日志
        if (showExtVars) {
            for (Map.Entry<String, String> e : ConsoleRegistry.envVars().entrySet()) {
                varItems.add(new VarRow(e.getKey(), e.getValue(), "扩展·只读", true));
            }
        }
    }

    /**
     * 「显示 / 隐藏扩展变量」。
     * <p>以前这个按钮只把内容打到<b>指令页</b>的日志里，用户在变量页点击自然"看着没反应"；
     * 现在直接把扩展变量作为<b>只读行</b>列进变量表（类型列标「扩展·只读」，双击也改不动）。</p>
     */
    private void toggleExtVars() {
        showExtVars = !showExtVars;
        if (showEnvButton != null) showEnvButton.setText(showExtVars ? "🔌 隐藏扩展变量" : "🔌 显示扩展变量");
        refreshVars();
        int n = ConsoleRegistry.envVars().size();
        println(showExtVars
                ? "已在变量表里显示 " + n + " 个扩展变量（类型列「扩展·只读」，不可编辑）"
                : "已隐藏扩展变量（地图自己的变量仍在表里）");
        status.setText(showExtVars
                ? "变量表 " + varItems.size() + " 行（含 " + n + " 个扩展变量）"
                : "场景 " + sceneItems.size() + " 个");
    }

    private void refreshSaves() {
        saveItems.clear();
        saveItems.addAll(ctx.saves());
    }

    private void printEnvVars() {
        Map<String, String> env = ConsoleRegistry.envVars();
        println("扩展变量（由插件/宿主通过 ConsoleRegistry.registerEnvProvider 注册的只读信息，"
                + "不是操作系统环境变量）：");
        if (env.isEmpty()) {
            println("  （当前一个都没有。外部插件想挂自己的状态：ConsoleRegistry.registerEnvProvider(\"分组\", 提供者)）");
            return;
        }
        println("  共 " + env.size() + " 项：");
        for (Map.Entry<String, String> e : env.entrySet()) println("  " + e.getKey() + " = " + e.getValue());
    }

    private void jumpToSelectedScene() {
        String sel = sceneList.getSelectionModel().getSelectedItem();
        if (sel == null) { println("[跳转失败] 先在左侧选一个场景"); return; }
        boolean ok = ctx.gotoScene(sel);
        println((ok ? "[已跳转] " : "[跳转失败] ") + sel);
        refreshScenes();
        refreshVars();
    }

    private String slotName() {
        String typed = saveName.getText() == null ? "" : saveName.getText().trim();
        if (!typed.isEmpty()) return typed;
        String sel = saveList.getSelectionModel().getSelectedItem();
        return sel == null ? "" : sel;
    }

    // =====================================================================
    // 指令
    // =====================================================================

    private void runInput() {
        String raw = input.getText();
        if (raw == null || raw.isBlank()) return;
        input.clear();
        history.add(raw);
        historyPos = history.size();
        println("› " + raw);
        try {
            execute(raw.trim());
        } catch (RuntimeException ex) {
            println("[指令异常] " + ex);
        }
    }

    /** 执行一行指令（公开：探针/自动化也能直接喂指令） */
    public void execute(String line) {
        List<String> parts = tokenize(line);
        if (parts.isEmpty()) return;
        String cmd = parts.get(0).toLowerCase(Locale.ROOT);
        List<String> args = parts.subList(1, parts.size());

        // 先看外部注册的指令（插件可以覆盖同名自带指令）
        ConsoleRegistry.Command ext = ConsoleRegistry.command(cmd);
        if (ext != null) {
            String r = ext.run(ctx, args);
            if (r != null && !r.isBlank()) println(r);
            return;
        }

        switch (cmd) {
            case "help", "?" -> printHelp();
            case "scenes", "ls" -> {
                println("场景共 " + ctx.scenes().size() + " 个" + hintCurrent());
                for (String s : ctx.scenes()) println("  " + (s.equals(ctx.currentScene()) ? "* " : "  ") + s);
            }
            case "goto", "jump" -> {
                if (args.isEmpty()) { println("用法：goto <场景名>"); return; }
                String name = String.join(" ", args);
                println(ctx.gotoScene(name) ? "[已跳转] " + name : "[跳转失败] 没有这个场景：" + name);
                refreshScenes();
                refreshVars();
            }
            case "vars" -> {
                Map<String, String> vars = ctx.variables();
                println("变量共 " + vars.size() + " 个：");
                for (Map.Entry<String, String> e : vars.entrySet()) println("  " + e.getKey() + " = " + e.getValue());
            }
            case "set" -> {
                if (args.size() < 2) { println("用法：set <变量名> <值>"); return; }
                String name = args.get(0);
                String value = String.join(" ", args.subList(1, args.size()));
                ctx.setVariable(name, value);
                int n = ctx.refreshAfterVariableChange();
                println("[已设置] " + name + " = " + value + "　（重绘了 " + n + " 个引用变量的节点）");
                if (n == 0) {
                    println("  提示：这张地图的节点不是靠文本里的 @var(...) 驱动的。"
                            + "若它是用槽把变量映射到节点（如「点灯 | set | 亮灯11_1 | visible | value=@var(灯1亮)」），"
                            + "再用 emit <信号名> 跑一遍那条槽即可，例如：emit 点灯");
                }
                refreshVars();
            }
            case "emit" -> {
                if (args.isEmpty()) { println("用法：emit <信号名> [参数=值 …]（跑地图自己的槽，用来触发变量→节点的映射）"); return; }
                String signal = args.get(0);
                Map<String, Object> params = new LinkedHashMap<>();
                for (String kv : args.subList(1, args.size())) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) params.put(kv.substring(0, eq), kv.substring(eq + 1));
                }
                boolean ok = ctx.emitSignal(signal, params);
                println(ok ? "[已发信号] " + signal + (params.isEmpty() ? "" : " " + params)
                           : "[发信号失败] " + signal);
                refreshVars();
            }
            case "env" -> printEnvVars();
            case "saves" -> {
                List<String> saves = ctx.saves();
                println("已有存档 " + saves.size() + " 个：");
                for (String s : saves) println("  " + s);
                refreshSaves();
            }
            case "save" -> {
                if (args.isEmpty()) { println("用法：save <存档名>"); return; }
                println(ctx.save(args.get(0)) ? "[存档成功] " + args.get(0) : "[存档失败] " + args.get(0));
                refreshSaves();
            }
            case "load" -> {
                if (args.isEmpty()) { println("用法：load <存档名>"); return; }
                boolean ok = ctx.load(args.get(0));
                println(ok ? "[读档成功] " + args.get(0) : "[读档失败] " + args.get(0));
                if (ok) { refreshVars(); refreshScenes(); }
            }
            case "plugin" -> {
                if (args.isEmpty()) { println("用法：plugin <插件id> [参数…]（插件可用 ConsoleRegistry.register 注册自己的指令）"); return; }
                String id = args.get(0);
                List<String> rest = args.subList(1, args.size());
                String r = ctx.invokePlugin(id, rest);
                println("[插件 " + id + "] " + (r == null || r.isBlank() ? "(无返回值)" : r));
            }
            case "clear", "cls" -> log.clear();
            case "close", "exit", "quit" -> close();
            default -> println("[未知指令] " + cmd + "（输入 help 查看全部指令）");
        }
    }

    private String hintCurrent() {
        String c = ctx.currentScene();
        return c == null ? "" : "　当前：" + c;
    }

    private void printHelp() {
        println("自带指令：");
        println("  help                    显示这份帮助");
        println("  scenes                  列出全部场景（* 为当前场景）");
        println("  goto <场景名>           跳转到指定场景");
        println("  vars                    列出当前所有变量");
        println("  set <变量名> <值>       修改变量（等效于在「变量」页双击改；改完会自动重绘引用变量的节点）");
        println("  emit <信号名> [参数=值…] 手动发一个信号，跑地图自己的槽"
                + "（变量→节点的映射常用槽实现，例如 emit 点灯）");
        println("  env                     列出插件/宿主注册的扩展变量（只读）");
        println("  saves                   列出已有存档");
        println("  save <存档名>           存档");
        println("  load <存档名>           读档");
        println("  plugin <插件id> [参数…] 调用外部插件");
        println("  clear / close           清屏 / 关闭控制台");
        List<ConsoleRegistry.Command> ext = ConsoleRegistry.commands();
        if (!ext.isEmpty()) {
            println("外部注册的指令（插件）：");
            for (ConsoleRegistry.Command c : ext) println("  " + pad(c.name()) + " " + c.help());
        }
    }

    private static String pad(String s) {
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        while (sb.length() < 24) sb.append(' ');
        return sb.toString();
    }

    /** 支持引号的简单分词 */
    private static List<String> tokenize(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { quoted = !quoted; continue; }
            if (!quoted && Character.isWhitespace(ch)) {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
                continue;
            }
            cur.append(ch);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // =====================================================================
    // 开关 / 输出
    // =====================================================================

    /**
     * 关掉控制台。
     *
     * <p>注意：这里<b>只隐藏、不把节点从父节点摘掉</b>。以前 close() 会
     * {@code parent.getChildren().remove(this)}，而 {@code ReaderView.showConsole()}
     * 又只在 {@code console == null} 时才 add —— 结果第二次打开时对象还在、但已经不在场景图里，
     * 表现就是"控制台关掉后打不开了"。</p>
     */
    public void close() {
        setVisible(false);
        setManaged(false);
    }

    /** 重新打开前刷新一遍数据（期间可能换过场景、改过变量、存过档） */
    public void refreshAll() {
        refreshScenes();
        refreshVars();
        refreshSaves();
    }

    /** 往日志追加一行（任何线程调用都安全） */
    public void println(String line) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> println(line));
            return;
        }
        log.appendText(line + "\n");
        log.positionCaret(log.getText().length());
    }

    /** 焦点给输入框（打开后可以直接敲指令） */
    public void focusInput() {
        Platform.runLater(input::requestFocus);
    }
}
