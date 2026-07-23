/*
 * Pointer-bound adapters for libhegel's three by-value aggregate calls.
 *
 * jolt.ffi cannot currently describe C structs passed by value. Keeping the
 * aggregate layout inside C lets each target compiler apply its own ABI while
 * jolt passes only ordinary pointers.
 */
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#define JOLT_HEGEL_EXPORT __declspec(dllexport)
#else
#include <dlfcn.h>
#define JOLT_HEGEL_EXPORT __attribute__((visibility("default")))
#endif

typedef struct hegel_context_t hegel_context_t;
typedef struct hegel_test_case_t hegel_test_case_t;

/* libhegel's hegel_result_t is a C enum with an int-sized ABI. */
typedef int hegel_result_t;
enum { HEGEL_E_INVALID_ARG = -5 };

typedef struct {
    int32_t year;
    uint8_t month;
    uint8_t day;
} hegel_date_t;

typedef struct {
    uint8_t hour;
    uint8_t minute;
    uint8_t second;
    uint32_t microsecond;
} hegel_time_t;

typedef struct {
    hegel_date_t date;
    hegel_time_t time;
} hegel_datetime_t;

_Static_assert(sizeof(int) == 4, "libhegel requires a 32-bit C int ABI");
_Static_assert(sizeof(hegel_date_t) == 8, "unexpected hegel_date_t size");
_Static_assert(_Alignof(hegel_date_t) == 4,
               "unexpected hegel_date_t alignment");
_Static_assert(offsetof(hegel_date_t, year) == 0,
               "unexpected hegel_date_t.year offset");
_Static_assert(offsetof(hegel_date_t, month) == 4,
               "unexpected hegel_date_t.month offset");
_Static_assert(offsetof(hegel_date_t, day) == 5,
               "unexpected hegel_date_t.day offset");

_Static_assert(sizeof(hegel_time_t) == 8, "unexpected hegel_time_t size");
_Static_assert(_Alignof(hegel_time_t) == 4,
               "unexpected hegel_time_t alignment");
_Static_assert(offsetof(hegel_time_t, hour) == 0,
               "unexpected hegel_time_t.hour offset");
_Static_assert(offsetof(hegel_time_t, minute) == 1,
               "unexpected hegel_time_t.minute offset");
_Static_assert(offsetof(hegel_time_t, second) == 2,
               "unexpected hegel_time_t.second offset");
_Static_assert(offsetof(hegel_time_t, microsecond) == 4,
               "unexpected hegel_time_t.microsecond offset");

_Static_assert(sizeof(hegel_datetime_t) == 16,
               "unexpected hegel_datetime_t size");
_Static_assert(_Alignof(hegel_datetime_t) == 4,
               "unexpected hegel_datetime_t alignment");
_Static_assert(offsetof(hegel_datetime_t, date) == 0,
               "unexpected hegel_datetime_t.date offset");
_Static_assert(offsetof(hegel_datetime_t, time) == 8,
               "unexpected hegel_datetime_t.time offset");

typedef hegel_result_t (*generate_date_fn)(hegel_context_t *,
                                           hegel_test_case_t *, hegel_date_t,
                                           hegel_date_t, hegel_date_t *);
typedef hegel_result_t (*generate_time_fn)(hegel_context_t *,
                                           hegel_test_case_t *, hegel_time_t,
                                           hegel_time_t, hegel_time_t *);
typedef hegel_result_t (*generate_datetime_fn)(
    hegel_context_t *, hegel_test_case_t *, hegel_datetime_t,
    hegel_datetime_t, hegel_datetime_t *);

static generate_date_fn generate_date = NULL;
static generate_time_fn generate_time = NULL;
static generate_datetime_fn generate_datetime = NULL;
static char shim_error[256] = "shim has not been initialized";

#if defined(_WIN32)
static HMODULE hegel_module = NULL;
#else
static void *hegel_module = NULL;
#endif

