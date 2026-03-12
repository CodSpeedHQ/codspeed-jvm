package io.codspeed;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.openjdk.jmh.infra.BenchmarkParams;

/** Builds CodSpeed benchmark URIs in the format: {file_path}::{classQName}::{method}[{params}] */
public class BenchmarkUri {

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

    String uri = filePath + "::" + classQName + "::" + benchName;

    String distribution = System.getenv("CODSPEED_JVM_DISTRIBUTION");
    if (distribution != null && !distribution.isEmpty()) {
      uri = uri + "_" + distribution;
    }

    return uri;
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
   * Searches from the git root for a .java file matching the class's package structure.
   *
   * <p>Falls back to the package-derived relative path if the file can't be found on disk.
   */
  static String resolveSourceFile(String classQName) {
    return sourceFileCache.computeIfAbsent(classQName, BenchmarkUri::resolveSourceFileUncached);
  }

  private static String resolveSourceFileUncached(String classQName) {
    // Handle inner classes: com.example.Outer$Inner -> com.example.Outer
    String outerClass = classQName;
    int dollarIdx = outerClass.indexOf('$');
    if (dollarIdx != -1) {
      outerClass = outerClass.substring(0, dollarIdx);
    }

    String relativePath = outerClass.replace('.', '/') + ".java";
    Path gitRoot = findGitRoot();

    Path found = findFile(gitRoot, relativePath);
    if (found != null) {
      // Normalize to forward slashes for consistent URIs across platforms
      return gitRoot.relativize(found).toString().replace('\\', '/');
    }

    return relativePath;
  }

  /** Walks up from the CWD to find the nearest .git directory, returns its parent. */
  static Path findGitRoot() {
    Path cached = cachedGitRoot;
    if (cached != null) {
      return cached;
    }

    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve(".git"))) {
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

  private static Path findFile(Path root, String relativeSuffix) {
    String suffix = "/" + relativeSuffix;
    Path[] result = new Path[1];

    try {
      Files.walkFileTree(
          root,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (file.toString().endsWith(suffix) || file.equals(root.resolve(relativeSuffix))) {
                result[0] = file;
                return FileVisitResult.TERMINATE;
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
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
