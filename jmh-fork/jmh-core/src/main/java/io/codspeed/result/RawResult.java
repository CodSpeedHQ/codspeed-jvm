package io.codspeed.result;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RawResult {
  private String name;
  private String uri;
  private int pid;

  @SerializedName("codspeed_time_per_round_ns")
  private long[] codspeedTimePerRoundNs;

  @SerializedName("codspeed_iters_per_round")
  private long[] codspeedItersPerRound;

  public String getName() {
    return name;
  }

  public String getUri() {
    return uri;
  }

  public int getPid() {
    return pid;
  }

  public long[] getCodspeedTimePerRoundNs() {
    return codspeedTimePerRoundNs;
  }

  public long[] getCodspeedItersPerRound() {
    return codspeedItersPerRound;
  }

  public static List<ParsedResult> parseFolder(Path folder) throws IOException {
    Gson gson = new Gson();
    List<ParsedResult> results = new ArrayList<>();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.json")) {
      for (Path path : stream) {
        try (Reader reader = Files.newBufferedReader(path)) {
          RawResult raw = gson.fromJson(reader, RawResult.class);
          WalltimeBenchmark bench =
              WalltimeBenchmark.fromRuntimeData(
                  raw.name, raw.uri, raw.codspeedItersPerRound, raw.codspeedTimePerRoundNs, null);
          results.add(new ParsedResult(raw.pid, bench));
        }
        Files.delete(path);
      }
    }

    return results;
  }

  public static class ParsedResult {
    private final int pid;
    private final WalltimeBenchmark benchmark;

    public ParsedResult(int pid, WalltimeBenchmark benchmark) {
      this.pid = pid;
      this.benchmark = benchmark;
    }

    public int getPid() {
      return pid;
    }

    public WalltimeBenchmark getBenchmark() {
      return benchmark;
    }
  }
}
