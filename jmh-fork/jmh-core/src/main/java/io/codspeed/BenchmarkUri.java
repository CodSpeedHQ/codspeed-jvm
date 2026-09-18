package io.codspeed;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.openjdk.jmh.infra.BenchmarkParams;

/** Builds CodSpeed benchmark URIs in the format: {file_path}::{classQName}::{method}[{params}] */
public class BenchmarkUri {

  static final String[] SOURCE_EXTENSIONS = {"java", "kt", "scala", "groovy"};

  private static volatile Path cachedGitRoot;
  private static final ConcurrentHashMap<String, String> sourceFileCache =
      new ConcurrentHashMap<>();

  /**
   * Builds a CodSpeed URI from the given benchmark params.
   *
   * @param params the JMH benchmark params
   * @return the URI string
   */
  public static String fromBenchmarkParams(BenchmarkParams params) {
    String benchmarkName = params.getBenchmark();
    int lastDot = benchmarkName.lastIndexOf('.');
    if (lastDot == -1) {
      return benchmarkName;
    }
    String classQName = benchmarkName.substring(0, lastDot);
    String method = benchmarkName.substring(lastDot + 1);

    String filePath = resolveSourceFile(classQName);
    String benchName = buildBenchName(method, params);

    return filePath + "::" + classQName + "::" + benchName;
  }

  static String buildBenchName(String method, BenchmarkParams params) {
    Collection<String> keys = params.getParamsKeys();
    if (keys == null || keys.isEmpty()) {
      return method;
    }

    StringBuilder sb = new StringBuilder(method);
    sb.append('[');
    boolean first = true;
    for (String key : keys) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(params.getParam(key));
      first = false;
    }
    sb.append(']');
    return sb.toString();
  }

  /**
   * Resolves the source file path relative to the git root for a given fully qualified class name.
   * Uses the class file's {@code SourceFile} attribute first, then searches from the git root for a
   * matching source file across known extensions (java, kt, scala, groovy).
   *
   * <p>Falls back to the package-derived relative path if the file can't be found on disk.
   */
  static String resolveSourceFile(String classQName) {
    return sourceFileCache.computeIfAbsent(classQName, BenchmarkUri::resolveSourceFileUncached);
  }

  private static String resolveSourceFileUncached(String classQName) {
    return resolveSourceFile(findGitRoot(), classQName, ClassFileSourceName.read(classQName));
  }

  static String resolveSourceFile(Path root, String classQName, String sourceFileName) {
    // Handle inner classes: com.example.Outer$Inner -> com.example.Outer
    int dollarIdx = classQName.indexOf('$');
    String outerClass = dollarIdx == -1 ? classQName : classQName.substring(0, dollarIdx);
    Path classPath = Paths.get(outerClass.replace('.', '/'));
    Path pkgDir = classPath.getParent();
    String simpleName = classPath.getFileName().toString();

    // The SourceFile attribute is authoritative when present; the extension search only covers
    // classes compiled without it.
    List<String> names =
        sourceFileName != null
            ? Collections.singletonList(sourceFileName)
            : Arrays.stream(SOURCE_EXTENSIONS)
                .map(ext -> simpleName + "." + ext)
                .collect(Collectors.toList());

    Path found =
        findFile(
            root,
            file ->
                (pkgDir == null || file.getParent().endsWith(pkgDir))
                    && names.contains(file.getFileName().toString()));

    Path result =
        found != null
            ? root.relativize(found)
            : pkgDir == null ? Paths.get(names.get(0)) : pkgDir.resolve(names.get(0));
    return result.toString().replace('\\', '/');
  }

  /** Walks up from the CWD to find the nearest .git directory, returns its parent. */
  static Path findGitRoot() {
    Path cached = cachedGitRoot;
    if (cached != null) {
      return cached;
    }

    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.exists(current.resolve(".git"))) {
        cachedGitRoot = current;
        return current;
      }
      current = current.getParent();
    }
    // Fall back to CWD if no git root found
    Path fallback = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    cachedGitRoot = fallback;
    return fallback;
  }

  private static Path findFile(Path root, Predicate<Path> matcher) {
    Path[] result = new Path[1];

    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (matcher.test(file)) {
                result[0] = file;
                return FileVisitResult.TERMINATE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (dir.equals(root)) {
                return FileVisitResult.CONTINUE;
              }
              String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
              // Skip hidden dirs, build outputs, and VCS dirs
              if (dirName.startsWith(".")
                  || dirName.equals("build")
                  || dirName.equals("target")
                  || dirName.equals("node_modules")) {
                return FileVisitResult.SKIP_SUBTREE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      // Fall through to return null
    }

    return result[0];
  }
}
