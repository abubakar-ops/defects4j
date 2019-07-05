package edu.washington.cs.mut.testlistener;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.runner.notification.RunListener;

/**
 * Test listener.
 */
public class Listener extends RunListener {

    public static final String TEST_FAILURE_PREFIX = "--- ";

    public static final String TEST_CLASS_NAME_SEPARATOR = "::";

    private PrintStream failingTestsPrintStream;

    private PrintStream allTestsPrintStream;

    {
      try {
          this.failingTestsPrintStream = new PrintStream(new FileOutputStream(System.getProperty("OUTFILE", "failing-tests.txt"), true), true);
          this.allTestsPrintStream = new PrintStream(new FileOutputStream(System.getProperty("ALLTESTS", "all_tests"), true), true);
      } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
      }
    }

    /**
     * Called before any tests have been run.
     */
    public final void onRunStart() {
        // empty
    }

    /**
     * Called when all tests have finished.
     */
    public final void onRunFinish() {
        // empty
    }

    /**
     * Called when an atomic test is about to be started.
     */
    public final void onTestStart(final String testName) {
        this.allTestsPrintStream.println(testName);
    }

    /**
     * Called when an atomic test has finished (whether the test successes or fails).
     *
     * @param testName
     */
    public final void onTestFinish(final String testName) {
        // empty
    }

    /**
     * Called when an atomic test fails.
     */
    public final void onTestFailure(final String trace) {
        if (trace == null) {
            return;
        }
        this.failingTestsPrintStream.println(trace);
    }

    /**
     * Called when a test will not be run, generally because a test method is annotated with @Ignore
     * or similar annotation.
     */
    public final void onTestSkipped() {
        // empty
    }

    /**
     * Converts the stack trace of a throwable exception to string.
     *
     * @param exception The exception thrown.
     * @return A string of the stack trace of a throwable exception.
     */
    protected final String throwableToString(Throwable exception) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        exception.printStackTrace(writer);
        return stringWriter.toString();
    }
}
