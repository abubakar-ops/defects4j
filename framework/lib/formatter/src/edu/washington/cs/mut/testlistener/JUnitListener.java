package edu.washington.cs.mut.testlistener;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * JUnit listener for the JUnit API.
 */
public final class JUnitListener extends Listener {

    @Override
    public void testRunStarted(final Description description) {
        super.onRunStart(description.getClassName());
    }

    @Override
    public void testRunFinished(final Result result) {
        super.onRunFinish();
    }

    @Override
    public void testStarted(final Description description) {
        super.onTestStart(this.getName(description));
    }

    @Override
    public void testFinished(final Description description) {
        super.onTestFinish(this.getName(description));
    }

    @Override
    public void testFailure(final Failure failure) {
        Description description = failure.getDescription();
        if (description.getMethodName() == null) {
            // if test is null it indicates an initialization error for the class
            super.onTestFailure(this.failClass(failure.getException()));
        } else {
            super.onTestFailure(this.handleFailure(this.getName(description), failure.getException()));
        }
    }

    @Override
    public void testAssumptionFailure(final Failure failure) {
        // an assumption failure is not propagated to org.junit.runner.Result anyways
    }

    @Override
    public void testIgnored(final Description description) {
        super.onTestSkipped();
    }

    private String getName(final Description description) {
        return description.getClassName() + Listener.TEST_CLASS_NAME_SEPARATOR
            + description.getMethodName();
    }
}
