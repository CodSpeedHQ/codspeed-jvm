package io.codspeed.result;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;

public class WalltimeBenchmark {
  private static final double IQR_OUTLIER_FACTOR = 1.5;
  private static final double STDEV_OUTLIER_FACTOR = 3.0;

  private final String name;
  private final String uri;
  private final BenchmarkConfig config;
  private final BenchmarkStats stats;

  private WalltimeBenchmark(String name, String uri, BenchmarkConfig config, BenchmarkStats stats) {
    this.name = name;
    this.uri = uri;
    this.config = config;
    this.stats = stats;
  }

  public String getName() {
    return name;
  }

  public String getUri() {
    return uri;
  }

  public BenchmarkConfig getConfig() {
    return config;
  }

  public BenchmarkStats getStats() {
    return stats;
  }

  public static WalltimeBenchmark fromRuntimeData(
      String name, String uri, long[] itersPerRound, long[] timesPerRoundNs, Long maxTimeNs) {
    int rounds = timesPerRoundNs.length;

    if (rounds == 0) {
      BenchmarkStats stats = new BenchmarkStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
      BenchmarkConfig config =
          new BenchmarkConfig(null, null, maxTimeNs != null ? (double) maxTimeNs : null, null);
      return new WalltimeBenchmark(name, uri, config, stats);
    }

    double totalTimeNs = 0;
    for (long t : timesPerRoundNs) {
      totalTimeNs += t;
    }
    double totalTime = totalTimeNs / 1_000_000_000.0;

    // Integer division to match Rust's u128 / u128 behavior
    double[] timePerIterPerRound = new double[rounds];
    for (int i = 0; i < rounds; i++) {
      timePerIterPerRound[i] = (double) (timesPerRoundNs[i] / itersPerRound[i]);
    }

    double[] sorted = timePerIterPerRound.clone();
    Arrays.sort(sorted);

    double meanNs = 0;
    for (double t : timePerIterPerRound) {
      meanNs += t;
    }
    meanNs /= rounds;

    double stdevNs;
    if (rounds < 2) {
      stdevNs = 0.0;
    } else {
      double sumSqDiff = 0;
      for (double t : timePerIterPerRound) {
        double diff = t - meanNs;
        sumSqDiff += diff * diff;
      }
      stdevNs = Math.sqrt(sumSqDiff / (rounds - 1));
    }

    double minNs = sorted[0];
    double maxNs = sorted[sorted.length - 1];

    double q1Ns = quantile(sorted, 0.25);
    double medianNs = quantile(sorted, 0.5);
    double q3Ns = quantile(sorted, 0.75);

    double iqrNs = q3Ns - q1Ns;
    long iqrOutlierRounds = 0;
    for (double t : timePerIterPerRound) {
      if (t < q1Ns - IQR_OUTLIER_FACTOR * iqrNs || t > q3Ns + IQR_OUTLIER_FACTOR * iqrNs) {
        iqrOutlierRounds++;
      }
    }

    long stdevOutlierRounds = 0;
    for (double t : timePerIterPerRound) {
      if (Math.abs(t - meanNs) > STDEV_OUTLIER_FACTOR * stdevNs) {
        stdevOutlierRounds++;
      }
    }

    long totalIters = 0;
    for (long i : itersPerRound) {
      totalIters += i;
    }
    long iterPerRound = totalIters / itersPerRound.length;

    BenchmarkStats stats =
        new BenchmarkStats(
            minNs,
            maxNs,
            meanNs,
            stdevNs,
            q1Ns,
            medianNs,
            q3Ns,
            rounds,
            totalTime,
            iqrOutlierRounds,
            stdevOutlierRounds,
            iterPerRound,
            0);

    BenchmarkConfig config =
        new BenchmarkConfig(null, null, maxTimeNs != null ? (double) maxTimeNs : null, null);

    return new WalltimeBenchmark(name, uri, config, stats);
  }