JOLT_HEGEL_EXPORT const char *jolt_hegel_shim_error(void) {
    return shim_error;
}

JOLT_HEGEL_EXPORT int jolt_hegel_shim_init(const char *libhegel_path) {
    if (generate_date != NULL && generate_time != NULL &&
        generate_datetime != NULL) {
        return 0;
    }
    if (libhegel_path == NULL || libhegel_path[0] == '\0') {
        snprintf(shim_error, sizeof(shim_error),
                 "libhegel path is NULL or empty");
        return -1;
    }

#if defined(_WIN32)
    hegel_module = LoadLibraryA(libhegel_path);
    if (hegel_module == NULL) {
        snprintf(shim_error, sizeof(shim_error),
                 "LoadLibraryA failed for libhegel (Windows error %lu)",
                 (unsigned long)GetLastError());
        return -1;
    }
    generate_date =
        (generate_date_fn)(uintptr_t)GetProcAddress(hegel_module,
                                                   "hegel_generate_date");
    generate_time =
        (generate_time_fn)(uintptr_t)GetProcAddress(hegel_module,
                                                   "hegel_generate_time");
    generate_datetime =
        (generate_datetime_fn)(uintptr_t)GetProcAddress(
            hegel_module, "hegel_generate_datetime");
#else
    hegel_module = dlopen(libhegel_path, RTLD_NOW | RTLD_LOCAL);
    if (hegel_module == NULL) {
        snprintf(shim_error, sizeof(shim_error), "dlopen failed: %s",
                 dlerror());
        return -1;
    }
    generate_date = (generate_date_fn)dlsym(hegel_module,
                                             "hegel_generate_date");
    generate_time = (generate_time_fn)dlsym(hegel_module,
                                             "hegel_generate_time");
    generate_datetime = (generate_datetime_fn)dlsym(
        hegel_module, "hegel_generate_datetime");
#endif

    if (generate_date == NULL || generate_time == NULL ||
        generate_datetime == NULL) {
        snprintf(shim_error, sizeof(shim_error),
                 "libhegel does not export all date/time functions");
#if defined(_WIN32)
        FreeLibrary(hegel_module);
#else
        dlclose(hegel_module);
#endif
        hegel_module = NULL;
        generate_date = NULL;
        generate_time = NULL;
        generate_datetime = NULL;
        return -2;
    }

    shim_error[0] = '\0';
    return 0;
}

JOLT_HEGEL_EXPORT hegel_result_t
jolt_hegel_generate_date(hegel_context_t *ctx, hegel_test_case_t *tc,
                         const hegel_date_t *min_value,
                         const hegel_date_t *max_value,
                         hegel_date_t *out_value) {
    if (generate_date == NULL || min_value == NULL || max_value == NULL) {
        return HEGEL_E_INVALID_ARG;
    }
    return generate_date(ctx, tc, *min_value, *max_value, out_value);
}

JOLT_HEGEL_EXPORT hegel_result_t
jolt_hegel_generate_time(hegel_context_t *ctx, hegel_test_case_t *tc,
                         const hegel_time_t *min_value,
                         const hegel_time_t *max_value,
                         hegel_time_t *out_value) {
    if (generate_time == NULL || min_value == NULL || max_value == NULL) {
        return HEGEL_E_INVALID_ARG;
    }
    return generate_time(ctx, tc, *min_value, *max_value, out_value);
}

JOLT_HEGEL_EXPORT hegel_result_t
jolt_hegel_generate_datetime(hegel_context_t *ctx, hegel_test_case_t *tc,
                             const hegel_datetime_t *min_value,
                             const hegel_datetime_t *max_value,
                             hegel_datetime_t *out_value) {
    if (generate_datetime == NULL || min_value == NULL || max_value == NULL) {
        return HEGEL_E_INVALID_ARG;
    }
    return generate_datetime(ctx, tc, *min_value, *max_value, out_value);
}
