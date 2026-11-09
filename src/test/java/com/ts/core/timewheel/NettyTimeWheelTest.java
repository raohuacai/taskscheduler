package com.ts.core.timewheel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NettyTimeWheel}: lifecycle guard, add/remove bookkeeping,
 * and stop cleanup. Uses real (short-lived) timers.
 */
class NettyTimeWheelTest {

    private NettyTimeWheel wheel;

    @AfterEach
    void tearDown() {
        if (wheel != null) {
            try {
                wheel.stop();
            } catch (Exception ignored) {
                // timer may already be stopped / never started
            }
        }
    }

    @Test
    void addTask_beforeStart_shouldThrow() {
        wheel = new NettyTimeWheel();
        assertThrows(IllegalStateException.class,
                () -> wheel.addTask(() -> { }, 1000, TimeUnit.MILLISECONDS));
    }

    @Test
    void addTask_afterStart_shouldRegisterTask() {
        wheel = new NettyTimeWheel();
        wheel.start();

        String taskId = wheel.addTask(() -> { }, 60_000, TimeUnit.MILLISECONDS);

        assertNotNull(taskId);
        assertEquals(1, wheel.getStats().get("totalActiveTasks"));
    }

    @Test
    void removeTask_shouldRemoveTask() {
        wheel = new NettyTimeWheel();
        wheel.start();
        String taskId = wheel.addTask(() -> { }, 60_000, TimeUnit.MILLISECONDS);

        assertTrue(wheel.removeTask(taskId));
        assertEquals(0, wheel.getStats().get("totalActiveTasks"));

        // removing twice is a no-op
        assertFalse(wheel.removeTask(taskId));
    }

    @Test
    void removeTask_unknownId_returnsFalse() {
        wheel = new NettyTimeWheel();
        wheel.start();
        assertFalse(wheel.removeTask("no-such-task"));
    }

    @Test
    void stop_shouldClearAllTasks() {
        wheel = new NettyTimeWheel();
        wheel.start();
        wheel.addTask(() -> { }, 60_000, TimeUnit.MILLISECONDS);
        wheel.addTask(() -> { }, 60_000, TimeUnit.MILLISECONDS);

        wheel.stop();

        assertEquals(0, wheel.getStats().get("totalActiveTasks"));
        assertEquals(false, wheel.getStats().get("running"));
    }

    @Test
    void start_twice_shouldBeIdempotent() {
        wheel = new NettyTimeWheel();
        wheel.start();
        // second start is a no-op, does not throw
        assertDoesNotThrow(wheel::start);
        assertTrue((boolean) wheel.getStats().get("running"));
    }
}
