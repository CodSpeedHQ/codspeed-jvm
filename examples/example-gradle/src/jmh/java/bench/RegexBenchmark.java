package bench;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class RegexBenchmark {

  // Complex pattern with alternations, named groups, lookahead, and quantifiers.
  // Matches things like email addresses, URLs, ISO dates, and IPv4 addresses.
  private static final String COMPLEX_PATTERN =
      "(?<email>[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"
          + "|(?<url>https?://[a-zA-Z0-9.-]+(?:/[a-zA-Z0-9_.~!$&'()*+,;=:@/-]*)*(?:\\?[a-zA-Z0-9_.~!$&'()*+,;=:@/?-]*)?)"
          + "|(?<date>\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])T(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d+)?Z?)"
          + "|(?<ipv4>(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?))";

  // Pattern that causes significant backtracking on near-miss inputs.
  // Nested quantifiers: (a+)+ applied to a string of 'a's followed by a non-matching char.
  private static final String BACKTRACK_PATTERN = "^(a+)+b$";

  @Param({"20", "24"})
  private int backtrackLength;

  private String scanInput;
  private String backtrackInput;
  private Pattern[] scanPatterns;

  @Setup
  public void setup() {
    // Build a realistic mixed-content text for scanning
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 50; i++) {
      sb.append("Contact user").append(i).append("@example.com for details. ");
      sb.append("Visit https://docs.example.com/api/v2/resource?page=")
          .append(i)
          .append("&limit=100 ");
      sb.append("Created at 2025-06-")
          .append(String.format("%02d", (i % 28) + 1))
          .append("T14:30:00.000Z ");
      sb.append("Server at 192.168.")
          .append(i % 256)
          .append(".")
          .append((i * 7) % 256)
          .append(" responded. ");
      sb.append("Some filler text with no matches here to keep the scanner busy. ");
    }
    scanInput = sb.toString();

    // Near-miss input for backtracking: all 'a's with no trailing 'b'
    backtrackInput = "a".repeat(backtrackLength) + "c";

    // Multiple patterns for the tokenizer-style scan
    scanPatterns =
        new Pattern[] {
          Pattern.compile("(?<email>[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"),
          Pattern.compile(
              "(?<url>https?://[a-zA-Z0-9.-]+(?:/[a-zA-Z0-9_.~!$&'()*+,;=:@/-]*)*(?:\\?[a-zA-Z0-9_.~!$&'()*+,;=:@/?-]*)?)"),
          Pattern.compile(
              "\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])T(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d+)?Z?"),
          Pattern.compile(
              "(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)"),
        };
  }

  /**
   * Compiles a complex pattern from scratch and matches against the full input. Forces the regex
   * compiler path every iteration for deep compilation frames.
   */
  @Benchmark
  public int compileAndMatch() {
    Pattern p = Pattern.compile(COMPLEX_PATTERN);
    Matcher m = p.matcher(scanInput);
    int count = 0;
    while (m.find()) {
      count++;
    }
    return count;
  }

  /**
   * Exercises catastrophic backtracking with nested quantifiers on a near-miss input. Produces deep
   * recursive matching stacks.
   */
  @Benchmark
  public boolean backtrackHeavy() {
    Pattern p = Pattern.compile(BACKTRACK_PATTERN);
    return p.matcher(backtrackInput).matches();
  }

  /**
   * Applies multiple pre-compiled patterns in sequence over a large text, simulating a
   * tokenizer/lexer. Exercises repeated Matcher.find() loops and produces varied frames across
   * different pattern types.
   */
  @Benchmark
  public void multiPatternScan(Blackhole bh) {
    for (Pattern pattern : scanPatterns) {
      Matcher m = pattern.matcher(scanInput);
      int count = 0;
      while (m.find()) {
        bh.consume(m.group());
        count++;
      }
      bh.consume(count);
    }
  }
}
