package edu.washington.cs.mut.util;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * A classloader that should have the same classpath as the "normal" classloader, but shares
 * absolutely nothing with it. Used to ensure that the execution of a single test method doest not
 * change the static state of each loaded class, which will affect later the execution of other test
 * methods.
 */
public class IsolatingClassLoader extends URLClassLoader {

    public IsolatingClassLoader(final URL[] classpath, final ClassLoader parent) {
        super(classpath, parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
          throws ClassNotFoundException {

        if (name.startsWith("junit.") || name.startsWith("org.junit.")
              || name.startsWith("org.hamcrest.")) {
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
}
