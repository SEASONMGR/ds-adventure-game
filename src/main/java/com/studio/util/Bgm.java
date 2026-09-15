package com.studio.util;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * BGM 解析与播放（用于"非剧本"场合：标题画面、小游戏、结局）。
 *
 * <p>曲库由美术侧交付：{@code assets/sounds/bgm/bgm_<类别>_<序号>.mp3} +
 * {@code bgm_map.json}（含用途 / 首选或备选 / 是否无缝循环 / 出处）。
 * 本项目不引 JSON 依赖，因此由 {@code tools/build_bgm_index.mjs} 把 JSON 压成扁平索引
 * {@code bgm_index.txt}，这里只读索引。</p>
 *
 * <p>选取规则：优先 {@code role=选} 的曲子，在同类里<b>随机</b>取一首
 * （美术侧的口径是"同类多首 = 冗余，随机播放"）；没有首选则全体随机。</p>
 *
 * <p>剧本内的 BGM（{@code @bgm} / 场景 {@code bgm:}）走引擎的音频通道，不走这里。</p>
 */
public final class Bgm {

    /** 一条曲目 */
    public record Track(String category, String file, String role, boolean loop, String source) {
        public String relPath() { return "assets/sounds/bgm/" + file; }
    }

    private static final String INDEX_RES = "assets/sounds/bgm/bgm_index.txt";
    private static final Random RANDOM = new Random();

    private static Map<String, List<Track>> index;
    private static MediaPlayer player;
    private static String currentCategory = "";
    private static String currentFile = "";

    private Bgm() { }

    // =====================================================================
    // 索引
    // =====================================================================

    /** 读取索引（首次调用时加载；读不到返回空表，调用方静默跳过） */
    public static synchronized Map<String, List<Track>> index() {
        if (index != null) return index;
        Map<String, List<Track>> m = new LinkedHashMap<>();
        try (InputStream in = Bgm.class.getClassLoader().getResourceAsStream(INDEX_RES)) {
            if (in == null) {
                Logs.warn("[Bgm] 找不到索引 " + INDEX_RES + "（请先跑 tools/build_bgm_index.mjs）");
                index = m;
                return m;
            }
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] p = s.split("\\|");
                if (p.length < 4) continue;
                Track t = new Track(p[0], p[1], p[2], "1".equals(p[3]), p.length > 4 ? p[4] : "");
                m.computeIfAbsent(t.category(), k -> new ArrayList<>()).add(t);
            }
        } catch (Exception e) {
            Logs.warn("[Bgm] 索引读取失败：" + e.getMessage());
        }
        index = m;
        return m;
    }

    /** 该类别下的全部曲目 */
    public static List<Track> tracks(String category) {
        return index().getOrDefault(category, List.of());
    }

    /** 该类别是否可用 */
    public static boolean has(String category) {
        return !tracks(category).isEmpty();
    }

    /** 全部类别（按索引顺序） */
    public static List<String> categories() {
        return new ArrayList<>(index().keySet());
    }

    /** 随机取一首：优先"选"，否则全体 */
    public static Track pick(String category) {
        List<Track> all = tracks(category);
        if (all.isEmpty()) return null;
        List<Track> preferred = new ArrayList<>();
        for (Track t : all) {
            if ("选".equals(t.role())) preferred.add(t);
        }
        List<Track> pool = preferred.isEmpty() ? all : preferred;
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /** 类别 + 随机曲目 → 相对路径（{@code assets/sounds/bgm/xxx.mp3}），不可用返回空串 */
    public static String relPath(String category) {
        Track t = pick(category);
        return t == null ? "" : t.relPath();
    }

    // =====================================================================
    // 独立播放（标题画面等；游戏内请用引擎的音频通道）
    // =====================================================================

    /** 播放某类别（随机取曲）；{@code forceLoop} 为真时即使曲目非无缝循环也循环播放 */
    public static synchronized void play(String category, double volume, boolean forceLoop) {
        Track t = pick(category);
        if (t == null) {
            Logs.info("[Bgm] 类别不可用，跳过：" + category);
            return;
        }
        File f = resolveFile(t.relPath());
        if (f == null || !f.isFile()) {
            Logs.info("[Bgm] 曲目文件不可用，跳过：" + t.relPath());
            return;
        }
        stop();
        try {
            MediaPlayer p = new MediaPlayer(new Media(f.toURI().toString()));
            boolean loop = forceLoop || t.loop();
            p.setCycleCount(loop ? MediaPlayer.INDEFINITE : 1);
            p.setVolume(Math.max(0, Math.min(1, volume)));
            p.setOnError(() -> Logs.warn("[Bgm] 播放失败 " + t.file() + "：" + p.getError()));
            p.play();
            player = p;
            currentCategory = category;
            currentFile = t.file();
            Logs.info("[Bgm] 播放 [" + category + "] " + t.file()
                    + "（role=" + t.role() + (loop ? " · 循环" : "") + " · 来源 " + t.source() + "）");
        } catch (RuntimeException e) {
            Logs.warn("[Bgm] 无法播放 " + t.file() + "：" + e.getMessage());
        }
    }

    /** 播放某类别（循环与否按索引） */
    public static void play(String category, double volume) {
        play(category, volume, false);
    }

    public static synchronized void stop() {
        if (player != null) {
            try {
                player.stop();
                player.dispose();
            } catch (RuntimeException ignored) {
                // 释放失败不影响后续
            }
            player = null;
        }
        currentCategory = "";
        currentFile = "";
    }

    public static String currentCategory() { return currentCategory; }

    public static String currentFile() { return currentFile; }

    public static boolean playing() { return player != null; }

    // =====================================================================
    // 文件解析（工作目录 → classpath；jar 内资源释放为临时文件，Media 不支持 jar:）
    // =====================================================================

    private static final Map<String, File> TEMP = new LinkedHashMap<>();

    /** 把 {@code assets/...} 解析成可交给 Media 的本地文件 */
    public static File resolveFile(String rel) {
        if (rel == null || rel.isBlank()) return null;
        File loose = new File(System.getProperty("user.dir"), rel);
        if (loose.isFile()) return loose;
        File cached = TEMP.get(rel);
        if (cached != null && cached.isFile()) return cached;
        try {
            URL url = Bgm.class.getClassLoader().getResource(rel);
            if (url == null) return null;
            if ("file".equalsIgnoreCase(url.getProtocol())) return new File(url.toURI());
            File tmp = File.createTempFile("dsa-bgm-", "-" + new File(rel).getName());
            tmp.deleteOnExit();
            try (InputStream in = url.openStream()) {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            TEMP.put(rel, tmp);
            return tmp;
        } catch (Exception e) {
            Logs.warn("[Bgm] 资源解析失败 " + rel + "：" + e.getMessage());
            return null;
        }
    }

    /** 按结局 id 推测 BGM 类别（bad_collapse / busy → bad；true / temp → ending；local → warm） */
    public static String categoryForEnding(String endingId) {
        if (endingId == null) return "";
        return switch (endingId.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "bad_collapse", "busy" -> "bad";
            case "true", "temp" -> "ending";
            case "local" -> "warm";
            default -> "";
        };
    }
}
