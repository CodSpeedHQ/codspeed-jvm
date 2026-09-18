package io.codspeed;

import java.io.IOException;
import java.io.InputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Reads the {@code SourceFile} attribute of a compiled class, which names the file the class was
 * compiled from ({@code MyBench.kt}, {@code Foo.scala}, {@code benchmarks.kt}, ...).
 */
final class ClassFileSourceName {

  private ClassFileSourceName() {}

  /**
   * Reads the source file name declared by the given class.
   *
   * @param classQName fully qualified class name
   * @return the declared source file name, or null if the class isn't on the classpath, can't be
   *     parsed, or carries no {@code SourceFile} attribute
   */
  static String read(String classQName) {
    InputStream stream = openClassFile(classQName.replace('.', '/') + ".class");
    if (stream == null) {
      return null;
    }

    try (InputStream in = stream) {
      String[] source = {null};
      new ClassReader(in)
          .accept(
              new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visitSource(String name, String debug) {
                  source[0] = name;
                }
              },
              ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
      return source[0];
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static InputStream openClassFile(String resource) {
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    if (contextLoader != null) {
      InputStream stream = contextLoader.getResourceAsStream(resource);
      if (stream != null) {
        return stream;
      }
    }

    ClassLoader ownLoader = ClassFileSourceName.class.getClassLoader();
    return ownLoader == null ? null : ownLoader.getResourceAsStream(resource);
  }
}