  static double quantile(double[] sorted, double q) {
    if (sorted.length == 0) {
      return 0.0;
    }
    if (sorted.length == 1) {
      return sorted[0];
    }
    if (sorted.length == 2) {
      // Linear interpolation between the two values
      return sorted[0] * (1.0 - q) + sorted[1] * q;
    }
    // Python's exclusive method: position = q * (n + 1) - 1 (0-based indexing)
    double pos = q * (sorted.length + 1) - 1;
    int idx = (int) Math.floor(pos);
    double frac = pos - idx;
    if (idx + 1 < sorted.length) {
      return sorted[idx] * (1.0 - frac) + sorted[idx + 1] * frac;
    }
    return sorted[Math.min(idx, sorted.length - 1)];
  }

  public static class BenchmarkStats {
    @SerializedName("min_ns")
    private final double minNs;

    @SerializedName("max_ns")
    private final double maxNs;

    @SerializedName("mean_ns")
    private final double meanNs;

    @SerializedName("stdev_ns")
    private final double stdevNs;

    @SerializedName("q1_ns")
    private final double q1Ns;

    @SerializedName("median_ns")
    private final double medianNs;

    @SerializedName("q3_ns")
    private final double q3Ns;

    private final long rounds;

    @SerializedName("total_time")
    private final double totalTime;

    @SerializedName("iqr_outlier_rounds")
    private final long iqrOutlierRounds;

    @SerializedName("stdev_outlier_rounds")
    private final long stdevOutlierRounds;

    @SerializedName("iter_per_round")
    private final long iterPerRound;

    @SerializedName("warmup_iters")
    private final long warmupIters;

    public BenchmarkStats(
        double minNs,
        double maxNs,
        double meanNs,
        double stdevNs,
        double q1Ns,
        double medianNs,
        double q3Ns,
        long rounds,
        double totalTime,
        long iqrOutlierRounds,
        long stdevOutlierRounds,
        long iterPerRound,
        long warmupIters) {
      this.minNs = minNs;
      this.maxNs = maxNs;
      this.meanNs = meanNs;
      this.stdevNs = stdevNs;
      this.q1Ns = q1Ns;
      this.medianNs = medianNs;
      this.q3Ns = q3Ns;
      this.rounds = rounds;
      this.totalTime = totalTime;
      this.iqrOutlierRounds = iqrOutlierRounds;
      this.stdevOutlierRounds = stdevOutlierRounds;
      this.iterPerRound = iterPerRound;
      this.warmupIters = warmupIters;
    }

    public double getMinNs() {
      return minNs;
    }

    public double getMaxNs() {
      return maxNs;
    }

    public double getMeanNs() {
      return meanNs;
    }

    public double getStdevNs() {
      return stdevNs;
    }

    public double getQ1Ns() {
      return q1Ns;
    }

    public double getMedianNs() {
      return medianNs;
    }

    public double getQ3Ns() {
      return q3Ns;
    }

    public long getRounds() {
      return rounds;
    }

    public double getTotalTime() {
      return totalTime;
    }

    public long getIqrOutlierRounds() {
      return iqrOutlierRounds;
    }

    public long getStdevOutlierRounds() {
      return stdevOutlierRounds;
    }

    public long getIterPerRound() {
      return iterPerRound;
    }

    public long getWarmupIters() {
      return warmupIters;
    }
  }

  public static class BenchmarkConfig {
    @SerializedName("warmup_time_ns")
    private final Double warmupTimeNs;

    @SerializedName("min_round_time_ns")
    private final Double minRoundTimeNs;

    @SerializedName("max_time_ns")
    private final Double maxTimeNs;

    @SerializedName("max_rounds")
    private final Long maxRounds;

    public BenchmarkConfig(
        Double warmupTimeNs, Double minRoundTimeNs, Double maxTimeNs, Long maxRounds) {
      this.warmupTimeNs = warmupTimeNs;
      this.minRoundTimeNs = minRoundTimeNs;
      this.maxTimeNs = maxTimeNs;
      this.maxRounds = maxRounds;
    }
  }
}
