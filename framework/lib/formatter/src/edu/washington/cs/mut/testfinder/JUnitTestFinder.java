package edu.washington.cs.mut.testfinder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import org.junit.runner.Description;
import org.junit.runner.Request;

import edu.washington.cs.mut.util.WildcardMatcher;

public class JUnitTestFinder {

    /**
     * Collects all JUnit test methods that match a given matcher in a given
     * class.
     *
     * @param testsMatcher wildcard expression
     * @param ctClass a {@link javassist.CtClass} object
     * @return list of test methods in the {@link javassist.CtClass} object
     */
    public static List<String> find(final WildcardMatcher testsMatcher,
        final CtClass ctClass) throws ClassNotFoundException {
        final List<String> testMethods = new ArrayList<String>();

        // load the test class using a default classloader
        Class<?> clazz =
            Class.forName(ctClass.getName(), false, Thread.currentThread().getContextClassLoader());
        assert clazz != null;

        if (!looksLikeJUnitTestClass(ctClass)) {
            return testMethods;
        }

        for (Description test : Request.aClass(clazz).getRunner().getDescription().getChildren()) {
            // a parameterised atomic test case does not have a method name
            if (test.getMethodName() == null) {
                for (Method m : clazz.getMethods()) {
                    if (looksLikeJUnitTestMethod(m)) {
                        String testMethodFullName = clazz.getName() + "::"
                            + m.getName() + test.getDisplayName();
                        if (testsMatcher.matches(testMethodFullName)) {
                            testMethods.add(testMethodFullName);
                        }
                    }
                }
            } else {
                if (test.getTestClass().getName().equals("junit.framework.TestSuite$1") &&
                      test.getMethodName().equals("warning")) {
                    // a JUnit 3 test class (i.e., one that extends junit.framework.TestCase)
                    // inherit a method named 'warning' from a super class 'junit.framework.TestSuite$1',
                    // which of course it is not a test method
                    continue;
                }

                // non-parameterised atomic test case
                String testMethodFullName = test.getTestClass().getName()
                    + "::" + test.getMethodName();
                if (testsMatcher.matches(testMethodFullName)) {
                    testMethods.add(testMethodFullName);
                }
            }
        }

        return testMethods;
    }

    private static boolean isAnonymousClass(final CtClass ctClass) {
        int pos = ctClass.getName().lastIndexOf('$');
        if (pos < 0) {
            return false;
        }
        return Character.isDigit(ctClass.getName().charAt(pos + 1));
    }

    private static boolean matchSuperClass(final CtClass ctClass, final String expectedSuperClassname) {
        try {
            CtClass superCtClass = ctClass.getSuperclass();
            while (superCtClass != null) {
                if (superCtClass.getName().equals(expectedSuperClassname)) {
                    return true;
                }
                superCtClass = superCtClass.getSuperclass();
            }
          return false;
        } catch (NotFoundException e) {
          return false;
        }
    }

    private static boolean matchMethodAnnotation(final CtClass ctClass, final String annotation) {
        for (CtBehavior ctBehavior : ctClass.getMethods()) {
            if (ctBehavior.hasAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeJUnitTestClass(final CtClass ctClass) {
        return (
            // a JUnit test class must be public
            ((ctClass.getModifiers() & Modifier.PUBLIC) != 0) &&
            // a JUnit test class cannot be an abstract class
            ((ctClass.getModifiers() & Modifier.ABSTRACT) == 0) &&
            // a JUnit test class cannot be an interface class
            ((ctClass.getModifiers() & Modifier.INTERFACE) == 0) &&
            // a JUnit test class cannot be an anonymous class
            (!isAnonymousClass(ctClass)) &&
            // a JUnit3/4 test class must has ...
            (
                // a JUnit3 test class must has a specific super class
                matchSuperClass(ctClass, "junit.framework.TestCase") ||
                // a JUnit4 test class must has at least one method annotated with specific JUnit tags
                (
                    matchMethodAnnotation(ctClass, "org.junit.Test") ||
                    matchMethodAnnotation(ctClass, "org.junit.experimental.theories.Theory")
                )
            )
        );
    }

    private static boolean looksLikeJUnitTestMethod(final Method m) {
        // JUnit 3: an atomic test case is "public", does not return anything ("void"), has 0
        // parameters and starts with the word "test"
        // JUnit 4: an atomic test case is annotated with @Test
        return (m.isAnnotationPresent(org.junit.Test.class) || (m.getParameterTypes().length == 0
            && m.getReturnType().equals(Void.TYPE) && Modifier.isPublic(m.getModifiers())
            && (m.getName().startsWith("test") || m.getName().endsWith("Test")
                || m.getName().startsWith("Test") || m.getName().endsWith("test"))));
    }
}
