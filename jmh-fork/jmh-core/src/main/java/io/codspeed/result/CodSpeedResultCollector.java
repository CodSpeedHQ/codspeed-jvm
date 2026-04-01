package io.codspeed.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.codspeed.BenchmarkUri;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.RunResult;

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
      TimeUnit timeUnit = params.getTimeUnit();
      String benchmarkName = params.getBenchmark();
      String uri = BenchmarkUri.fromBenchmarkParams(params);
      double nanosPerUnit = TimeUnit.NANOSECONDS.convert(1, timeUnit);

      collectAverageTime(runResult, benchmarkName, uri, nanosPerUnit, benchmarks);
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
   * Collects results from AverageTime mode.
   *
   * <p>JMH runs a fixed time window per iteration and reports the average time per operation. Each
   * iteration produces one data point: the total wall time for all ops in that iteration, which we
   * back-calculate from the score: {@code timeNs = score * ops * nanosPerUnit}.
   *
   * <p>This is structurally equivalent to how criterion (codspeed-rust) collects walltime data:
   * each round has a variable iteration count and a measured total wall time.
   */
  private static void collectAverageTime(
      RunResult runResult,
      String benchmarkName,
      String uri,
      double nanosPerUnit,
      List<WalltimeBenchmark> benchmarks) {
    List<Long> itersPerRound = new ArrayList<>();
    List<Long> timesPerRoundNs = new ArrayList<>();

    for (BenchmarkResult br : runResult.getBenchmarkResults()) {
      for (IterationResult ir : br.getIterationResults()) {
        long ops = ir.getMetadata().getMeasuredOps();
        double score = ir.getPrimaryResult().getScore();

        // avgt: score = durationNs / (ops * nanosPerUnit)
        long timeNs = Math.round(score * ops * nanosPerUnit);

        itersPerRound.add(ops);
        timesPerRoundNs.add(timeNs);
      }
    }

    if (itersPerRound.isEmpty()) {
      return;
    }

    benchmarks.add(
        WalltimeBenchmark.fromRuntimeData(
            benchmarkName, uri, toLongArray(itersPerRound), toLongArray(timesPerRoundNs), null));
  }

  private static long[] toLongArray(List<Long> list) {
    long[] arr = new long[list.size()];
    for (int i = 0; i < list.size(); i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }
}
