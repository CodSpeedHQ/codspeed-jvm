/*
 * perf_map_agent.c — JVMTI agent that writes /tmp/perf-<pid>.map
 *
 * Hooks CompiledMethodLoad events and writes perf map entries with
 * absolute source file paths resolved via git root discovery.
 *
 * Usage:
 *   java -agentpath:libperf_map_agent.so[=file=<path>] ...
 *
 * Output format (standard perf map):
 *   With source path: <hex_addr> <hex_size> <absolute_source_path>::<class.name>.<method>
 *   Without source:   <hex_addr> <hex_size> <class.name>.<method>
 */

#define _GNU_SOURCE

#include <jvmti.h>

#include <dirent.h>
#include <limits.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

/* -------------------------------------------------------------------------- */
/* Source file cache                                                           */
/* -------------------------------------------------------------------------- */

typedef struct cache_entry {
  char *key;   /* class-relative path, e.g. "com/example/Foo.java" */
  char *value; /* absolute path on disk; empty string means "not found" */
  struct cache_entry *next;
} cache_entry_t;

static cache_entry_t *source_cache = NULL;
static pthread_mutex_t cache_lock = PTHREAD_MUTEX_INITIALIZER;

static const char *cache_lookup(const char *key) {
  cache_entry_t *e;
  for (e = source_cache; e; e = e->next) {
    if (strcmp(e->key, key) == 0) {
      return e->value;
    }
  }
  return NULL;
}

static const char *cache_insert(const char *key, const char *value) {
  cache_entry_t *e = malloc(sizeof(*e));
  if (!e) {
    return NULL;
  }
  e->key = strdup(key);
  e->value = strdup(value);
  if (!e->key || !e->value) {
    free(e->key);
    free(e->value);
    free(e);
    return NULL;
  }
  e->next = source_cache;
  source_cache = e;
  return e->value;
}

/* -------------------------------------------------------------------------- */
/* Git root discovery                                                         */
/* -------------------------------------------------------------------------- */

static char git_root[PATH_MAX];
static pthread_once_t git_root_once = PTHREAD_ONCE_INIT;

static void find_git_root(void) {
  char cwd[PATH_MAX];
  if (!getcwd(cwd, sizeof(cwd))) {
    git_root[0] = '/';
    git_root[1] = '\0';
    return;
  }

  char path[PATH_MAX];
  strncpy(path, cwd, PATH_MAX - 1);
  path[PATH_MAX - 1] = '\0';

  while (path[0] != '\0') {
    char test[PATH_MAX];
    snprintf(test, sizeof(test), "%s/.git", path);

    struct stat st;
    if (stat(test, &st) == 0) {
      strncpy(git_root, path, PATH_MAX - 1);
      git_root[PATH_MAX - 1] = '\0';
      return;
    }

    /* Walk up to parent directory */
    char *slash = strrchr(path, '/');
    if (!slash || slash == path) {
      break;
    }
    *slash = '\0';
  }

  /* Fall back to CWD */
  strncpy(git_root, cwd, PATH_MAX - 1);
  git_root[PATH_MAX - 1] = '\0';
}

/* -------------------------------------------------------------------------- */
/* Recursive file search                                                      */
/* -------------------------------------------------------------------------- */

static int should_skip_dir(const char *name) {
  if (name[0] == '.') {
    return 1;
  }
  if (strcmp(name, "build") == 0 || strcmp(name, "target") == 0 ||
      strcmp(name, "node_modules") == 0) {
    return 1;
  }
  return 0;
}

/*
 * Search for a file whose path ends with `suffix` under `dir`.
 * Returns 1 if found (result written to `out`), 0 otherwise.
 */
static int find_file_recursive(const char *dir, const char *suffix,
                               char *out, size_t out_size) {
  DIR *d = opendir(dir);
  if (!d) {
    return 0;
  }

  struct dirent *ent;
  while ((ent = readdir(d)) != NULL) {
    if (strcmp(ent->d_name, ".") == 0 || strcmp(ent->d_name, "..") == 0) {
      continue;
    }

    char full_path[PATH_MAX];
    snprintf(full_path, sizeof(full_path), "%s/%s", dir, ent->d_name);

    struct stat st;
    if (stat(full_path, &st) != 0) {
      continue;
    }

    if (S_ISDIR(st.st_mode)) {
      if (should_skip_dir(ent->d_name)) {
        continue;
      }
      if (find_file_recursive(full_path, suffix, out, out_size)) {
        closedir(d);
        return 1;
      }
    } else if (S_ISREG(st.st_mode)) {
      size_t full_len = strlen(full_path);
      size_t suffix_len = strlen(suffix);
      if (full_len >= suffix_len &&
          strcmp(full_path + full_len - suffix_len, suffix) == 0) {
        strncpy(out, full_path, out_size - 1);
        out[out_size - 1] = '\0';
        closedir(d);
        return 1;
      }
    }
  }

  closedir(d);
  return 0;
}

