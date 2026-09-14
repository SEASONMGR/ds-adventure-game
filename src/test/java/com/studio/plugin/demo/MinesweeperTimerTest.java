package com.studio.plugin.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 扫雷用时格式化回归（计时器 UI 细节移植自 PR #12 的 Main.java）。
 */
class MinesweeperTimerTest {

    @Test
    void formatsZero() {
        assertEquals("00:00", MinesweeperPlugin.formatElapsed(0));
    }

    @Test
    void formatsSingleDigitSeconds() {
        assertEquals("00:09", MinesweeperPlugin.formatElapsed(9));
    }

    @Test
    void formatsMinuteBoundary() {
        assertEquals("01:00", MinesweeperPlugin.formatElapsed(60));
    }

    @Test
    void formatsLongGame() {
        assertEquals("61:01", MinesweeperPlugin.formatElapsed(3661));
    }

    @Test
    void clampsNegativeInput() {
        assertEquals("00:00", MinesweeperPlugin.formatElapsed(-5));
    }
}
