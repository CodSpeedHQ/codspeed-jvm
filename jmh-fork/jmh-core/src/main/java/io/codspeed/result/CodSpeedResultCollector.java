package io.codspeed.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.util.Statistics;

public class CodSpeedResultCollector {

  public static void collectAndWrite(
      Iterable<RunResult> runResults,
      int pid,
      String creatorName,
      String creatorVersion,
      Path outputDir)
      throws IOException {
    List<WalltimeBenchmark> benchmarks = new ArrayList<>();

    for (RunResult runResult : runResults) {
      BenchmarkParams params = runResult.getParams();
      Mode mode = params.getMode();
      TimeUnit timeUnit = params.getTimeUnit();
      String benchmarkName = params.getBenchmark();
      double nanosPerUnit = TimeUnit.NANOSECONDS.convert(1, timeUnit);

      if (mode == Mode.SampleTime) {
        collectSampleTime(runResult, benchmarkName, nanosPerUnit, benchmarks);
      } else {
        collectIterationBased(runResult, benchmarkName, mode, nanosPerUnit, benchmarks);
      }
    }

    if (benchmarks.isEmpty()) {
      return;
    }

    Files.createDirectories(outputDir);

    WalltimeResults.Creator creator = new WalltimeResults.Creator(creatorName, creatorVersion, pid);
    WalltimeResults results = new WalltimeResults(benchmarks, creator);

    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    Path outputFile = outputDir.resolve(pid + ".json");
    Files.writeString(outputFile, gson.toJson(results));
  }

  /**
   * Collects results from iteration-based modes (Throughput, AverageTime, SingleShotTime).
   *
   * <p>In these modes, JMH runs a fixed time window per iteration and reports an aggregated score.
   * Each iteration produces one data point: the total wall time for all ops in that iteration. We
   * back-calculate the total wall time from the score (see inline comments for the formulas per
   * mode).
   *
   * <p>Compare with {@link #collectSampleTime}, where JMH records per-operation latencies in a
   * histogram, giving finer-grained data.
   */
  private static void collectIterationBased(
      RunResult runResult,
      String benchmarkName,
      Mode mode,
      double nanosPerUnit,
      List<WalltimeBenchmark> benchmarks) {
    List<Long> itersPerRound = new ArrayList<>();
    List<Long> timesPerRoundNs = new ArrayList<>();

    for (BenchmarkResult br : runResult.getBenchmarkResults()) {
      for (IterationResult ir : br.getIterationResults()) {
        long ops = ir.getMetadata().getMeasuredOps();
        double score = ir.getPrimaryResult().getScore();

        long timeNs;
        if (mode == Mode.Throughput) {
          // score = ops * nanosPerUnit / durationNs
          timeNs = Math.round(ops * nanosPerUnit / score);
        } else {
          // avgt/ss: score = durationNs / (ops * nanosPerUnit)
          timeNs = Math.round(score * ops * nanosPerUnit);
        }

        itersPerRound.add(ops);
        timesPerRoundNs.add(timeNs);
      }
    }

    if (itersPerRound.isEmpty()) {
      return;
    }

    benchmarks.add(
        WalltimeBenchmark.fromRuntimeData(
            benchmarkName,
            benchmarkName,
            toLongArray(itersPerRound),
            toLongArray(timesPerRoundNs),
            null));
  }

  /**
   * Collects results from SampleTime mode.
   *
   * <p>In this mode, JMH measures each individual operation's latency separately and records them
   * in a histogram. Each sample is a direct timing of one op, giving finer-grained data than
   * iteration-based modes. We treat each sample as its own round with 1 iteration.
   */
  private static void collectSampleTime(
      RunResult runResult,
      String benchmarkName,
      double nanosPerUnit,
      List<WalltimeBenchmark> benchmarks) {
    List<Long> itersPerRound = new ArrayList<>();
    List<Long> timesPerRoundNs = new ArrayList<>();

    for (BenchmarkResult br : runResult.getBenchmarkResults()) {
      for (IterationResult ir : br.getIterationResults()) {
        Statistics stats = ir.getPrimaryResult().getStatistics();
        Iterator<Map.Entry<Double, Long>> rawData = stats.getRawData();

        while (rawData.hasNext()) {
          Map.Entry<Double, Long> entry = rawData.next();
          double valueInUnit = entry.getKey();
          long count = entry.getValue();

          // Each sample is one op's time in the output unit.
          // Convert back to nanoseconds.
          long timeNs = Math.round(valueInUnit * nanosPerUnit);

          for (long i = 0; i < count; i++) {
            itersPerRound.add(1L);
            timesPerRoundNs.add(timeNs);
          }
        }
      }
    }

    if (itersPerRound.isEmpty()) {
      return;
    }

    benchmarks.add(
        WalltimeBenchmark.fromRuntimeData(
            benchmarkName,
            benchmarkName,
            toLongArray(itersPerRound),
            toLongArray(timesPerRoundNs),
            null));
  }

  private static long[] toLongArray(List<Long> list) {
    long[] arr = new long[list.size()];
    for (int i = 0; i < list.size(); i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }
}
