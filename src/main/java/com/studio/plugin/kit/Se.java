package com.studio.plugin.kit;

import com.studio.plugin.GamePlugin;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 小游戏逐动作音效（{@link GamePlugin#PARAM_SE} 的便捷入口）。
 *
 * <p>引擎在嵌入小游戏时调用 {@link #attach(Map)} 挂上通道；游戏在动作发生处调
 * {@link #play(String)} 即可（如 {@code Se.play("se_snake_eat")}）。未挂通道时静默，
 * 因此纯逻辑类与单测不受影响。</p>
 *
 * <p>为什么用静态：小游戏由引擎在"嵌入时"挂通道、收起时摘掉，同一时刻只会有一个小游戏在跑；
 * 这样各游戏不必为了音效改自己的构造与接口（改动面最小、回归风险最低）。</p>
 */
public final class Se {

    private static Consumer<String> sink;

    private Se() { }

    /** 引擎在嵌入小游戏时调用 */
    @SuppressWarnings("unchecked")
    public static void attach(Map<String, Object> params) {
        Object s = params == null ? null : params.get(GamePlugin.PARAM_SE);
        sink = (s instanceof Consumer) ? (Consumer<String>) s : null;
    }

    /** 引擎在小游戏收起时调用 */
    public static void detach() {
        sink = null;
    }

    /** 播放一条音效（id 形如 {@code se_snake_eat}）；无通道或文件缺失时静默 */
    public static void play(String id) {
        if (sink != null && id != null && !id.isBlank()) {
            sink.accept(id);
        }
    }

    public static boolean ready() {
        return sink != null;
    }
}
