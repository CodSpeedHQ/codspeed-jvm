package io.codspeed.instrument_hooks;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class InstrumentHooks {

  private static final boolean nativeAvailable;

  static {
    boolean loaded = false;
    try {
      loadNativeLibrary("/libinstrument_hooks_jni.so");
      loaded = true;
    } catch (IOException | UnsatisfiedLinkError e) {
      // Native library not available (e.g. non-Linux platform).
      // All methods will gracefully no-op.
    }
    nativeAvailable = loaded;
  }

  private static void loadNativeLibrary(String path) throws IOException {
    try (InputStream in = InstrumentHooks.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new FileNotFoundException("Native library resource not found: " + path);
      }

      File tempDir = Files.createTempDirectory("codspeed").toFile();
      tempDir.deleteOnExit();

      String filename = path.substring(path.lastIndexOf('/') + 1);
      File tempLib = new File(tempDir, filename);

      Files.copy(in, tempLib.toPath(), StandardCopyOption.REPLACE_EXISTING);

      System.load(tempLib.getAbsolutePath());
      tempLib.delete();
      tempDir.delete();
    }
  }

  private static class Holder {
    static final InstrumentHooks INSTANCE = new InstrumentHooks();
  }

  public static InstrumentHooks getInstance() {
    return Holder.INSTANCE;
  }

  private long nativePtr;

  // Marker type constants
  public static final int MARKER_TYPE_SAMPLE_START = 0;
  public static final int MARKER_TYPE_SAMPLE_END = 1;
  public static final int MARKER_TYPE_BENCHMARK_START = 2;
  public static final int MARKER_TYPE_BENCHMARK_END = 3;

  // Feature constants
  public static final int FEATURE_DISABLE_CALLGRIND_MARKERS = 0;

  // Static methods (no instance needed)
  public static long currentTimestamp() {
    return nativeAvailable ? nativeCurrentTimestamp() : 0L;
  }

  private static native long nativeCurrentTimestamp();

  public static native void setFeature(int feature, boolean enabled);

  // Native lifecycle methods
  private static native long nativeInit();

  private static native void nativeDeinit(long ptr);

  // Native instance methods
  private native boolean nativeIsInstrumented(long ptr);

  private native void nativeStartBenchmark(long ptr);

  private native void nativeStopBenchmark(long ptr);

  private native void nativeSetExecutedBenchmark(long ptr, int pid, String uri);

  private native void nativeSetIntegration(long ptr, String name, String version);

  private native void nativeAddMarker(long ptr, int pid, int markerType, long timestamp);

  private native void nativeStartBenchmarkInline(long ptr);

  private native void nativeStopBenchmarkInline(long ptr);

  private InstrumentHooks() {
    if (!nativeAvailable) {
      nativePtr = 0;
      return;
    }
    nativePtr = nativeInit();
    if (nativePtr == 0) {
      throw new RuntimeException("Failed to initialize InstrumentHooks");
    }
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (nativePtr != 0) {
                    nativeDeinit(nativePtr);
                    nativePtr = 0;
                  }
                }));
  }

  public boolean isInstrumented() {
    if (!nativeAvailable) return false;
    return nativeIsInstrumented(nativePtr);
  }

  public void startBenchmark() {
    if (!nativeAvailable) return;
    nativeStartBenchmark(nativePtr);
  }

  public void stopBenchmark() {
    if (!nativeAvailable) return;
    nativeStopBenchmark(nativePtr);
  }

  public void setExecutedBenchmark(int pid, String uri) {
    if (!nativeAvailable) return;
    nativeSetExecutedBenchmark(nativePtr, pid, uri);
  }

  public void setIntegration(String name, String version) {
    if (!nativeAvailable) return;
    nativeSetIntegration(nativePtr, name, version);
  }

  public void addMarker(int pid, int markerType, long timestamp) {
    if (!nativeAvailable) return;
    nativeAddMarker(nativePtr, pid, markerType, timestamp);
  }

  public void startBenchmarkInline() {
    if (!nativeAvailable) return;
    nativeStartBenchmarkInline(nativePtr);
  }

  public void stopBenchmarkInline() {
    if (!nativeAvailable) return;
    nativeStopBenchmarkInline(nativePtr);
  }
}
