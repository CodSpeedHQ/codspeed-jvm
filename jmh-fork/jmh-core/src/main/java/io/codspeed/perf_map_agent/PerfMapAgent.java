package io.codspeed.perf_map_agent;

import com.sun.tools.attach.VirtualMachine;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages the perf-map JVMTI agent that writes {@code /tmp/perf-<pid>.map} files with source-aware
 * JIT symbol information.
 *
 * <p>Two usage modes:
 *
 * <ul>
 *   <li><b>Startup:</b> Call {@link #getAgentPath()} to get a {@code -agentpath:} argument for
 *       forked JVM commands.
 *   <li><b>Attach:</b> Call {@link #attach(String, String)} to load the agent into a running JVM.
 * </ul>
 */
public class PerfMapAgent {

  private static final String LIB_RESOURCE = "/libperf_map_agent.so";
  private static volatile String cachedAgentPath;

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: PerfMapAgent <pid> [agent-options]");
      System.exit(1);
    }
    String pid = args[0];
    String options = args.length > 1 ? args[1] : "";
    attach(pid, options);
  }

  /** Attach the perf-map agent to the JVM with the given PID. */
  public static void attach(String pid, String options) throws Exception {
    String libPath = extractLibrary();
    VirtualMachine vm = VirtualMachine.attach(pid);
    try {
      vm.loadAgentPath(libPath, options);
    } finally {
      vm.detach();
    }
  }

  /**
   * Attach the perf-map agent to the current JVM. Requires {@code
   * -Djdk.attach.allowAttachSelf=true} (set by the CodSpeed runner via {@code JAVA_TOOL_OPTIONS}).
   */
  public static void attachToCurrentVM(String options) throws Exception {
    String pid = String.valueOf(ProcessHandle.current().pid());
    attach(pid, options);
  }

  /**
   * Returns the filesystem path to the native agent library, extracting it from classpath resources
   * on first call. Returns {@code null} if the library is not available (non-Linux, not built).
   *
   * <p>The returned path is suitable for use in {@code -agentpath:<path>} JVM arguments.
   */
  public static String getAgentPath() {
    if (cachedAgentPath != null) {
      return cachedAgentPath;
    }
    try {
      cachedAgentPath = extractLibrary();
    } catch (IOException e) {
      // Not available on this platform
    }
    return cachedAgentPath;
  }

  private static String extractLibrary() throws IOException {
    try (InputStream in = PerfMapAgent.class.getResourceAsStream(LIB_RESOURCE)) {
      if (in == null) {
        throw new FileNotFoundException(
            "Native library resource not found: "
                + LIB_RESOURCE
                + ". Build with Gradle on Linux first.");
      }
      File tempDir = Files.createTempDirectory("perf_map_agent").toFile();
      File tempLib = new File(tempDir, "libperf_map_agent.so");
      Files.copy(in, tempLib.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return tempLib.getAbsolutePath();
    }
  }
}
