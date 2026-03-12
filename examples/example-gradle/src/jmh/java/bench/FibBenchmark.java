package bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class FibBenchmark {

  @Param({"30"})
  private int n;

  @Benchmark
  public long fib() {
    return fib(n);
  }

  private static long fib(int n) {
    if (n <= 1) return n;
    return fib(n - 1) + fib(n - 2);
  }
}
