package edu.washington.cs.mut.testfinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javassist.ClassPool;
import javassist.CtClass;
import org.apache.commons.io.FileUtils;

import edu.washington.cs.mut.util.WildcardMatcher;

/**
 * Finds all test methods in a provided test directory that match a provided
 * pattern. By default all test methods in a test directory are considered. It
 * writes the name of each test method to a provided file (one test method name
 * per row). The name of each test method follows the following format:
 *   <test class name>::<test method name>
 *
 * Usage:
 *   java edu.washington.cs.mut.testfinder.TestFinder \
 *     <output file path> \
 *     <test classes directory path>
 *     [patterns separated by ',', '*::*' by default]"
 *
 */
public class TestFinder {

    private static final ClassPool classPool = ClassPool.getDefault();

    private static void usageAndExit() {
        System.err.println("Usage: java " + TestFinder.class.getCanonicalName() +
          " <output file path>" +
          " <test classes directory path>" +
          " [patterns separated by ',', '*::*' by default]");
        System.exit(1);
    }

    public static void main(String ... args) throws Exception {
        if (args.length != 2 && args.length != 3) {
            usageAndExit();
        }

        final PrintStream outputFile = new PrintStream(new FileOutputStream(args[0], false), true);

        // Check whether directory with test classes exist
        final String testsDirPath = args[1];
        final File testsDir = new File(testsDirPath);
        if (!testsDir.exists() || !testsDir.isDirectory()) {
            System.err.println("[ERROR] '" + testsDirPath + "' does not exist or it is not a directory!");
            System.exit(1);
        }

        String patterns = "*::*";
        if (args.length == 3) {
            patterns = args[2];
        }

        // Pre-process the test matchers
        final List<WildcardMatcher> matchers = new ArrayList<WildcardMatcher>();
        for (String pattern : patterns.split(",")) {
            Matcher m = Pattern.compile("(?<className>[^:]+)?(::(?<methodName>[^:]+))?").matcher(pattern);
            if (!m.matches()) {
                usageAndExit();
            }

            String className  = m.group("className") == null ? "*" : m.group("className");
            String methodName = m.group("methodName") == null ? "*" : m.group("methodName");
            matchers.add(new WildcardMatcher(className + "::" + methodName));
        }

        // Find all .class files and for each one find the test method declared
        // in the test class
        for (File file : FileUtils.listFiles(testsDir, new String[] {"class"}, true)) {
            FileInputStream fin = new FileInputStream(file);
            CtClass ctClass = classPool.makeClassIfNew(fin);

            for (WildcardMatcher matcher : matchers) {
                for (String testMethod : JUnitTestFinder.find(matcher, ctClass)) {
                    outputFile.println(testMethod);
                }
            }

            ctClass.detach();
            fin.close();
        }

        // Exit and indicate success
        System.exit(0);
    }
}
