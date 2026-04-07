package io.codspeed.result;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.codspeed.BenchmarkUri;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.CodSpeedResult;
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
      Mode mode = params.getMode();
      String benchmarkName = params.getBenchmark();
      String uri = BenchmarkUri.fromBenchmarkParams(params);

      if (mode != Mode.CodSpeed) {
        throw new IllegalStateException(
            "CodSpeedResultCollector only supports Mode.CodSpeed, got: " + mode);
      }

      List<Long> itersPerRound = new ArrayList<>();
      List<Long> timesPerRoundNs = new ArrayList<>();

      for (BenchmarkResult br : runResult.getBenchmarkResults()) {
        for (IterationResult ir : br.getIterationResults()) {
          CodSpeedResult result = (CodSpeedResult) ir.getPrimaryResult();
          itersPerRound.add(result.getRawOps());
          timesPerRoundNs.add(result.getRawDurationNs());
        }
      }

      benchmarks.add(
          WalltimeBenchmark.fromRuntimeData(
              benchmarkName, uri, toLongArray(itersPerRound), toLongArray(timesPerRoundNs), null));
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

  private static long[] toLongArray(List<Long> list) {
    long[] arr = new long[list.size()];
    for (int i = 0; i < list.size(); i++) {
      arr[i] = list.get(i);
    }
    return arr;
  }
}