/* -------------------------------------------------------------------------- */
/* Source file resolution                                                      */
/* -------------------------------------------------------------------------- */

/*
 * Resolve a class-relative path (e.g. "com/example/Foo.java") to an absolute
 * path on disk by searching from the git root.
 *
 * Returns a pointer that remains valid for the process lifetime.
 * Empty string means "source file not found on disk".
 */
static const char *resolve_source_file(const char *relative_path) {
  pthread_mutex_lock(&cache_lock);

  const char *cached = cache_lookup(relative_path);
  if (cached) {
    pthread_mutex_unlock(&cache_lock);
    return cached;
  }

  pthread_once(&git_root_once, find_git_root);

  /* Build the suffix to search for: "/com/example/Foo.java" */
  char suffix[PATH_MAX];
  snprintf(suffix, sizeof(suffix), "/%s", relative_path);

  char found[PATH_MAX];
  const char *result = NULL;
  if (find_file_recursive(git_root, suffix, found, sizeof(found))) {
    result = cache_insert(relative_path, found);
  } else {
    /* Source not on disk (JDK class, dependency, etc.) */
    result = cache_insert(relative_path, "");
  }

  pthread_mutex_unlock(&cache_lock);
  return result ? result : "";
}

/* -------------------------------------------------------------------------- */
/* Perf map file                                                              */
/* -------------------------------------------------------------------------- */

static FILE *map_file = NULL;
static pthread_mutex_t map_lock = PTHREAD_MUTEX_INITIALIZER;

static int open_map_file(const char *path) {
  pthread_mutex_lock(&map_lock);
  if (map_file) {
    pthread_mutex_unlock(&map_lock);
    return 0; /* already open */
  }
  if (path && path[0] != '\0') {
    map_file = fopen(path, "w");
  } else {
    char default_path[PATH_MAX];
    snprintf(default_path, sizeof(default_path), "/tmp/perf-%d.map", getpid());
    map_file = fopen(default_path, "w");
  }
  if (map_file) {
    setvbuf(map_file, NULL, _IOLBF, 0); /* line-buffered */
  }
  int result = map_file ? 0 : -1;
  pthread_mutex_unlock(&map_lock);
  return result;
}

static void write_entry(const void *code_addr, int code_size,
                         const char *symbol) {
  pthread_mutex_lock(&map_lock);
  if (!map_file) {
    pthread_mutex_unlock(&map_lock);
    return;
  }
  fprintf(map_file, "%lx %x %s\n", (unsigned long)(uintptr_t)code_addr,
          (unsigned int)code_size, symbol);
  pthread_mutex_unlock(&map_lock);
}

/* -------------------------------------------------------------------------- */
/* Class signature → relative source path                                     */
/* -------------------------------------------------------------------------- */

/*
 * Convert a JVMTI class signature to a relative source path.
 *
 * class_sig:   "Lcom/example/Foo;"  or  "Lcom/example/Foo$Inner;"
 * source_file: "Foo.java" (from GetSourceFileName, may be NULL)
 *
 * Result: "com/example/Foo.java"
 */
