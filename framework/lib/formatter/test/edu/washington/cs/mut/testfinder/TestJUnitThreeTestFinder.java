package edu.washington.cs.mut.testfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import javassist.ClassPool;
import javassist.CtClass;
import org.junit.Test;
import edu.washington.cs.mut.testrunner.junit3.AbstractTestClass;
import edu.washington.cs.mut.testrunner.junit3.EmptyTestClass;
import edu.washington.cs.mut.testrunner.junit3.InitError;
import edu.washington.cs.mut.testrunner.junit3.NonJUnitTest;
import edu.washington.cs.mut.testrunner.junit3.SimpleTest;
import edu.washington.cs.mut.testrunner.junit3.Timeout;
import edu.washington.cs.mut.util.WildcardMatcher;

public class TestJUnitThreeTestFinder {

    private static final ClassPool classPool = ClassPool.getDefault();

    private static final WildcardMatcher excludeMatcher = new WildcardMatcher("");

    @Test
    public void testAbstractTestClass() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(AbstractTestClass.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(excludeMatcher, matcher, ctClass);
        assertTrue(tests.isEmpty());
    }

    @Test
    public void testEmptyTestClass() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(EmptyTestClass.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(excludeMatcher, matcher, ctClass);
        for (String test : tests) {
            System.err.println(test);
        }
        assertTrue(tests.isEmpty());
    }

    @Test
    public void testNonJUnitTestClass() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(NonJUnitTest.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(excludeMatcher, matcher, ctClass);
        assertTrue(tests.isEmpty());
    }

    @Test
    public void testClassWithJUnitThreeTests() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");

        CtClass ctClass = classPool.get(SimpleTest.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(excludeMatcher, matcher, ctClass);
        assertEquals(3, tests.size());
        assertEquals(SimpleTest.class.getCanonicalName() + "::test1", tests.get(0));
        assertEquals(SimpleTest.class.getCanonicalName() + "::test2", tests.get(1));
        assertEquals(SimpleTest.class.getCanonicalName() + "::test3", tests.get(2));

        ctClass = classPool.get(Timeout.class.getCanonicalName());
        tests = JUnitTestFinder.find(excludeMatcher, matcher, ctClass);
        assertEquals(3, tests.size());
        assertEquals(Timeout.class.getCanonicalName() + "::test1", tests.get(0));
        assertEquals(Timeout.class.getCanonicalName() + "::test2", tests.get(1));
        assertEquals(Timeout.class.getCanonicalName() + "::test3", tests.get(2));

        ctClass = classPool.get(InitError.class.getCanonicalName());
        tests = JUnitTestFinder.find(excludeMatcher, matcher, ctClass);
        assertTrue(tests.isEmpty());
    }
}
