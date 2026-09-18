package io.codspeed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.runner.WorkloadParams;
import org.openjdk.jmh.runner.options.TimeValue;

public class BenchmarkUriTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private static Path createSource(Path root, String relativePath) throws IOException {
    Path file = root.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.createFile(file);
    return file;
  }

  private static BenchmarkParams makeParams(String benchmark, WorkloadParams workloadParams) {
    return new BenchmarkParams(
        benchmark,
        "generated.Target",
        false,
        1,
        new int[] {1},
        Collections.emptyList(),
        1,
        0,
        new IterationParams(IterationType.WARMUP, 1, TimeValue.seconds(1), 1),
        new IterationParams(IterationType.MEASUREMENT, 1, TimeValue.seconds(1), 1),
        Mode.Throughput,
        workloadParams,
        java.util.concurrent.TimeUnit.SECONDS,
        1,
        "java",
        Collections.emptyList(),
        "17",
        "HotSpot",
        "17.0.1",
        "1.0",
        TimeValue.seconds(10));
  }

  @Test
  public void testBuildBenchNameNoParams() {
    WorkloadParams wp = new WorkloadParams();
    BenchmarkParams params = makeParams("com.example.MyBenchmark.testMethod", wp);
    String benchName = BenchmarkUri.buildBenchName("testMethod", params);
    assertEquals("testMethod", benchName);
  }

  @Test
  public void testBuildBenchNameSingleParam() {
    WorkloadParams wp = new WorkloadParams();
    wp.put("size", "1024", 0);
    BenchmarkParams params = makeParams("com.example.MyBenchmark.testMethod", wp);
    String benchName = BenchmarkUri.buildBenchName("testMethod", params);
    assertEquals("testMethod[1024]", benchName);
  }

  @Test
  public void testBuildBenchNameMultipleParams() {
    WorkloadParams wp = new WorkloadParams();
    wp.put("size", "1024", 0);
    wp.put("mode", "fast", 0);
    BenchmarkParams params = makeParams("com.example.MyBenchmark.testMethod", wp);
    String benchName = BenchmarkUri.buildBenchName("testMethod", params);
    // WorkloadParams uses TreeMap, so keys are sorted alphabetically: mode < size
    assertEquals("testMethod[fast, 1024]", benchName);
  }

  @Test
  public void testResolveSourceFileFallback() {
    // Class that doesn't exist on disk — should fall back to derived path
    String path = BenchmarkUri.resolveSourceFile("com.nonexistent.FakeClass");
    assertEquals("com/nonexistent/FakeClass.java", path);
  }

  @Test
  public void testResolveSourceFileInnerClass() {
    // Inner class should strip the $Inner part
    String path = BenchmarkUri.resolveSourceFile("com.nonexistent.Outer$Inner");
    assertEquals("com/nonexistent/Outer.java", path);
  }

  @Test
  public void testFullUriNoParams() {
    WorkloadParams wp = new WorkloadParams();
    BenchmarkParams params = makeParams("com.nonexistent.MyBenchmark.testMethod", wp);
    String uri = BenchmarkUri.fromBenchmarkParams(params);
    assertEquals("com/nonexistent/MyBenchmark.java::com.nonexistent.MyBenchmark::testMethod", uri);
  }

  @Test
  public void testFullUriWithParams() {
    WorkloadParams wp = new WorkloadParams();
    wp.put("size", "65536", 0);
    BenchmarkParams params = makeParams("com.nonexistent.MyBenchmark.encode", wp);
    String uri = BenchmarkUri.fromBenchmarkParams(params);
    assertEquals(
        "com/nonexistent/MyBenchmark.java::com.nonexistent.MyBenchmark::encode[65536]", uri);
  }

  @Test
  public void testResolveSourceFileKotlinByExtension() throws IOException {
    Path root = tempFolder.getRoot().toPath();
    createSource(root, "src/jmh/kotlin/com/example/KotlinBench.kt");
    String path = BenchmarkUri.resolveSourceFile(root, "com.example.KotlinBench", null);
    assertEquals("src/jmh/kotlin/com/example/KotlinBench.kt", path);
  }

  @Test
  public void testResolveSourceFileScalaByExtension() throws IOException {
    Path root = tempFolder.getRoot().toPath();
    createSource(root, "src/jmh/scala/com/example/ScalaBench.scala");
    String path = BenchmarkUri.resolveSourceFile(root, "com.example.ScalaBench", null);
    assertEquals("src/jmh/scala/com/example/ScalaBench.scala", path);
  }

  @Test
  public void testResolveSourceFileKotlinFileNameDiffersFromClass() throws IOException {
    Path root = tempFolder.getRoot().toPath();
    createSource(root, "src/main/kotlin/com/example/benchmarks.kt");
    String path = BenchmarkUri.resolveSourceFile(root, "com.example.KotlinBench", "benchmarks.kt");
    assertEquals("src/main/kotlin/com/example/benchmarks.kt", path);
  }

  @Test
  public void testResolveSourceFileFallbackWithSourceFileName() {
    Path root = tempFolder.getRoot().toPath();
    String path =
        BenchmarkUri.resolveSourceFile(root, "com.example.KotlinBench", "KotlinBench.kt");
    assertEquals("com/example/KotlinBench.kt", path);
  }

  @Test
  public void testResolveSourceFileFallbackWithoutSourceFileName() {
    Path root = tempFolder.getRoot().toPath();
    String path = BenchmarkUri.resolveSourceFile(root, "com.example.KotlinBench", null);
    assertEquals("com/example/KotlinBench.java", path);
  }

  @Test
  public void testResolveSourceFileSkipsBuildDirectory() throws IOException {
    Path root = tempFolder.getRoot().toPath();
    createSource(root, "build/com/example/Skipped.kt");
    String path = BenchmarkUri.resolveSourceFile(root, "com.example.Skipped", null);
    assertEquals("com/example/Skipped.java", path);
  }

  @Test
  public void testResolveSourceFileInnerClassWithKotlinSource() throws IOException {
    Path root = tempFolder.getRoot().toPath();
    createSource(root, "com/example/Outer.kt");
    String path = BenchmarkUri.resolveSourceFile(root, "com.example.Outer$Inner", null);
    assertEquals("com/example/Outer.kt", path);
  }

  @Test
  public void testClassFileSourceNameReadsOwnSourceFile() {
    String sourceFile = ClassFileSourceName.read("io.codspeed.BenchmarkUriTest");
    assertEquals("BenchmarkUriTest.java", sourceFile);
  }

  @Test
  public void testClassFileSourceNameReturnsNullForMissingClass() {
    String sourceFile = ClassFileSourceName.read("com.nonexistent.Missing");
    assertNull(sourceFile);
  }

  @Test
  public void testResolveSourceFileEndToEndForThisTestClass() {
    String path = BenchmarkUri.resolveSourceFile("io.codspeed.BenchmarkUriTest");
    assertEquals("jmh-fork/jmh-core/src/test/java/io/codspeed/BenchmarkUriTest.java", path);
  }
}
