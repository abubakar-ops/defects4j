package edu.washington.cs.mut.testfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import javassist.ClassPool;
import javassist.CtClass;
import org.junit.Test;
import edu.washington.cs.mut.testrunner.junit4.AbstractTestClass;
import edu.washington.cs.mut.testrunner.junit4.EmptyTestClass;
import edu.washington.cs.mut.testrunner.junit4.InitError;
import edu.washington.cs.mut.testrunner.junit4.MethodTimeout;
import edu.washington.cs.mut.testrunner.junit4.NonJUnitTest;
import edu.washington.cs.mut.testrunner.junit4.ParameterizedTest;
import edu.washington.cs.mut.testrunner.junit4.SimpleTest;
import edu.washington.cs.mut.testrunner.junit4.Timeout;
import edu.washington.cs.mut.util.WildcardMatcher;

public class TestJUnitFourTestFinder {

    private static final ClassPool classPool = ClassPool.getDefault();

    @Test
    public void testAbstractTestClass() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(AbstractTestClass.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(matcher, ctClass);
        System.err.println(tests.size());
        assertTrue(tests.isEmpty());
    }

    @Test
    public void testEmptyTestClass() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(EmptyTestClass.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(matcher, ctClass);
        assertTrue(tests.isEmpty());
    }

    @Test
    public void testMethodTimeout() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(MethodTimeout.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(matcher, ctClass);
        assertEquals(2, tests.size());
        assertEquals(MethodTimeout.class.getCanonicalName() + "::test1", tests.get(0));
        assertEquals(MethodTimeout.class.getCanonicalName() + "::test2", tests.get(1));
    }

    @Test
    public void testNonJUnitTestClass() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(NonJUnitTest.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(matcher, ctClass);
        assertTrue(tests.isEmpty());
    }

    @Test
    public void testParameterizedTest() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");
        final CtClass ctClass = classPool.get(ParameterizedTest.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(matcher, ctClass);
        assertEquals(7, tests.size());
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[0]", tests.get(0));
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[1]", tests.get(1));
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[2]", tests.get(2));
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[3]", tests.get(3));
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[4]", tests.get(4));
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[5]", tests.get(5));
        assertEquals(ParameterizedTest.class.getCanonicalName() + "::testFibonacci[6]", tests.get(6));
    }

    @Test
    public void testClassWithJUnitFourTests() throws Exception {
        final WildcardMatcher matcher = new WildcardMatcher("*::*");

        CtClass ctClass = classPool.get(SimpleTest.class.getCanonicalName());
        List<String> tests = JUnitTestFinder.find(matcher, ctClass);
        assertEquals(3, tests.size());
        assertEquals(SimpleTest.class.getCanonicalName() + "::test1", tests.get(0));
        assertEquals(SimpleTest.class.getCanonicalName() + "::test2", tests.get(1));
        assertEquals(SimpleTest.class.getCanonicalName() + "::test3", tests.get(2));
        tests.clear();

        ctClass = classPool.get(Timeout.class.getCanonicalName());
        tests = JUnitTestFinder.find(matcher, ctClass);
        assertEquals(3, tests.size());
        assertEquals(Timeout.class.getCanonicalName() + "::test1", tests.get(0));
        assertEquals(Timeout.class.getCanonicalName() + "::test2", tests.get(1));
        assertEquals(Timeout.class.getCanonicalName() + "::test3", tests.get(2));
        tests.clear();

        ctClass = classPool.get(InitError.class.getCanonicalName());
        tests = JUnitTestFinder.find(matcher, ctClass);
        assertEquals(3, tests.size());
        assertEquals(InitError.class.getCanonicalName() + "::test1", tests.get(0));
        assertEquals(InitError.class.getCanonicalName() + "::test2", tests.get(1));
        assertEquals(InitError.class.getCanonicalName() + "::test3", tests.get(2));
    }
}
