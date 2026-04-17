package bench;

import com.thealgorithms.dynamicprogramming.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DynamicProgrammingBenchmark {

  // -- Fibonacci --

  @State(Scope.Benchmark)
  public static class FibonacciState {
    @Param({"30"})
    public int fibN;
  }

  @Benchmark
  public int fibonacciMemo(FibonacciState state) {
    return Fibonacci.fibMemo(state.fibN);
  }

  @Benchmark
  public int fibonacciBottomUp(FibonacciState state) {
    return Fibonacci.fibBotUp(state.fibN);
  }

  @Benchmark
  public int fibonacciOptimized(FibonacciState state) {
    return Fibonacci.fibOptimized(state.fibN);
  }

  // -- Knapsack --

  @State(Scope.Benchmark)
  public static class KnapsackState {
    @Param({"20"})
    public int knapsackSize;

    public int[] knapsackWeights;
    public int[] knapsackValues;

    @Setup(Level.Trial)
    public void setupKnapsack() {
      knapsackWeights = new int[knapsackSize];
      knapsackValues = new int[knapsackSize];
      for (int i = 0; i < knapsackSize; i++) {
        knapsackWeights[i] = (i % 5) + 1;
        knapsackValues[i] = (i % 10) + 1;
      }
    }
  }

  @Benchmark
  public int knapsack(KnapsackState state) {
    return Knapsack.knapSack(state.knapsackSize * 2, state.knapsackWeights, state.knapsackValues);
  }

  // -- Edit Distance --

  @State(Scope.Benchmark)
  public static class EditDistanceState {
    @Param({"saturday"})
    public String editWord1;
  }

  @Benchmark
  public int editDistance(EditDistanceState state) {
    return EditDistance.minDistance(state.editWord1, "sitting");
  }

  // -- Levenshtein Distance --

  @State(Scope.Benchmark)
  public static class LevenshteinState {
    @Param({"saturday sunday"})
    public String levenshteinPair;
  }

  @Benchmark
  public int levenshteinDistance(LevenshteinState state) {
    String[] parts = state.levenshteinPair.split(" ");
    return LevenshteinDistance.optimizedLevenshteinDistance(parts[0], parts[1]);
  }

  // -- Longest Increasing Subsequence --

  @State(Scope.Benchmark)
  public static class LisState {
    @Param({"100"})
    public int lisSize;

    public int[] lisArray;

    @Setup(Level.Trial)
    public void setupLIS() {
      lisArray = new int[lisSize];
      // Semi-random sequence with some increasing runs
      for (int i = 0; i < lisSize; i++) {
        lisArray[i] = (i * 7 + 3) % (lisSize * 2);
      }
    }
  }

  @Benchmark
  public int longestIncreasingSubsequence(LisState state) {
    return LongestIncreasingSubsequence.lis(state.lisArray);
  }

  // -- Coin Change --

  @State(Scope.Benchmark)
  public static class CoinChangeState {
    @Param({"200"})
    public int coinAmount;
  }

  private static final int[] COINS = {1, 5, 10, 25};

  @Benchmark
  public int coinChange(CoinChangeState state) {
    return CoinChange.minimumCoins(COINS, state.coinAmount);
  }

  // -- Subset Sum --

  @State(Scope.Benchmark)
  public static class SubsetSumState {
    @Param({"20"})
    public int subsetSize;

    public int[] subsetArr;

    @Setup(Level.Trial)
    public void setupSubsetSum() {
      subsetArr = new int[subsetSize];
      for (int i = 0; i < subsetSize; i++) {
        subsetArr[i] = i + 1;
      }
    }
  }

  @Benchmark
  public boolean subsetSum(SubsetSumState state) {
    return SubsetSum.subsetSum(state.subsetArr, state.subsetSize * 2);
  }
}
