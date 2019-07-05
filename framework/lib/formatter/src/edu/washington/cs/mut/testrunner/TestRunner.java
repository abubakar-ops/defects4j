package edu.washington.cs.mut.testrunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import org.junit.runner.Result;

public class TestRunner {

    public static Result run(final TestTask testTask) {
        FutureTask<Result> task = new FutureTask<Result>(testTask);
        ThreadGroup group = new ThreadGroup("[thread group for " + testTask.toString() + "]");
        Thread thread = new Thread(group, task, "[thread for " + testTask.toString() + "]");
        thread.start();
        try {
            return task.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            killThreadGroup(group);
            thread.interrupt();
            group.destroy();
        }
        return null;
    }

    /**
     * Iterates over all threads in provided group and interrupts each thread.
     */
    private static void killThreadGroup(final ThreadGroup group) {
        Thread[] activeThreads = new Thread[group.activeCount()];
        for (int i = 0; i < group.enumerate(activeThreads); ++i) {
            activeThreads[i].interrupt();
        }
    }
}
