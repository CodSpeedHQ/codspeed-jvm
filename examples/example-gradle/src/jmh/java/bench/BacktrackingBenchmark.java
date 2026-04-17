package bench;

import com.thealgorithms.backtracking.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BacktrackingBenchmark {

  // -- N-Queens --

  @State(Scope.Benchmark)
  public static class NQueensState {
    @Param({"8"})
    public int nQueens;
  }

  @Benchmark
  public List<List<String>> nQueensSolver(NQueensState state) {
    return NQueens.getNQueensArrangements(state.nQueens);
  }

  // -- Parentheses Generation --

  @State(Scope.Benchmark)
  public static class ParenthesesState {
    @Param({"6"})
    public int nParens;
  }

  @Benchmark
  public List<String> generateParentheses(ParenthesesState state) {
    return ParenthesesGenerator.generateParentheses(state.nParens);
  }

  // -- Combinations --

  @State(Scope.Benchmark)
  public static class CombinationsState {
    @Param({"9"})
    public int nCombinations;

    public Integer[] arr;

    @Setup(Level.Trial)
    public void setup() {
      arr = new Integer[nCombinations];
      for (int i = 0; i < nCombinations; i++) {
        arr[i] = i;
      }
    }
  }

  @Benchmark
  public void generateCombinations(CombinationsState state, Blackhole bh) {
    bh.consume(Combination.combination(state.arr, 3));
  }

  // -- Permutations --

  @State(Scope.Benchmark)
  public static class PermutationsState {
    @Param({"7"})
    public int nPermutations;

    public Integer[] arr;

    @Setup(Level.Trial)
    public void setup() {
      arr = new Integer[nPermutations];
      for (int i = 0; i < nPermutations; i++) {
        arr[i] = i;
      }
    }
  }

  @Benchmark
  public void permutations(PermutationsState state, Blackhole bh) {
    bh.consume(Permutation.permutation(state.arr));
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
    @Param({"12"})
    public int nSubsequences;

    public List<Integer> seq;

    @Setup(Level.Trial)
    public void setup() {
      seq = new ArrayList<>(nSubsequences);
      for (int i = 0; i < nSubsequences; i++) {
        seq.add(i);
      }
    }
  }

  @Benchmark
  public void generateSubsequences(SubsequencesState state, Blackhole bh) {
    bh.consume(SubsequenceFinder.generateAll(state.seq));
  }
}
