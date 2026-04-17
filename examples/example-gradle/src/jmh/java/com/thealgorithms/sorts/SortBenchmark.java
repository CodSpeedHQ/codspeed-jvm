package com.thealgorithms.sorts;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SortBenchmark {

  @Param({"10000"})
  private int size;

  private Integer[] data;
  private Integer[] working;

  private final QuickSort quickSort = new QuickSort();
  private final MergeSort mergeSort = new MergeSort();
  private final TimSort timSort = new TimSort();
  private final DualPivotQuickSort dualPivotQuickSort = new DualPivotQuickSort();

  @Setup(Level.Trial)
  public void setup() {
    Random rng = new Random(42);
    data = new Integer[size];
    working = new Integer[size];
    for (int i = 0; i < size; i++) {
      data[i] = rng.nextInt(size * 10);
    }
  }

  private Integer[] resetWorking() {
    System.arraycopy(data, 0, working, 0, size);
    return working;
  }

  @Benchmark
  public Integer[] quickSort() {
    return quickSort.sort(resetWorking());
  }

  @Benchmark
  public Integer[] mergeSort() {
    return mergeSort.sort(resetWorking());
  }

  @Benchmark
  public Integer[] timSort() {
    return timSort.sort(resetWorking());
  }

  @Benchmark
  public Integer[] dualPivotQuickSort() {
    return dualPivotQuickSort.sort(resetWorking());
  }
}
