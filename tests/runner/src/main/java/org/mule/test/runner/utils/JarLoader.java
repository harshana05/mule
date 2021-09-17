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

import net.bytebuddy.agent.ByteBuddyAgent;

public class JarLoader {

  private static Instrumentation inst;
  private static ClassLoader addUrlThis;
  private static Method addUrlMethod;
  private static File AGENT_JAR =
      new File("~/.m2/repository/org/mule/tests/mule-tests-runner/4.5.0-SNAPSHOT/mule-tests-runner-4.5.0-SNAPSHOT.jar");

  private JarLoader() {}

  public static void addToClassPathWithAgent(File jarFile) {
    ByteBuddyAgent.attach(AGENT_JAR, String.valueOf(getPid()), jarFile.getPath());
  }

  private static String getPid() {
    return java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
  }

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

    try {
      if (isJavaVersionAbove8()) {
        addToClassPathWithAgent(jarFile);
      } else {
        getAddUrlMethod().invoke(addUrlThis, jarFile.toURI().toURL());
      }
    } catch (SecurityException iae) {
      throw new RuntimeException("security model prevents access to method", iae);
    } catch (Throwable t) {
      throw new AssertionError("internal error", t);
    }
  }

  private static boolean isJavaVersionAbove8() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      // Version format up to Java 8, e.g. 1.7.0
      return false;
    } else {
      // Version format beginning at Java 9, the major version stands in front
      return true;
    }
  }

  public static void agentmain(String agentArgs, Instrumentation instrumentation) throws IOException {
    instrumentation.appendToSystemClassLoaderSearch(new JarFile(agentArgs));
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
