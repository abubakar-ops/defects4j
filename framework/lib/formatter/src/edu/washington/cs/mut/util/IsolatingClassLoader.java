package edu.washington.cs.mut.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A classloader that should have the same classpath as the "normal" classloader, but shares
 * absolutely nothing with it. Used to ensure that the execution of a single test method doest not
 * change the static state of each loaded class, which will affect later the execution of other test
 * methods.
 */
public class IsolatingClassLoader extends URLClassLoader {

    private final Set<String> loadedClasses;

    public IsolatingClassLoader(final URL[] classpath, final ClassLoader parent) {
        super(classpath, parent);
        this.loadedClasses = new LinkedHashSet<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
          throws ClassNotFoundException {
        this.loadedClasses.add(name);

        if (// JUnit classes must be loaded by the super classloader. Otherwise,
            // all sort of classloader issues occur
            name.startsWith("junit.") || name.startsWith("org.junit.")
              || name.startsWith("org.hamcrest.") ||
            // Cobertura and EvoSuite classes must be loaded by the super
            // classloader! Otherwise, neither of the two tools is able to
            // collect and report code coverage
            name.startsWith("net.sourceforge.cobertura.") ||
            name.startsWith("org.evosuite.")) {
            return super.loadClass(name, resolve);
        }

        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                c = findClass(name);
            } catch (ClassNotFoundException e) {
                c = super.loadClass(name, resolve);
            }
        }

        if (resolve) {
            resolveClass(c);
        }

        return c;
    }

    /**
     * Returns the set of loaded classes.
     */
    public Set<String> getLoadedClasses() {
        return this.loadedClasses;
    }
}
