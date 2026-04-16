#include "core.h"
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void throw_runtime_exception(JNIEnv *env, const char *msg) {
  jclass cls = (*env)->FindClass(env, "java/lang/RuntimeException");
  if (cls == NULL) {
    fprintf(stderr, "FATAL: Could not find java/lang/RuntimeException\n");
    abort();
  }
  (*env)->ThrowNew(env, cls, msg);
}

// --- Static methods ---

JNIEXPORT jlong JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_currentTimestamp(
    JNIEnv *env, jclass cls) {
  (void)env;
  (void)cls;
  return (jlong)instrument_hooks_current_timestamp();
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_setFeature(
    JNIEnv *env, jclass cls, jint feature, jboolean enabled) {
  (void)env;
  (void)cls;
  instrument_hooks_set_feature((instrument_hooks_feature_t)feature,
                               (bool)enabled);
}

// --- Lifecycle ---

JNIEXPORT jlong JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeInit(JNIEnv *env,
                                                              jclass cls) {
  (void)env;
  (void)cls;
  InstrumentHooks *hooks = instrument_hooks_init();
  return (jlong)(intptr_t)hooks;
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeDeinit(JNIEnv *env,
                                                                jclass cls,
                                                                jlong ptr) {
  (void)env;
  (void)cls;
  if (ptr != 0) {
    instrument_hooks_deinit((InstrumentHooks *)(intptr_t)ptr);
  }
}

// --- Instance methods ---

JNIEXPORT jboolean JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeIsInstrumented(
    JNIEnv *env, jobject obj, jlong ptr) {
  (void)env;
  (void)obj;
  return (jboolean)instrument_hooks_is_instrumented(
      (InstrumentHooks *)(intptr_t)ptr);
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeStartBenchmark(
    JNIEnv *env, jobject obj, jlong ptr) {
  (void)obj;
  uint8_t ret =
      instrument_hooks_start_benchmark((InstrumentHooks *)(intptr_t)ptr);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to start benchmark");
  }
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeStopBenchmark(
    JNIEnv *env, jobject obj, jlong ptr) {
  (void)obj;
  uint8_t ret =
      instrument_hooks_stop_benchmark((InstrumentHooks *)(intptr_t)ptr);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to stop benchmark");
  }
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeSetExecutedBenchmark(
    JNIEnv *env, jobject obj, jlong ptr, jint pid, jstring uri) {
  (void)obj;
  const char *uriStr = (*env)->GetStringUTFChars(env, uri, NULL);
  if (uriStr == NULL) {
    return; // OutOfMemoryError already thrown
  }
  uint8_t ret = instrument_hooks_set_executed_benchmark(
      (InstrumentHooks *)(intptr_t)ptr, (int32_t)pid, uriStr);
  (*env)->ReleaseStringUTFChars(env, uri, uriStr);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to set executed benchmark");
  }
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeSetIntegration(
    JNIEnv *env, jobject obj, jlong ptr, jstring name, jstring version) {
  (void)obj;
  const char *nameStr = (*env)->GetStringUTFChars(env, name, NULL);
  if (nameStr == NULL) {
    return;
  }
  const char *versionStr = (*env)->GetStringUTFChars(env, version, NULL);
  if (versionStr == NULL) {
    (*env)->ReleaseStringUTFChars(env, name, nameStr);
    return;
  }
  uint8_t ret = instrument_hooks_set_integration(
      (InstrumentHooks *)(intptr_t)ptr, nameStr, versionStr);
  (*env)->ReleaseStringUTFChars(env, version, versionStr);
  (*env)->ReleaseStringUTFChars(env, name, nameStr);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to set integration");
  }
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeAddMarker(
    JNIEnv *env, jobject obj, jlong ptr, jint pid, jint markerType,
    jlong timestamp) {
  (void)obj;
  uint8_t ret = instrument_hooks_add_marker((InstrumentHooks *)(intptr_t)ptr,
                                            (uint32_t)pid, (uint8_t)markerType,
                                            (uint64_t)timestamp);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to add marker");
  }
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeStartBenchmarkInline(
    JNIEnv *env, jobject obj, jlong ptr) {
  (void)obj;
  uint8_t ret =
      instrument_hooks_start_benchmark_inline((InstrumentHooks *)(intptr_t)ptr);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to start benchmark (inline)");
  }
}

JNIEXPORT void JNICALL
Java_io_codspeed_instrument_1hooks_InstrumentHooks_nativeStopBenchmarkInline(
    JNIEnv *env, jobject obj, jlong ptr) {
  (void)obj;
  uint8_t ret =
      instrument_hooks_stop_benchmark_inline((InstrumentHooks *)(intptr_t)ptr);
  if (ret != 0) {
    throw_runtime_exception(env, "Failed to stop benchmark (inline)");
  }
}
