package io.codspeed.result;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

/**
 * Tests for {@link WalltimeBenchmark} statistics computation. Test vectors are ported from the Rust
 * reference implementation in codspeed/crates/runner-shared/src/walltime_results/stats.rs to ensure
 * cross-language consistency.
 * 
 * See: https://github.com/CodSpeedHQ/codspeed/blob/b76cef5129b69f356b0ba929bf507658f708c3e3/crates/runner-shared/src/walltime_results/stats.rs#L154-L416
 */
public class WalltimeBenchmarkTest {

  private static final double EPSILON = 1e-6;

  private static WalltimeBenchmark.BenchmarkStats compute(long[] times, long[] iters) {
    return WalltimeBenchmark.fromRuntimeData("test", "test::uri", iters, times, null).getStats();
  }

  // --- Ported from Rust: test_parse_single_benchmark ---
  @Test
  public void testSingleRound() {
    WalltimeBenchmark.BenchmarkStats stats = compute(new long[] {42}, new long[] {1});

    assertEquals(42.0, stats.getMinNs(), EPSILON);
    assertEquals(42.0, stats.getMaxNs(), EPSILON);
    assertEquals(42.0, stats.getMeanNs(), EPSILON);
    assertEquals(42.0, stats.getMedianNs(), EPSILON);
    assertEquals(42.0, stats.getQ1Ns(), EPSILON);
    assertEquals(42.0, stats.getQ3Ns(), EPSILON);
    assertEquals(0.0, stats.getStdevNs(), EPSILON);
    assertEquals(1, stats.getRounds());
    assertEquals(0.000000042, stats.getTotalTime(), 1e-12);
    assertEquals(1, stats.getIterPerRound());
    assertEquals(0, stats.getIqrOutlierRounds());
    assertEquals(0, stats.getStdevOutlierRounds());
  }

  // --- Ported from Rust: test_parse_bench_with_variable_iterations ---
  @Test
  public void testVariableIterations() {
    // All rounds have 42ns per iteration despite different iteration counts
    long[] times = {42, 84, 126, 168, 210, 252};
    long[] iters = {1, 2, 3, 4, 5, 6};
    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(42.0, stats.getMeanNs(), EPSILON);
    assertEquals(0.0, stats.getStdevNs(), EPSILON);
    assertEquals(42.0, stats.getMinNs(), EPSILON);
    assertEquals(42.0, stats.getMaxNs(), EPSILON);
    assertEquals(0.000000882, stats.getTotalTime(), 1e-12);
    assertEquals(3, stats.getIterPerRound()); // average of 1..6
  }

  // --- Ported from Rust: test_basic_stats_computation ---
  @Test
  public void testBasicStats() {
    // times/iters → timePerIter: [600, 300, 100, 200, 400]
    // sorted: [100, 200, 300, 400, 600]
    long[] times = {6000, 3000, 1000, 2000, 4000};
    long[] iters = {10, 10, 10, 10, 10};
    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(100.0, stats.getMinNs(), EPSILON);
    assertEquals(600.0, stats.getMaxNs(), EPSILON);
    assertEquals(320.0, stats.getMeanNs(), EPSILON);
    assertEquals(300.0, stats.getMedianNs(), EPSILON);
    assertEquals(150.0, stats.getQ1Ns(), EPSILON);
    assertEquals(500.0, stats.getQ3Ns(), EPSILON);
    // variance = [(100-320)^2 + (200-320)^2 + (300-320)^2 + (400-320)^2 + (600-320)^2] / 4
    //          = 37000, stdev = sqrt(37000)
    assertEquals(Math.sqrt(37000.0), stats.getStdevNs(), 0.01);
    assertEquals(5, stats.getRounds());
    assertEquals(0.000016, stats.getTotalTime(), 1e-9);
    assertEquals(10, stats.getIterPerRound());
    assertEquals(0, stats.getIqrOutlierRounds());
    assertEquals(0, stats.getStdevOutlierRounds());
  }

  // --- Ported from Rust: test_stdev_outlier_detection ---
  @Test
  public void testStdevOutlierDetection() {
    // 16 rounds of 1ns + 1 round of 50ns
    long[] times = new long[17];
    long[] iters = new long[17];
    Arrays.fill(times, 1L);
    Arrays.fill(iters, 1L);
    times[16] = 50;

    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(3.8823529411764706, stats.getMeanNs(), EPSILON);
    assertEquals(1.0, stats.getMedianNs(), EPSILON);
    assertEquals(11.884245626780316, stats.getStdevNs(), EPSILON);
    assertEquals(1, stats.getStdevOutlierRounds());
  }

  // --- Ported from Rust: test_iqr_outlier_detection ---
  @Test
  public void testIqrOutlierDetection() {
    long[] times = {100, 110, 120, 130, 140, 500};
    long[] iters = {1, 1, 1, 1, 1, 1};
    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(107.5, stats.getQ1Ns(), EPSILON);
    assertEquals(230.0, stats.getQ3Ns(), EPSILON);
    assertEquals(1, stats.getIqrOutlierRounds());
  }

