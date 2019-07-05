package edu.washington.cs.mut.testrunner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.runner.Result;

/**
 * Runs, in isolation, all test methods defined in the provided intput file. The
 * input file must follow the following format:
 *   <test class name>::<test method name>
 *
 * Usage:
 *   java edu.washington.cs.mut.testrunner.MultipleTestRunner \
 *     <test methods file path>
 */
public class MultipleTestRunner {

    private static void usageAndExit() {
        System.err.println("Usage: java " + TestRunner.class.getCanonicalName() +
          " <test methods file path>");
        System.exit(1);
    }

    public static void main(String ... args) throws Exception {
        if (args.length != 1) {
            usageAndExit();
        }

        final File testMethodsFile = new File(args[0]);
        if (!testMethodsFile.exists() || !testMethodsFile.canRead()) {
            throw new RuntimeException(testMethodsFile + " does not exist or cannot be read");
        }

        // Backup defined system properties
        final Properties backupProperties = (Properties) System.getProperties().clone();
        // Get classpath
        final URL[] classpathURLs = ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();

        try (BufferedReader br = new BufferedReader(new FileReader(testMethodsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher matcher = Pattern.compile("(?<className>[^:]+)(::(?<methodName>[^:]+))").matcher(line);
                if (!matcher.matches()) {
                    usageAndExit();
                }
                System.err.println("Running: " + line);

                String testClassName  = matcher.group("className");
                String testMethodName = matcher.group("methodName");

                // Run each unit test in isolation
                TestTask testTask = new TestTask(classpathURLs, testClassName, testMethodName);
                Result result = TestRunner.run(testTask);

                // "Release/Free" object
                testTask = null;

                // Restore system properties
                System.setProperties((Properties) backupProperties.clone());
            }
        }

        // Exit and indicate success. Use System.exit in case any waiting
        // threads are preventing a proper JVM shutdown.
        System.exit(0);
    }
}
