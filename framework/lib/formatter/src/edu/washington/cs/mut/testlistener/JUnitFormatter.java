package edu.washington.cs.mut.testlistener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;

/**
 * JUnit listener for the Ant JUnit task.
 */
public class JUnitFormatter extends Listener implements JUnitResultFormatter {

    private String testClassName;

    private boolean alreadyPrinted = true;

    /**
     *
     */
    public JUnitFormatter() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startTest(Test test) {
        super.onTestFinish(this.getName(test));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endTest(Test test) {
        super.onTestFinish(this.getName(test));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startTestSuite(JUnitTest suite) throws BuildException {
        this.testClassName = suite.getName();
        this.alreadyPrinted = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endTestSuite(JUnitTest suite) throws BuildException {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFailure(Test test, AssertionFailedError assertionFailedError) {
        super.onTestFailure(this.handleFailure(test, assertionFailedError));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addError(Test test, Throwable throwable) {
        super.onTestFailure(this.handleFailure(test, throwable));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setOutput(OutputStream out) {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemError(String err) {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemOutput(String out) {
        // empty
    }

    private String getName(Test test) {
        String className = null;
        String methodName = null;

        {
          Pattern regexp = Pattern.compile("(.*)\\((.*)\\)");
          Matcher match = regexp.matcher(test.toString());
          if (match.matches()) {
              className = match.group(2);
              methodName = match.group(1);
          }
        }
        {
          // for some weird reason this format is used for Timeout in Junit4
          Pattern regexp = Pattern.compile("(.*):(.*)");
          Matcher match = regexp.matcher(test.toString());
          if (match.matches()) {
              className = match.group(1);
              methodName = match.group(2);
          }
        }

        return className + Listener.TEST_CLASS_NAME_SEPARATOR + methodName;
    }

    private String handleFailure(Test test, Throwable throwable) {
        if (test == null) { // if test is null it indicates an initialization error for the class
            return this.failClass(throwable);
        }

        String testFullName = this.getName(test);
        String className = testFullName.split(Listener.TEST_CLASS_NAME_SEPARATOR)[0];
        String methodName = testFullName.split(Listener.TEST_CLASS_NAME_SEPARATOR)[1];

        if ("warning".equals(methodName) || "initializationError".equals(methodName)) {
            return this.failClass(throwable); // there is an issue with the class, not the method.
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
            return new String(Listener.TEST_FAILURE_PREFIX + "broken test input " +
                test.toString() + System.lineSeparator() +
                this.throwableToString(throwable));
        }
    }

    private String failClass(Throwable throwable) {
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
}
