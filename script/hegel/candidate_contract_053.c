/* Candidate-engine contract, compiled against the released 0.53.2 header. */
#include "hegel.h"
#include <stdio.h>
#include <string.h>

#define CHECK(expr) do { if (!(expr)) { fprintf(stderr, "%s failed\n", #expr); return 1; } } while (0)

static int temporal(hegel_context_t *ctx) {
    hegel_date_options_t *options = NULL;
    hegel_date_t monday = {2024, 1, 1}, tuesday = {2024, 1, 2};
    hegel_time_t between_ticks = {.nanosecond = 999}, on_tick = {.nanosecond = 1000};
    CHECK(hegel_date_options_new(ctx, &options) == HEGEL_OK);
    CHECK(hegel_date_options_set_weekdays(ctx, options, 1) == HEGEL_OK);
    CHECK(hegel_validate_date(ctx, monday, monday, options) == HEGEL_OK);
    CHECK(hegel_validate_date(ctx, tuesday, tuesday, options) == HEGEL_E_INVALID_ARG);
    CHECK(hegel_validate_time(ctx, between_ticks, between_ticks, HEGEL_PRECISION_MICROSECONDS) == HEGEL_E_INVALID_ARG);
    CHECK(hegel_validate_time(ctx, between_ticks, on_tick, HEGEL_PRECISION_MICROSECONDS) == HEGEL_OK);
    CHECK(hegel_date_options_free(ctx, options) == HEGEL_OK);

    return 0;
}

static int failure_report(hegel_context_t *ctx, int quiet) {
    hegel_settings_t *settings = NULL;
    hegel_run_t *run = NULL;
    hegel_run_result_t *result = NULL;
    hegel_failure_t *failure = NULL;
    const hegel_string_t empty = {"", 0}, origin = {"lookup", 6};
    const hegel_string_t summary = {"mismatch", 8}, detail = {"expected false", 14};
    const hegel_string_t *output = NULL, *diagnostic = NULL;
    size_t count = 0;

    CHECK(hegel_settings_new(ctx, &settings) == HEGEL_OK);
    CHECK(hegel_settings_set_database(ctx, settings, &empty) == HEGEL_OK);
    CHECK(hegel_settings_set_verbosity(ctx, settings,
          quiet ? HEGEL_VERBOSITY_QUIET : HEGEL_VERBOSITY_NORMAL) == HEGEL_OK);
    CHECK(hegel_run_start(ctx, settings, NULL, NULL, &run) == HEGEL_OK);
    for (;;) {
        hegel_test_case_t *tc = NULL;
        bool wants_output, wants_detail;
        CHECK(hegel_next_test_case(ctx, run, &tc) == HEGEL_OK);
        if (!tc) break;
        CHECK(hegel_test_case_wants_output(ctx, tc, &wants_output) == HEGEL_OK);
        CHECK(hegel_test_case_wants_detail(ctx, tc, &wants_detail) == HEGEL_OK);
        if (wants_output || wants_detail)
            CHECK(hegel_mark_complete(ctx, tc, HEGEL_STATUS_INTERESTING,
                  &origin, NULL, NULL) == HEGEL_E_INVALID_ARG);
        CHECK(hegel_mark_complete(ctx, tc, HEGEL_STATUS_INTERESTING, &origin,
              wants_output ? &summary : NULL, wants_detail ? &detail : NULL) == HEGEL_OK);
        CHECK(hegel_test_case_free(ctx, tc) == HEGEL_OK);
    }
    CHECK(hegel_run_result(ctx, run, &result) == HEGEL_OK);
    CHECK(hegel_run_result_failure_count(ctx, result, &count) == HEGEL_OK && count == 1);
    CHECK(hegel_run_result_failure(ctx, result, 0, &failure) == HEGEL_OK);
    CHECK(hegel_failure_output(ctx, failure, &output) == HEGEL_OK);
    CHECK(hegel_failure_diagnostic(ctx, failure, &diagnostic) == HEGEL_OK);
    if (quiet) {
        CHECK(output == NULL && diagnostic == NULL);
    } else {
        CHECK(output && output->len == 0);
        CHECK(diagnostic && diagnostic->len == detail.len &&
              memcmp(diagnostic->data, detail.data, detail.len) == 0);
    }
    CHECK(hegel_failure_free(ctx, failure) == HEGEL_OK);
    CHECK(hegel_run_result_free(ctx, result) == HEGEL_OK);
    CHECK(hegel_run_free(ctx, run) == HEGEL_OK);
    CHECK(hegel_settings_free(ctx, settings) == HEGEL_OK);
    return 0;
}

int main(void) {
    hegel_context_t *ctx = hegel_context_new();
    CHECK(ctx);
    CHECK(temporal(ctx) == 0);
    CHECK(failure_report(ctx, 0) == 0);
    CHECK(failure_report(ctx, 1) == 0);
    CHECK(hegel_context_free(ctx) == HEGEL_OK);
    return 0;
}
