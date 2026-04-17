package bench;

import com.thealgorithms.bitmanipulation.*;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class BitManipulationBenchmark {

  @Param({"65535"})
  private int bitValue;

  @Benchmark
  public int countSetBits() {
    return CountSetBits.countSetBits(bitValue);
  }

  @Benchmark
  public Optional<Integer> findHighestSetBit() {
    return HighestSetBit.findHighestSetBit(bitValue);
  }

  @Benchmark
  public int binaryToGray() {
    return GrayCodeConversion.binaryToGray(bitValue);
  }

  @Benchmark
  public int grayToBinary() {
    return GrayCodeConversion.grayToBinary(bitValue);
  }

  @Benchmark
  public int reverseBits() {
    return ReverseBits.reverseBits(bitValue);
  }

  @Benchmark
  public boolean isPowerOfTwo() {
    return IsPowerTwo.isPowerTwo(bitValue);
  }
}
