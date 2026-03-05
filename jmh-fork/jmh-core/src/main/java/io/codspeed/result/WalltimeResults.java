package io.codspeed.result;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class WalltimeResults {
  private final Creator creator;
  private final Instrument instrument;
  private final List<WalltimeBenchmark> benchmarks;

  public WalltimeResults(List<WalltimeBenchmark> benchmarks, Creator creator) {
    this.creator = creator;
    this.instrument = new Instrument("walltime");
    this.benchmarks = benchmarks;
  }

  public Creator getCreator() {
    return creator;
  }

  public List<WalltimeBenchmark> getBenchmarks() {
    return benchmarks;
  }

  public static class Creator {
    private final String name;
    private final String version;
    private final int pid;

    public Creator(String name, String version, int pid) {
      this.name = name;
      this.version = version;
      this.pid = pid;
    }

    public String getName() {
      return name;
    }

    public String getVersion() {
      return version;
    }

    public int getPid() {
      return pid;
    }
  }

  private static class Instrument {
    @SerializedName("type")
    private final String type;

    Instrument(String type) {
      this.type = type;
    }
  }
}
