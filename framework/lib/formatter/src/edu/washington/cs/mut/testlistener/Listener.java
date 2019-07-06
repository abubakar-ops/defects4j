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
public abstract class Listener extends RunListener {

    public static final String TEST_FAILURE_PREFIX = "--- ";

    public static final String TEST_CLASS_NAME_SEPARATOR = "::";

    private PrintStream failingTestsPrintStream;

    private PrintStream allTestsPrintStream;

    private String testClassName = null;

    private boolean alreadyPrinted = true;

    private boolean testClassWithProblems = false;

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
    public final void onRunStart(final String testClassName) {
        this.testClassName = testClassName;
        this.alreadyPrinted = false;
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

    public final boolean isATestClassWithProblems() {
        return this.testClassWithProblems;
    }

    protected String handleFailure(String testFullName, Throwable throwable) {
        String className  = testFullName.split(Listener.TEST_CLASS_NAME_SEPARATOR)[0];
        String methodName = testFullName.split(Listener.TEST_CLASS_NAME_SEPARATOR)[1];

        if ("warning".equals(methodName) || "initializationError".equals(methodName)) {
            return this.failClass(throwable); // there is an issue with the class, not the method
        } else if (null != methodName && null != className) {
            if (this.isJunit4InitFail(throwable)) {
                return this.failClass(throwable);
            } else {
                // normal case
                return new String(Listener.TEST_FAILURE_PREFIX +
                    testFullName + System.lineSeparator() +
                    this.throwableToString(throwable));
            }
        } else {
            this.testClassWithProblems = true;
            return new String(Listener.TEST_FAILURE_PREFIX + "broken test input " +
                testFullName + System.lineSeparator() +
                this.throwableToString(throwable));
        }
    }

    protected String failClass(Throwable throwable) {
        this.testClassWithProblems = true;
        if (!this.alreadyPrinted) {
            this.alreadyPrinted = true;
            return new String(Listener.TEST_FAILURE_PREFIX + this.testClassName +
                System.lineSeparator() +
                this.throwableToString(throwable));
        }
        return null;
    }

    private boolean isJunit4InitFail(Throwable throwable) {
        for (StackTraceElement stackTraceElement: throwable.getStackTrace()) {
            if ("createTest".equals(stackTraceElement.getMethodName())) {
                return true;
            }
        }
        return false;
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
