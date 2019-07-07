package edu.washington.cs.mut.testfinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javassist.ClassPool;
import javassist.CtClass;

import edu.washington.cs.mut.util.WildcardMatcher;

/**
 * Finds all test methods in a provided test directory that match any provided
 * pattern. It writes the name of each test method to a provided file (one test
 * method name per row). The name of each test method follows the following
 * format:
 *   <test class name>::<test method name>
 *
 * Usage:
 *   java edu.washington.cs.mut.testfinder.TestFinder \
 *     <output file path> \
 *     <test classes directory path>
 *     <file with patterns to exclude (one pattern per line)>"
 *     <file with patterns to include (one pattern per line)>"
 */
public class TestFinder {

    private static final ClassPool classPool = ClassPool.getDefault();

    private static void usageAndExit() {
        System.err.println("Usage: java " + TestFinder.class.getCanonicalName() +
          " <output file path>" +
          " <test classes directory path>" +
          " <file with patterns to exclude (one pattern per line)>" +
          " <file with patterns to include (one pattern per line)>");
        System.exit(1);
    }

    public static void main(String ... args) throws Exception {
        if (args.length != 4) {
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

        WildcardMatcher patternsToExclude = constructMatcher(new File(args[2]));
        WildcardMatcher patternsToInclude = constructMatcher(new File(args[3]));

        // Find all .class files and for each one find the test method declared
        // in the test class
        for (File file : TestFinder.getClassFiles(testsDir)) {
            FileInputStream fin = new FileInputStream(file);
            CtClass ctClass = classPool.makeClassIfNew(fin);

            for (String testMethod : JUnitTestFinder.find(patternsToExclude, patternsToInclude, ctClass)) {
                outputFile.println(testMethod);
            }

            ctClass.detach();
            fin.close();
        }

        // Exit and indicate success
        System.exit(0);
    }

    public static Collection<File> getClassFiles(final File directory) {
        List<File> classFiles = new ArrayList<File>();
        if (directory == null || directory.listFiles() == null){
            return classFiles;
        }
        for (File entry : directory.listFiles()) {
            if (entry.isFile() && entry.getName().endsWith(".class")) {
                classFiles.add(entry);
            } else {
                classFiles.addAll(getClassFiles(entry));
            }
        }
        return classFiles;
    }

    public static WildcardMatcher constructMatcher(File file) throws Exception {
        if (!file.exists() || !file.canRead()) {
            throw new RuntimeException(file + " does not exist or cannot be read");
        }

        StringBuilder str = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                str.append(line);
                str.append(",");
            }
        }

        return new WildcardMatcher(str.toString());
    }
}