static void build_relative_path(const char *class_sig, const char *source_file,
                                char *out, size_t out_size) {
  /* Strip leading 'L' and trailing ';' */
  const char *start = class_sig;
  if (*start == 'L') {
    start++;
  }
  size_t len = strlen(start);
  if (len > 0 && start[len - 1] == ';') {
    len--;
  }

  /*
   * Find the package prefix boundary: the last '/' before any '$'.
   * Lambda/inner classes like "com/example/Foo$$Lambda/0x..." contain
   * '/' after the '$', which must not be treated as a package separator.
   */
  size_t effective_len = len;
  for (size_t i = 0; i < len; i++) {
    if (start[i] == '$') {
      effective_len = i;
      break;
    }
  }

  const char *last_slash = NULL;
  for (size_t i = 0; i < effective_len; i++) {
    if (start[i] == '/') {
      last_slash = start + i;
    }
  }

  if (source_file && source_file[0] != '\0') {
    /* Use package prefix + source file name from debug info */
    if (last_slash) {
      size_t prefix_len = (size_t)(last_slash - start) + 1; /* include the '/' */
      if (prefix_len >= out_size) {
        prefix_len = out_size - 1;
      }
      memcpy(out, start, prefix_len);
      strncpy(out + prefix_len, source_file, out_size - prefix_len - 1);
      out[out_size - 1] = '\0';
    } else {
      strncpy(out, source_file, out_size - 1);
      out[out_size - 1] = '\0';
    }
  } else {
    /* Derive from class name: strip inner class ($), append .java */
    char class_path[PATH_MAX];
    if (len >= sizeof(class_path)) {
      len = sizeof(class_path) - 1;
    }
    memcpy(class_path, start, len);
    class_path[len] = '\0';

    /* Strip inner class: "com/example/Foo$Inner" → "com/example/Foo" */
    char *dollar = strchr(class_path, '$');
    if (dollar) {
      *dollar = '\0';
    }

    snprintf(out, out_size, "%s.java", class_path);
  }
}

/*
 * Convert a JVMTI class signature to a dotted class name.
 *
 * "Lcom/example/Foo;" → "com.example.Foo"
 */
static void class_sig_to_dotted(const char *class_sig, char *out,
                                size_t out_size) {
  const char *start = class_sig;
  if (*start == 'L') {
    start++;
  }
  size_t len = strlen(start);
  if (len > 0 && start[len - 1] == ';') {
    len--;
  }
  if (len >= out_size) {
    len = out_size - 1;
  }
  memcpy(out, start, len);
  out[len] = '\0';

  /* Replace '/' with '.' */
  for (size_t i = 0; i < len; i++) {
    if (out[i] == '/') {
      out[i] = '.';
    }
  }
}

/* -------------------------------------------------------------------------- */
/* JVMTI callbacks                                                            */
/* -------------------------------------------------------------------------- */

static void JNICALL on_compiled_method_load(jvmtiEnv *jvmti, jmethodID method,
                                            jint code_size,
                                            const void *code_addr,
                                            jint map_length,
                                            const jvmtiAddrLocationMap *map,
                                            const void *compile_info) {
  (void)map_length;
  (void)map;
  (void)compile_info;

  if (code_size == 0) {
    return;
  }

  jclass decl_class;
  if ((*jvmti)->GetMethodDeclaringClass(jvmti, method, &decl_class) !=
      JVMTI_ERROR_NONE) {
    return;
  }

  char *class_sig = NULL;
  char *method_name = NULL;
  char *source_file = NULL;

  (*jvmti)->GetClassSignature(jvmti, decl_class, &class_sig, NULL);
  (*jvmti)->GetMethodName(jvmti, method, &method_name, NULL, NULL);
  (*jvmti)->GetSourceFileName(jvmti, decl_class, &source_file);

  if (!class_sig || !method_name) {
    goto cleanup;
  }

  char relative_path[PATH_MAX];
  build_relative_path(class_sig, source_file, relative_path,
                      sizeof(relative_path));

  const char *absolute_path = resolve_source_file(relative_path);

  char dotted_class[2048];
  class_sig_to_dotted(class_sig, dotted_class, sizeof(dotted_class));

  char symbol[PATH_MAX + 2048 + 256];
  if (absolute_path[0] != '\0') {
    snprintf(symbol, sizeof(symbol), "%s::%s.%s", absolute_path, dotted_class,
             method_name);
  } else {
    snprintf(symbol, sizeof(symbol), "%s.%s", dotted_class, method_name);
  }

  write_entry(code_addr, code_size, symbol);

cleanup:
  if (class_sig) {
    (*jvmti)->Deallocate(jvmti, (unsigned char *)class_sig);
  }
  if (method_name) {
    (*jvmti)->Deallocate(jvmti, (unsigned char *)method_name);
  }
  if (source_file) {
    (*jvmti)->Deallocate(jvmti, (unsigned char *)source_file);
  }
}

static void JNICALL on_dynamic_code_generated(jvmtiEnv *jvmti, const char *name,
                                              const void *code_addr,
                                              jint code_size) {
  (void)jvmti;

  if (code_size == 0) {
    return;
  }

  write_entry(code_addr, code_size, name);
}

/*
 * Flush deferred CompiledMethodLoad events at VM shutdown.
 *
 * Modern JVMs defer these events to a service thread. Short-lived programs
 * can exit before the queue is drained, causing compiled user methods to be
 * missing from the perf map. GenerateEvents forces synchronous delivery.
 */