  // --- Ported from Rust: test_single_round_edge_case ---
  @Test
  public void testSingleRoundWithMultipleIters() {
    WalltimeBenchmark.BenchmarkStats stats = compute(new long[] {500}, new long[] {5});

    assertEquals(100.0, stats.getMinNs(), EPSILON);
    assertEquals(100.0, stats.getMaxNs(), EPSILON);
    assertEquals(100.0, stats.getMeanNs(), EPSILON);
    assertEquals(100.0, stats.getMedianNs(), EPSILON);
    assertEquals(100.0, stats.getQ1Ns(), EPSILON);
    assertEquals(100.0, stats.getQ3Ns(), EPSILON);
    assertEquals(0.0, stats.getStdevNs(), EPSILON);
    assertEquals(1, stats.getRounds());
    assertEquals(0.0000005, stats.getTotalTime(), 1e-12);
    assertEquals(0, stats.getIqrOutlierRounds());
    assertEquals(0, stats.getStdevOutlierRounds());
  }

  // --- Ported from Rust: test_quantile_computation ---
  @Test
  public void testQuantileOddLength() {
    // sorted: [10, 20, 30, 40, 50, 60, 70, 80, 90]
    long[] times = {10, 20, 30, 40, 50, 60, 70, 80, 90};
    long[] iters = {1, 1, 1, 1, 1, 1, 1, 1, 1};
    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(25.0, stats.getQ1Ns(), EPSILON);
    assertEquals(50.0, stats.getMedianNs(), EPSILON);
    assertEquals(75.0, stats.getQ3Ns(), EPSILON);
  }

  // --- Ported from Rust: test_quantile_interpolation ---
  @Test
  public void testQuantileEvenLength() {
    // sorted: [10, 20, 30, 40, 50, 60, 70, 80]
    long[] times = {10, 20, 30, 40, 50, 60, 70, 80};
    long[] iters = {1, 1, 1, 1, 1, 1, 1, 1};
    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(22.5, stats.getQ1Ns(), EPSILON);
    assertEquals(45.0, stats.getMedianNs(), EPSILON);
    assertEquals(67.5, stats.getQ3Ns(), EPSILON);
  }

  // --- Ported from Rust: test_empty_rounds ---
  @Test
  public void testEmptyRounds() {
    WalltimeBenchmark.BenchmarkStats stats = compute(new long[] {}, new long[] {});

    assertEquals(0.0, stats.getMinNs(), EPSILON);
    assertEquals(0.0, stats.getMaxNs(), EPSILON);
    assertEquals(0.0, stats.getMeanNs(), EPSILON);
    assertEquals(0.0, stats.getMedianNs(), EPSILON);
    assertEquals(0.0, stats.getStdevNs(), EPSILON);
    assertEquals(0, stats.getRounds());
    assertEquals(0.0, stats.getTotalTime(), EPSILON);
  }

  // --- Ported from Rust: test_two_rounds ---
  @Test
  public void testTwoRounds() {
    long[] times = {100, 200};
    long[] iters = {1, 1};
    WalltimeBenchmark.BenchmarkStats stats = compute(times, iters);

    assertEquals(100.0, stats.getMinNs(), EPSILON);
    assertEquals(200.0, stats.getMaxNs(), EPSILON);
    assertEquals(150.0, stats.getMeanNs(), EPSILON);
    assertEquals(150.0, stats.getMedianNs(), EPSILON);
    assertEquals(70.71067811865476, stats.getStdevNs(), EPSILON);
    assertEquals(2, stats.getRounds());
  }

  // --- Direct quantile function tests ---
  @Test
  public void testQuantileFunctionSingleElement() {
    assertEquals(42.0, WalltimeBenchmark.quantile(new double[] {42.0}, 0.25), EPSILON);
    assertEquals(42.0, WalltimeBenchmark.quantile(new double[] {42.0}, 0.5), EPSILON);
    assertEquals(42.0, WalltimeBenchmark.quantile(new double[] {42.0}, 0.75), EPSILON);
  }

  @Test
  public void testQuantileFunctionEmpty() {
    assertEquals(0.0, WalltimeBenchmark.quantile(new double[] {}, 0.5), EPSILON);
  }

  @Test
  public void testQuantileFunctionTwoElements() {
    double[] sorted = {10.0, 20.0};
    // For n==2, uses simple linear interpolation: sorted[0]*(1-q) + sorted[1]*q
    assertEquals(12.5, WalltimeBenchmark.quantile(sorted, 0.25), EPSILON);
    assertEquals(15.0, WalltimeBenchmark.quantile(sorted, 0.5), EPSILON);
    assertEquals(17.5, WalltimeBenchmark.quantile(sorted, 0.75), EPSILON);
  }
}
