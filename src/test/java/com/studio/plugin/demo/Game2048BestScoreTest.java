package com.studio.plugin.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2048 历史最高分回归（该 UI 细节移植自 PR #14 的 Main.java）。
 */
class Game2048BestScoreTest {

    @Test
    void keepsZeroWhenNoScore() {
        assertEquals(0L, Game2048Plugin.nextBest(0L, 0L));
    }

    @Test
    void recordsFirstScore() {
        assertEquals(128L, Game2048Plugin.nextBest(0L, 128L));
    }

    @Test
    void keepsHigherBest() {
        assertEquals(256L, Game2048Plugin.nextBest(256L, 128L));
    }

    @Test
    void refreshesWhenBeaten() {
        assertEquals(512L, Game2048Plugin.nextBest(256L, 512L));
    }

    @Test
    void toleratesNegativeInput() {
        assertEquals(16L, Game2048Plugin.nextBest(-8L, 16L));
    }
}
