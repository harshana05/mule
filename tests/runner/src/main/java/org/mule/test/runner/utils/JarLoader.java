/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.runner.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

public class JarLoader {

  private static Instrumentation inst;
  private static ClassLoader addUrlThis;
  private static Method addUrlMethod;

  private JarLoader() {}

  /**
   * Adds a JAR file to the list of JAR files searched by the system class loader. This effectively adds a new JAR to the class
   * path.
   *
   * @param jarFile the JAR file to add
   * @throws IOException if there is an error accessing the JAR file
   */
  public static synchronized void addToClassPath(File jarFile) throws IOException {
    if (jarFile == null) {
      throw new NullPointerException();
    }
    // do our best to ensure consistent behaviour across methods
    if (!jarFile.exists()) {
      throw new FileNotFoundException(jarFile.getAbsolutePath());
    }
    if (!jarFile.canRead()) {
      throw new IOException("can't read jar: " + jarFile.getAbsolutePath());
    }
    if (jarFile.isDirectory()) {
      throw new IOException("not a jar: " + jarFile.getAbsolutePath());
    }

    // add the jar using instrumentation, or fall back to reflection
    if (inst != null) {
      inst.appendToSystemClassLoaderSearch(new JarFile(jarFile));
      return;
    }
    try {
      getAddUrlMethod().invoke(addUrlThis, jarFile.toURI().toURL());
    } catch (SecurityException iae) {
      throw new RuntimeException("security model prevents access to method", iae);
    } catch (Throwable t) {
      throw new AssertionError("internal error", t);
    }
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) {
    agentmain(agentArgs, instrumentation);
  }

  public static void agentmain(String agentArgs, Instrumentation instrumentation) {
    if (inst == null) {
      inst = instrumentation;
    }
  }

  private static Method getAddUrlMethod() {
    if (addUrlMethod == null) {
      addUrlThis = ClassLoader.getSystemClassLoader();
      if (addUrlThis instanceof URLClassLoader) {
        try {
          final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
          method.setAccessible(true);
          addUrlMethod = method;
        } catch (NoSuchMethodException nsm) {
          throw new AssertionError(); // violates URLClassLoader API!
        }
      } else {
        throw new UnsupportedOperationException(
                                                "did you forget -javaagent:<jarpath>?");
      }
    }
    return addUrlMethod;
  }

}
