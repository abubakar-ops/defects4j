package edu.washington.cs.mut.testrunner;

import java.io.PrintStream;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import edu.washington.cs.mut.testlistener.Listener;
import edu.washington.cs.mut.util.IsolatingClassLoader;

public class TestTask implements Callable<Result> {

    private static final AtomicInteger INC = new AtomicInteger();

    private final int id;

    private final URL[] classPathURLs;

    private final PrintStream loadedClassesPrintStream;

    private final String testClassName;

    private final String testMethodName;

    /**
     * Constructor for task to run a test method.
     */
    protected TestTask(final URL[] classPathURLs, final PrintStream loadedClassesPrintStream,
        final String testClassName, final String testMethodName) {
        this.id = INC.incrementAndGet();
        this.classPathURLs  = classPathURLs;
        this.loadedClassesPrintStream = loadedClassesPrintStream;
        this.testClassName  = testClassName;
        this.testMethodName = testMethodName;
    }

    /**
     * Callable method to run a test method and return result.
     *
     * {@inheritDoc}
     */
    @Override
    public Result call() throws Exception {
        // Create a new isolated classloader with the same classpath as the current one
        final IsolatingClassLoader classLoader = new IsolatingClassLoader(this.classPathURLs,
            Thread.currentThread().getContextClassLoader());

        // Make the isolating classloader the thread's new classloader. This method is called in a
        // dedicated thread that ends right after this method returns, so there is no need to restore
        // the old/original classloader when it finishes.
        Thread.currentThread().setContextClassLoader(classLoader);

        Class<?> clazz = Class.forName(this.testClassName, false, classLoader);

        // Get unit test to run
        Request request = Request.method(clazz, this.testMethodName);

        // Run unit test
        JUnitCore runner = new JUnitCore();
        Listener listener = (Listener) new edu.washington.cs.mut.testlistener.JUnitListener();
        runner.addListener(listener);
        Result result = runner.run(request);

        // Close classloader object and return test result
        classLoader.close();

        // Get loaded classes
        Set<String> loadedClasses = classLoader.getLoadedClasses();
        if (!loadedClasses.isEmpty() && !listener.isATestClassWithProblems()) {
            StringBuilder loadedClassesStr = new StringBuilder();
            loadedClassesStr.append(this.testClassName);
            loadedClassesStr.append(Listener.TEST_CLASS_NAME_SEPARATOR);
            loadedClassesStr.append(this.testMethodName);
            loadedClassesStr.append("#");
            for (String loadedClass : loadedClasses) {
                loadedClassesStr.append(loadedClass);
                loadedClassesStr.append(",");
            }
            // org.foo.TestBar::test1#java.lang.Object,java.util.Set,org.foo.TestBar,org.foo.Bar,...
            this.loadedClassesPrintStream.println(loadedClassesStr.toString());
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[" + this.id + "] # " + this.testClassName + "::" + this.testMethodName;
    }
}
