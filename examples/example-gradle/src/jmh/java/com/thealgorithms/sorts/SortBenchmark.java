package com.thealgorithms.sorts;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class SortBenchmark {

  @Param({"100", "1000", "10000"})
  private int size;

  private Integer[] data;

  private final QuickSort quickSort = new QuickSort();
  private final MergeSort mergeSort = new MergeSort();
  private final HeapSort heapSort = new HeapSort();
  private final InsertionSort insertionSort = new InsertionSort();
  private final BubbleSort bubbleSort = new BubbleSort();
  private final TimSort timSort = new TimSort();
  private final ShellSort shellSort = new ShellSort();
  private final SelectionSort selectionSort = new SelectionSort();
  private final DualPivotQuickSort dualPivotQuickSort = new DualPivotQuickSort();
  private final IntrospectiveSort introspectiveSort = new IntrospectiveSort();

  @Setup(Level.Trial)
  public void setup() {
    Random rng = new Random(42);
    data = new Integer[size];
    for (int i = 0; i < size; i++) {
      data[i] = rng.nextInt(size * 10);
    }
  }

  private Integer[] copyData() {
    return Arrays.copyOf(data, data.length);
  }

  @Benchmark
  public Integer[] quickSort() {
    return quickSort.sort(copyData());
  }

  @Benchmark
  public Integer[] mergeSort() {
    return mergeSort.sort(copyData());
  }

  @Benchmark
  public Integer[] heapSort() {
    return heapSort.sort(copyData());
  }

  @Benchmark
  public Integer[] timSort() {
    return timSort.sort(copyData());
  }

  @Benchmark
  public Integer[] shellSort() {
    return shellSort.sort(copyData());
  }

  @Benchmark
  public Integer[] selectionSort() {
    return selectionSort.sort(copyData());
  }

  @Benchmark
  public Integer[] insertionSort() {
    return insertionSort.sort(copyData());
  }

  @Benchmark
  public Integer[] dualPivotQuickSort() {
    return dualPivotQuickSort.sort(copyData());
  }

  @Benchmark
  public Integer[] introspectiveSort() {
    return introspectiveSort.sort(copyData());
  }
}
