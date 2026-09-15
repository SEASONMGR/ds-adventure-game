package com.studio.reader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 播放器<b>控制台</b>的扩展登记处（对外预留的接口）。
 *
 * <p>三类扩展点：</p>
 * <ol>
 *   <li><b>指令</b>：{@link #register(Command)} —— 自带插件 / 外部插件 / 逻辑层都可以注册自己的命令，
 *       控制台里输入 {@code 名字 参数…} 就会调用它；</li>
 *   <li><b>环境变量</b>：{@link #registerEnvProvider(String, Supplier)} —— 把"引擎/插件/宿主环境"的信息
 *       （版本、当前平台、正在播放的 BGM、插件状态……）以只读变量的形式挂进控制台，
 *       既能在「变量」页看，也能在指令里用 {@code @env(名字)} 取；</li>
 *   <li><b>调用外部插件</b>：见 {@link Context#invokePlugin(String, List)} ——
 *       指令里统一用 {@code plugin <插件id> 参数…} 调用，插件自己不用关心控制台。</li>
 * </ol>
 *
 * <p>所有注册都是线程安全的（播放器与插件线程都可能注册）。</p>
 */
public final class ConsoleRegistry {

    /** 控制台给指令提供的宿主能力（由 {@link ReaderView} 实现） */
    public interface Context {
        /** 当前场景名 */
        String currentScene();

        /** 全部场景名（按工程顺序） */
        List<String> scenes();

        /** 跳转到某个场景；成功返回 true */
        boolean gotoScene(String name);

        /** 当前变量快照（名称 → 值） */
        Map<String, String> variables();

        /** 修改变量（会写回引擎变量表） */
        void setVariable(String name, String value);

        /**
         * 变量改完之后调用：让引擎把当前场景里「引用变量」的节点重绘一遍。
         *
         * @return 实际重绘的节点数（0 表示这张地图的节点不是靠 @var 表达式驱动的）
         */
        int refreshAfterVariableChange();

        /**
         * 往当前场景发一个信号，跑地图自己定义/订阅的槽。
         * <p>很多地图是用槽把变量映射到节点的（{@code slot = 点灯 | set | 亮灯11_1 | visible | value=@var(灯1亮)}），
         * 这种"改变量"不会自动生效，需要发一下对应的信号。</p>
         *
         * @return 是否发出
         */
        boolean emitSignal(String name, Map<String, Object> params);

        /** 已有存档名列表 */
        List<String> saves();

        /** 存档（返回是否成功） */
        boolean save(String slot);

        /** 读档（返回是否成功） */
        boolean load(String slot);

        /** 调用一个槽插件：{@code idOrClass} + 参数列表（与槽的 target/arg/extra 对齐） */
        String invokePlugin(String idOrClass, List<String> args);

        /** 往控制台追加一行（异步安全） */
        void print(String line);
    }

    /** 一条控制台指令 */
    public interface Command {
        /** 指令名（不含空格；大小写不敏感） */
        String name();

        /** 一行帮助 */
        String help();

        /** 执行；返回值会打印到控制台（可返回 null） */
        String run(Context ctx, List<String> args);
    }

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, Supplier<Map<String, String>>> ENV_PROVIDERS = new LinkedHashMap<>();

    private ConsoleRegistry() { }

    // ---------------- 指令 ----------------

    /** 注册（同名会覆盖，方便插件热重载） */
    public static synchronized void register(Command command) {
        if (command == null || command.name() == null || command.name().isBlank()) return;
        COMMANDS.put(command.name().trim().toLowerCase(java.util.Locale.ROOT), command);
    }

    /** 注销（插件 {@code onDetach} 时可调用） */
    public static synchronized boolean unregister(String name) {
        return name != null && COMMANDS.remove(name.trim().toLowerCase(java.util.Locale.ROOT)) != null;
    }

    /** 当前所有指令（按注册顺序） */
    public static synchronized List<Command> commands() {
        return new ArrayList<>(COMMANDS.values());
    }

    /** 按名字找指令（没有返回 null） */
    public static synchronized Command command(String name) {
        return name == null ? null : COMMANDS.get(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    // ---------------- 环境变量 ----------------

    /**
     * 注册一批"环境变量"（只读）。
     *
     * @param group   分组名（同一个 group 再注册会覆盖，便于刷新）
     * @param provider 每次读取时调用，返回 名称 → 值
     */
    public static synchronized void registerEnvProvider(String group, Supplier<Map<String, String>> provider) {
        if (group == null || group.isBlank() || provider == null) return;
        ENV_PROVIDERS.put(group.trim(), provider);
    }

    public static synchronized void unregisterEnvProvider(String group) {
        if (group != null) ENV_PROVIDERS.remove(group.trim());
    }

    /** 汇总所有环境变量（分组名作前缀：{@code 分组.名称}） */
    public static synchronized Map<String, String> envVars() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<Map<String, String>>> e : ENV_PROVIDERS.entrySet()) {
            Map<String, String> vars;
            try {
                vars = e.getValue().get();
            } catch (RuntimeException ex) {
                out.put(e.getKey() + ".<错误>", String.valueOf(ex.getMessage()));
                continue;
            }
            if (vars == null) continue;
            for (Map.Entry<String, String> v : vars.entrySet()) {
                out.put(e.getKey().isEmpty() ? v.getKey() : e.getKey() + "." + v.getKey(), v.getValue());
            }
        }
        return out;
    }
}
