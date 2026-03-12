package io.codspeed;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import org.junit.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.runner.IterationType;
import org.openjdk.jmh.runner.WorkloadParams;
import org.openjdk.jmh.runner.options.TimeValue;

public class BenchmarkUriTest {

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
}
