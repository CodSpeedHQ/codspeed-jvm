package io.codspeed.perf_map_agent;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Integration tests for the perf-map JVMTI agent.
 *
 * <p>Launches a child JVM with the agent loaded, triggers JIT compilation, and validates the output
 * perf map file format and content.
 *
 * <p>Requires the native agent library to be built (Linux only, via Gradle). Tests skip gracefully
 * when the library is not available.
 */
public class PerfMapAgentTest {

  @Test
  public void testAgentProducesValidPerfMap() throws Exception {
    assumeTrue(
        "perf-map agent is Linux-only",
        System.getProperty("os.name").toLowerCase().contains("linux"));

    Path agentLib = extractAgentLib();
    assumeTrue("Native agent lib not available (build with Gradle first)", agentLib != null);

    Path mapFile = Files.createTempFile("perf-map-test-", ".map");
    Files.deleteIfExists(mapFile); // agent will create it

    try {
      int exit = runWorkerWithAgent(agentLib, mapFile);
      assertEquals("Child JVM should exit cleanly", 0, exit);

      assertTrue("Map file should be created", Files.exists(mapFile));

      List<String> lines =
          Files.readAllLines(mapFile).stream()
              .filter(l -> !l.trim().isEmpty())
              .collect(Collectors.toList());
      assertFalse("Map file should contain entries", lines.isEmpty());

      // Validate perf map format: "<hex_addr> <hex_size> <symbol>"
      for (String line : lines) {
        String[] parts = line.split(" ", 3);
        assertEquals("Line format: addr size symbol: " + line, 3, parts.length);
        Long.parseUnsignedLong(parts[0], 16);
        Integer.parseUnsignedInt(parts[1], 16);
        assertFalse("Symbol must not be empty: " + line, parts[2].trim().isEmpty());
      }

      List<String> symbols =
          lines.stream().map(l -> l.split(" ", 3)[2]).collect(Collectors.toList());

      // Compiled Java methods should have source-path::class.method format.
      boolean hasSourceEntries =
          symbols.stream().anyMatch(s -> s.matches(".*\\.java::.+\\..+"));
      assertTrue("Should contain entries with source paths", hasSourceEntries);

      // Missing sources (JDK/dependency classes) must not be emitted as guessed
      // java/... .java paths.
      boolean hasGuessedJdkSourcePath =
          symbols.stream().anyMatch(s -> s.matches("^(java|jdk)/.*\\.java::.*"));
      assertFalse(
          "Should not emit guessed java/... .java:: source paths", hasGuessedJdkSourcePath);

      // Missing-source classes should keep the same shape with an empty source-path slot.
      boolean hasRawJdkFallback =
          symbols.stream().anyMatch(s -> s.matches("^::java\\.lang\\.String\\.hashCode$"));
      assertTrue(
          "Should contain fallback symbol with empty source-path slot", hasRawJdkFallback);
    } finally {
      Files.deleteIfExists(mapFile);
      cleanupLib(agentLib);
    }
  }

  @Test
  public void testAgentDefaultMapPath() throws Exception {
    assumeTrue(
        "perf-map agent is Linux-only",
        System.getProperty("os.name").toLowerCase().contains("linux"));

    Path agentLib = extractAgentLib();
    assumeTrue("Native agent lib not available", agentLib != null);

    try {
      // Run without file= option — agent creates /tmp/perf-<pid>.map
      String javaBin = System.getProperty("java.home") + "/bin/java";
      ProcessBuilder pb =
          new ProcessBuilder(
              javaBin,
              "-agentpath:" + agentLib,
              "-cp",
              System.getProperty("java.class.path"),
              WorkerMain.class.getName());
      pb.redirectErrorStream(true);
      Process p = pb.start();
      drain(p);
      int exit = p.waitFor();

      // Exit code 0 means the agent loaded and ran without errors
      assertEquals("Agent should load successfully with default path", 0, exit);
    } finally {
      cleanupLib(agentLib);
    }
  }

  /* ---- helpers ---- */

  private static Path extractAgentLib() throws IOException {
    try (InputStream in =
        PerfMapAgentTest.class.getResourceAsStream("/libperf_map_agent.so")) {
      if (in == null) {
        return null;
      }
      Path dir = Files.createTempDirectory("perf_map_agent_test");
      Path lib = dir.resolve("libperf_map_agent.so");
      Files.copy(in, lib, StandardCopyOption.REPLACE_EXISTING);
      return lib;
    }
  }

  private static void cleanupLib(Path lib) {
    if (lib == null) {
      return;
    }
    try {
      Files.deleteIfExists(lib);
      Files.deleteIfExists(lib.getParent());
    } catch (IOException ignored) {
    }
  }

  private static int runWorkerWithAgent(Path agentLib, Path mapFile)
      throws IOException, InterruptedException {
    String javaBin = System.getProperty("java.home") + "/bin/java";
    ProcessBuilder pb =
        new ProcessBuilder(
            javaBin,
            "-Xbatch",
            "-XX:CompileThreshold=100",
            "-agentpath:" + agentLib + "=file=" + mapFile,
            "-cp",
            System.getProperty("java.class.path"),
            WorkerMain.class.getName());
    pb.redirectErrorStream(true);
    Process p = pb.start();
    drain(p);
    return p.waitFor();
  }

  private static void drain(Process p) throws IOException {
    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
      while (r.readLine() != null) {}
    }
  }

  /** Minimal workload to trigger JIT compilation, run as a child process. */
  public static class WorkerMain {
    public static void main(String[] args) {
      long sum = 0;
      for (int i = 0; i < 500_000; i++) {
        sum += fib(15);
        sum += jdkHash(i);
      }
      if (sum == 0) {
        System.out.println("unexpected");
      }
    }

    private static int jdkHash(int i) {
      return Integer.toString(i & 1023).hashCode();
    }

    private static long fib(int n) {
      if (n <= 1) {
        return n;
      }
      return fib(n - 1) + fib(n - 2);
    }
  }
}
