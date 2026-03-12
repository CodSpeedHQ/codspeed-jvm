package bench;

import com.thealgorithms.backtracking.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class BacktrackingBenchmark {

  // -- N-Queens --

  @State(Scope.Benchmark)
  public static class NQueensState {
    @Param({"4", "5", "6", "7", "8"})
    public int nQueens;
  }

  @Benchmark
  public List<List<String>> nQueensSolver(NQueensState state) {
    return NQueens.getNQueensArrangements(state.nQueens);
  }

  // -- Parentheses Generation --

  @State(Scope.Benchmark)
  public static class ParenthesesState {
    @Param({"3", "4", "5", "6"})
    public int nParens;
  }

  @Benchmark
  public List<String> generateParentheses(ParenthesesState state) {
    return ParenthesesGenerator.generateParentheses(state.nParens);
  }

  // -- Combinations --

  @State(Scope.Benchmark)
  public static class CombinationsState {
    @Param({"5", "6", "7", "8", "9"})
    public int nCombinations;
  }

  @Benchmark
  public void generateCombinations(CombinationsState state, Blackhole bh) {
    Integer[] arr = new Integer[state.nCombinations];
    for (int i = 0; i < state.nCombinations; i++) {
      arr[i] = i;
    }
    bh.consume(Combination.combination(arr, 3));
  }

  // -- Permutations --

  @State(Scope.Benchmark)
  public static class PermutationsState {
    @Param({"3", "4", "5", "6", "7"})
    public int nPermutations;
  }

  @Benchmark
  public void permutations(PermutationsState state, Blackhole bh) {
    Integer[] arr = new Integer[state.nPermutations];
    for (int i = 0; i < state.nPermutations; i++) {
      arr[i] = i;
    }
    bh.consume(Permutation.permutation(arr));
  }

  // -- Sudoku Solver --

  @State(Scope.Benchmark)
  public static class SudokuState {
    public int[][] sudokuBoard;

    @Setup(Level.Invocation)
    public void setupSudoku() {
      sudokuBoard =
          new int[][] {
            {3, 0, 6, 5, 0, 8, 4, 0, 0},
            {5, 2, 0, 0, 0, 0, 0, 0, 0},
            {0, 8, 7, 0, 0, 0, 0, 3, 1},
            {0, 0, 3, 0, 1, 0, 0, 8, 0},
            {9, 0, 0, 8, 6, 3, 0, 0, 5},
            {0, 5, 0, 0, 9, 0, 6, 0, 0},
            {1, 3, 0, 0, 0, 0, 2, 5, 0},
            {0, 0, 0, 0, 0, 0, 0, 7, 4},
            {0, 0, 5, 2, 0, 6, 3, 0, 0},
          };
    }
  }

  @Benchmark
  public boolean sudokuSolver(SudokuState state) {
    return SudokuSolver.solveSudoku(state.sudokuBoard);
  }

  // -- Subsequences --

  @State(Scope.Benchmark)
  public static class SubsequencesState {
    @Param({"8", "10", "12"})
    public int nSubsequences;
  }

  @Benchmark
  public void generateSubsequences(SubsequencesState state, Blackhole bh) {
    List<Integer> seq = new java.util.ArrayList<>();
    for (int i = 0; i < state.nSubsequences; i++) {
      seq.add(i);
    }
    bh.consume(SubsequenceFinder.generateAll(seq));
  }
}