static void JNICALL on_vm_death(jvmtiEnv *jvmti, JNIEnv *jni_env) {
  (void)jni_env;
  (*jvmti)->GenerateEvents(jvmti, JVMTI_EVENT_COMPILED_METHOD_LOAD);
  (*jvmti)->GenerateEvents(jvmti, JVMTI_EVENT_DYNAMIC_CODE_GENERATED);
}

/* -------------------------------------------------------------------------- */
/* Agent option parsing                                                       */
/* -------------------------------------------------------------------------- */

static char custom_map_path[PATH_MAX];

static void parse_options(const char *options) {
  custom_map_path[0] = '\0';
  if (!options || options[0] == '\0') {
    return;
  }

  const char *file_prefix = "file=";
  const char *p = strstr(options, file_prefix);
  if (p) {
    p += strlen(file_prefix);
    /* Read until comma or end of string */
    size_t i = 0;
    while (*p && *p != ',' && i < sizeof(custom_map_path) - 1) {
      custom_map_path[i++] = *p++;
    }
    custom_map_path[i] = '\0';
  }
}

/* -------------------------------------------------------------------------- */
/* Agent init (shared by OnLoad and OnAttach)                                 */
/* -------------------------------------------------------------------------- */

static jint agent_init(JavaVM *jvm, char *options, int is_attach) {
  parse_options(options);

  jvmtiEnv *jvmti = NULL;
  if ((*jvm)->GetEnv(jvm, (void **)&jvmti, JVMTI_VERSION_1) != JNI_OK) {
    fprintf(stderr, "perf_map_agent: JVMTI version 1 not supported\n");
    return -1;
  }

  /* Request capabilities */
  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_generate_compiled_method_load_events = 1;
  caps.can_get_source_file_name = 1;

  if ((*jvmti)->AddCapabilities(jvmti, &caps) != JVMTI_ERROR_NONE) {
    fprintf(stderr, "perf_map_agent: failed to add capabilities\n");
    return -1;
  }

  /* Register callbacks */
  jvmtiEventCallbacks callbacks;
  memset(&callbacks, 0, sizeof(callbacks));
  callbacks.CompiledMethodLoad = on_compiled_method_load;
  callbacks.DynamicCodeGenerated = on_dynamic_code_generated;
  callbacks.VMDeath = on_vm_death;

  if ((*jvmti)->SetEventCallbacks(jvmti, &callbacks, sizeof(callbacks)) !=
      JVMTI_ERROR_NONE) {
    fprintf(stderr, "perf_map_agent: failed to set event callbacks\n");
    return -1;
  }

  /* Enable event notifications */
  (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                                     JVMTI_EVENT_COMPILED_METHOD_LOAD, NULL);
  (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                                     JVMTI_EVENT_DYNAMIC_CODE_GENERATED, NULL);
  (*jvmti)->SetEventNotificationMode(jvmti, JVMTI_ENABLE,
                                     JVMTI_EVENT_VM_DEATH, NULL);

  /* Open the perf map file */
  if (open_map_file(custom_map_path) != 0) {
    fprintf(stderr, "perf_map_agent: failed to open map file\n");
    return -1;
  }

  /* In attach mode, generate events for already-compiled methods */
  if (is_attach) {
    (*jvmti)->GenerateEvents(jvmti, JVMTI_EVENT_COMPILED_METHOD_LOAD);
    (*jvmti)->GenerateEvents(jvmti, JVMTI_EVENT_DYNAMIC_CODE_GENERATED);
  }

  return 0;
}

/* -------------------------------------------------------------------------- */
/* Agent entry points                                                         */
/* -------------------------------------------------------------------------- */

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options,
                                    void *reserved) {
  (void)reserved;
  return agent_init(jvm, options, 0);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM *jvm, char *options,
                                      void *reserved) {
  (void)reserved;
  return agent_init(jvm, options, 1);
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *jvm) {
  (void)jvm;

  pthread_mutex_lock(&map_lock);
  if (map_file) {
    fflush(map_file);
    fclose(map_file);
    map_file = NULL;
  }
  pthread_mutex_unlock(&map_lock);

  /* Free the cache */
  pthread_mutex_lock(&cache_lock);
  cache_entry_t *e = source_cache;
  while (e) {
    cache_entry_t *next = e->next;
    free(e->key);
    free(e->value);
    free(e);
    e = next;
  }
  source_cache = NULL;
  pthread_mutex_unlock(&cache_lock);
}
