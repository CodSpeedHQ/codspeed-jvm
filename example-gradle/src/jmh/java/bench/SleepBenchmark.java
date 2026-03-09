package bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SleepBenchmark {

  private static void busyWait(long nanos) {
    long start = System.nanoTime();
    while (System.nanoTime() - start < nanos) {
      // busy wait
    }
  }

  @Benchmark
  public void sleep1us() {
    busyWait(1_000);
  }

  @Benchmark
  public void sleep10us() {
    busyWait(10_000);
  }

  @Benchmark
  public void sleep100us() {
    busyWait(100_000);
  }

  @Benchmark
  public void sleep1ms() {
    busyWait(1_000_000);
  }

  @Benchmark
  public void sleep10ms() {
    busyWait(10_000_000);
  }

  @Benchmark
  public void sleep50ms() {
    busyWait(50_000_000);
  }
}
