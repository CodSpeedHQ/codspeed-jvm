package bench;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class RleBenchmark {

  @Param({"65536"})
  private int size;

  private byte[] rawData;
  private byte[] encodedData;

  @Setup
  public void setup() {
    // Generate data with many runs (RLE-friendly)
    Random rng = new Random(42);
    rawData = new byte[size];
    int i = 0;
    while (i < size) {
      byte value = (byte) rng.nextInt(256);
      int runLen = 1 + rng.nextInt(Math.min(20, size - i));
      for (int j = 0; j < runLen && i < size; j++, i++) {
        rawData[i] = value;
      }
    }
    encodedData = Rle.encode(rawData);
  }

  @Benchmark
  public byte[] encode() {
    return Rle.encode(rawData);
  }

  @Benchmark
  public byte[] decode() {
    return Rle.decode(encodedData);
  }

  public static void main(String[] args) throws Exception {
    new Runner(new OptionsBuilder().include(RleBenchmark.class.getSimpleName()).build()).run();
  }
}
