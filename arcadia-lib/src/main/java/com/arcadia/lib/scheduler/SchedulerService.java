package com.arcadia.lib.scheduler;

import com.arcadia.lib.ArcadiaLib;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tick-based task scheduler for all Arcadia mods.
 * Replaces raw tick counters scattered across mods with a centralized system.
 *
 * <p>All tasks run on the main server thread during ServerTickEvent.
 * For async work, use {@link com.arcadia.lib.data.DatabaseManager#executeAsync}.</p>
 *
 * <p>Usage:
 * <pre>
 * // Run once after 60 ticks (3 seconds)
 * SchedulerService.delayed(60, () -> player.sendSystemMessage(...));
 *
 * // Run every 20 ticks (1 second), forever
 * int taskId = SchedulerService.repeating(20, () -> checkPlayerStatus());
 *
 * // Cancel a repeating task
 * SchedulerService.cancel(taskId);
 * </pre></p>
 */
@EventBusSubscriber(modid = ArcadiaLib.MOD_ID)
public final class SchedulerService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Queue<ScheduledTask> tasks = new ConcurrentLinkedQueue<>();
    private static int nextId = 1;
    private static long currentTick = 0;

    private SchedulerService() {}

    // ── Scheduling API ──────────────────────────────────────────────────────

    /**
     * Schedules a one-shot task to run after a delay.
     * @param delayTicks ticks to wait before execution (20 ticks = 1 second)
     * @param task       the task to execute on the main server thread
     * @return task ID (can be used with cancel())
     */
    public static int delayed(int delayTicks, Runnable task) {
        int id = nextId++;
        tasks.add(new ScheduledTask(id, currentTick + delayTicks, 0, task, false));
        return id;
    }

    /**
     * Schedules a repeating task that runs every N ticks.
     * @param intervalTicks interval between executions
     * @param task          the task to execute on the main server thread
     * @return task ID (use with cancel())
     */
    public static int repeating(int intervalTicks, Runnable task) {
        int id = nextId++;
        tasks.add(new ScheduledTask(id, currentTick + intervalTicks, intervalTicks, task, true));
        return id;
    }

    /**
     * Schedules a repeating task with an initial delay before the first execution.
     * @param initialDelay  ticks before first execution
     * @param intervalTicks interval between subsequent executions
     * @param task          the task to execute
     * @return task ID
     */
    public static int repeatingDelayed(int initialDelay, int intervalTicks, Runnable task) {
        int id = nextId++;
        tasks.add(new ScheduledTask(id, currentTick + initialDelay, intervalTicks, task, true));
        return id;
    }

    /**
     * Runs a task on the next server tick (main thread).
     * Useful for scheduling work from async callbacks.
     */
    public static void runNextTick(Runnable task) {
        delayed(1, task);
    }

    /**
     * Cancels a scheduled task by ID.
     * @return true if the task was found and cancelled
     */
    public static boolean cancel(int taskId) {
        return tasks.removeIf(t -> t.id == taskId);
    }

    /** Cancels all scheduled tasks (called on server shutdown). */
    public static void cancelAll() {
        tasks.clear();
        currentTick = 0;
        nextId = 1;
    }

    /** Returns the current server tick count. */
    public static long getCurrentTick() {
        return currentTick;
    }

    // ── Tick handler ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        currentTick++;

        Iterator<ScheduledTask> it = tasks.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            if (currentTick >= task.nextRun) {
                try {
                    task.action.run();
                } catch (Exception e) {
                    LOGGER.error("[ArcadiaLib] Scheduled task {} failed", task.id, e);
                }

                if (task.repeating) {
                    task.nextRun = currentTick + task.interval;
                } else {
                    it.remove();
                }
            }
        }
    }

    // ── Internal task record ────────────────────────────────────────────────

    private static final class ScheduledTask {
        final int id;
        long nextRun;
        final int interval;
        final Runnable action;
        final boolean repeating;

        ScheduledTask(int id, long nextRun, int interval, Runnable action, boolean repeating) {
            this.id = id;
            this.nextRun = nextRun;
            this.interval = interval;
            this.action = action;
            this.repeating = repeating;
        }
    }
}
