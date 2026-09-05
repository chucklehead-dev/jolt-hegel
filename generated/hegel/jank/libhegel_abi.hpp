// Generated from resources/hegel/abi.edn. DO NOT EDIT.
#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <jank/runtime/convert.hpp>
#include <jank/runtime/rtti.hpp>
#include <jank/runtime/obj/big_integer.hpp>
#include <jank/runtime/obj/persistent_string.hpp>
#if defined(_WIN32)
#include <windows.h>
#else
#include <dlfcn.h>
#endif

extern "C"
{
struct hegel_jank_hegel_collection;
struct hegel_jank_hegel_context;
struct hegel_jank_hegel_failure;
struct hegel_jank_hegel_pool;
struct hegel_jank_hegel_printer;
struct hegel_jank_hegel_printer_options;
struct hegel_jank_hegel_recursion;
struct hegel_jank_hegel_run;
struct hegel_jank_hegel_run_result;
struct hegel_jank_hegel_settings;
struct hegel_jank_hegel_state_machine;
struct hegel_jank_hegel_string_generator;
struct hegel_jank_hegel_test_case;

struct hegel_jank_hegel_bytes_result
{
  std::uint8_t * data;
  std::size_t len;
};

struct hegel_jank_hegel_date
{
  std::int32_t year;
  std::uint8_t month;
  std::uint8_t day;
};

struct hegel_jank_hegel_printer_value_result
{
  std::int8_t * data;
  std::size_t len;
};

struct hegel_jank_hegel_string_result
{
  std::int8_t * data;
  std::size_t len;
};

struct hegel_jank_hegel_time
{
  std::uint8_t hour;
  std::uint8_t minute;
  std::uint8_t second;
  std::uint32_t nanosecond;
};

struct hegel_jank_hegel_datetime
{
  hegel_jank_hegel_date date;
  hegel_jank_hegel_time time;
};

using hegel_jank_hegel_output_callback = void (*) (void *, char const *, std::size_t);

using hegel_jank_fn_collection_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_collection *); // hegel_collection_free
using hegel_jank_fn_collection_more = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_collection *, std::uint8_t *); // hegel_collection_more
using hegel_jank_fn_collection_reject = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_collection *, char const *); // hegel_collection_reject
using hegel_jank_fn_context_free = std::int32_t (*) (hegel_jank_hegel_context *); // hegel_context_free
using hegel_jank_fn_context_last_error = char const * (*) (hegel_jank_hegel_context *); // hegel_context_last_error
using hegel_jank_fn_context_new = hegel_jank_hegel_context * (*) (void); // hegel_context_new
using hegel_jank_fn_event = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, char const *); // hegel_event
using hegel_jank_fn_event_value = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, double, char const *); // hegel_event_value
using hegel_jank_fn_failure_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_failure *); // hegel_failure_free
using hegel_jank_fn_failure_origin = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_failure *, char const * *); // hegel_failure_origin
using hegel_jank_fn_failure_reproduction_blob = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_failure *, char const * *); // hegel_failure_reproduction_blob
using hegel_jank_fn_generate_boolean = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, double, std::uint8_t, std::uint8_t, std::uint8_t *); // hegel_generate_boolean
using hegel_jank_fn_generate_bytes = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint64_t, std::uint64_t, hegel_jank_hegel_bytes_result *); // hegel_generate_bytes
using hegel_jank_fn_generate_bytes_result_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_bytes_result *); // hegel_generate_bytes_result_free
using hegel_jank_fn_generate_date = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_date, hegel_jank_hegel_date, hegel_jank_hegel_date *); // hegel_generate_date
using hegel_jank_fn_generate_datetime = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_datetime, hegel_jank_hegel_datetime, hegel_jank_hegel_datetime *); // hegel_generate_datetime
using hegel_jank_fn_generate_float = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint32_t, double, double, std::uint8_t, std::uint8_t, std::uint8_t, std::uint8_t, double, double *); // hegel_generate_float
using hegel_jank_fn_generate_integer = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::int64_t, std::int64_t, std::int64_t *); // hegel_generate_integer
using hegel_jank_fn_generate_integer_big = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t *, std::size_t, std::uint8_t *, std::size_t, std::uint8_t *, std::size_t, std::size_t *); // hegel_generate_integer_big
using hegel_jank_fn_generate_ipv4 = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t *); // hegel_generate_ipv4
using hegel_jank_fn_generate_ipv6 = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t *); // hegel_generate_ipv6
using hegel_jank_fn_generate_string = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_string_generator *, hegel_jank_hegel_string_result *); // hegel_generate_string
using hegel_jank_fn_generate_string_result_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_string_result *); // hegel_generate_string_result_free
using hegel_jank_fn_generate_time = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_time, hegel_jank_hegel_time, hegel_jank_hegel_time *); // hegel_generate_time
using hegel_jank_fn_generate_uuid = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t, std::uint8_t, std::uint8_t *); // hegel_generate_uuid
using hegel_jank_fn_mark_complete = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint32_t, char const *); // hegel_mark_complete
using hegel_jank_fn_new_collection = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint64_t, std::uint64_t, hegel_jank_hegel_collection * *); // hegel_new_collection
using hegel_jank_fn_new_pool = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_pool * *); // hegel_new_pool
using hegel_jank_fn_new_recursion = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint64_t, std::uint64_t, hegel_jank_hegel_recursion * *); // hegel_new_recursion
using hegel_jank_fn_new_state_machine = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, char const * *, std::int64_t *, std::size_t, char const * *, std::size_t, std::int64_t, std::int64_t, hegel_jank_hegel_state_machine * *, std::int64_t *); // hegel_new_state_machine
using hegel_jank_fn_next_test_case = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run *, hegel_jank_hegel_test_case * *); // hegel_next_test_case
using hegel_jank_fn_note = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t *, std::size_t); // hegel_note
using hegel_jank_fn_pool_add = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_pool *, std::int64_t *); // hegel_pool_add
using hegel_jank_fn_pool_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_pool *); // hegel_pool_free
using hegel_jank_fn_pool_generate = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_pool *, std::uint8_t, std::int64_t *); // hegel_pool_generate
using hegel_jank_fn_printer_abort_speculative = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *); // hegel_printer_abort_speculative
using hegel_jank_fn_printer_begin_group = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint64_t, std::uint8_t *, std::size_t); // hegel_printer_begin_group
using hegel_jank_fn_printer_begin_speculative = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *); // hegel_printer_begin_speculative
using hegel_jank_fn_printer_breakable = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint8_t *, std::size_t); // hegel_printer_breakable
using hegel_jank_fn_printer_comment = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint8_t *, std::size_t); // hegel_printer_comment
using hegel_jank_fn_printer_commit_speculative = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *); // hegel_printer_commit_speculative
using hegel_jank_fn_printer_deferred = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, hegel_jank_hegel_printer * *); // hegel_printer_deferred
using hegel_jank_fn_printer_end_group = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint8_t *, std::size_t); // hegel_printer_end_group
using hegel_jank_fn_printer_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *); // hegel_printer_free
using hegel_jank_fn_printer_hard_break = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *); // hegel_printer_hard_break
using hegel_jank_fn_printer_if_break = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint8_t *, std::size_t); // hegel_printer_if_break
using hegel_jank_fn_printer_is_live = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint8_t *); // hegel_printer_is_live
using hegel_jank_fn_printer_new = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer_options *, hegel_jank_hegel_printer * *); // hegel_printer_new
using hegel_jank_fn_printer_options_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer_options *); // hegel_printer_options_free
using hegel_jank_fn_printer_options_new = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer_options * *); // hegel_printer_options_new
using hegel_jank_fn_printer_options_set_max_width = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer_options *, std::uint64_t); // hegel_printer_options_set_max_width
using hegel_jank_fn_printer_resolve = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *); // hegel_printer_resolve
using hegel_jank_fn_printer_shift_indent = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::int64_t); // hegel_printer_shift_indent
using hegel_jank_fn_printer_text = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, std::uint8_t *, std::size_t); // hegel_printer_text
using hegel_jank_fn_printer_value = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer *, hegel_jank_hegel_printer_value_result *); // hegel_printer_value
using hegel_jank_fn_printer_value_result_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_printer_value_result *); // hegel_printer_value_result_free
using hegel_jank_fn_recursion_branch = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_recursion *, std::uint64_t, std::uint8_t *); // hegel_recursion_branch
using hegel_jank_fn_recursion_finish = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_recursion *); // hegel_recursion_finish
using hegel_jank_fn_recursion_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_recursion *); // hegel_recursion_free
using hegel_jank_fn_recursion_leaf = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_recursion *); // hegel_recursion_leaf
using hegel_jank_fn_recursion_retry = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_recursion *); // hegel_recursion_retry
using hegel_jank_fn_run_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run *); // hegel_run_free
using hegel_jank_fn_run_result = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run *, hegel_jank_hegel_run_result * *); // hegel_run_result
using hegel_jank_fn_run_result_error = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run_result *, char const * *); // hegel_run_result_error
using hegel_jank_fn_run_result_failure = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run_result *, std::size_t, hegel_jank_hegel_failure * *); // hegel_run_result_failure
using hegel_jank_fn_run_result_failure_count = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run_result *, std::size_t *); // hegel_run_result_failure_count
using hegel_jank_fn_run_result_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run_result *); // hegel_run_result_free
using hegel_jank_fn_run_result_status = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_run_result *, std::int32_t *); // hegel_run_result_status
using hegel_jank_fn_run_start = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, hegel_jank_hegel_output_callback, void *, hegel_jank_hegel_run * *); // hegel_run_start
using hegel_jank_fn_settings_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *); // hegel_settings_free
using hegel_jank_fn_settings_new = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings * *); // hegel_settings_new
using hegel_jank_fn_settings_set_backend = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint32_t); // hegel_settings_set_backend
using hegel_jank_fn_settings_set_database = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, char const *); // hegel_settings_set_database
using hegel_jank_fn_settings_set_database_key = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, char const *); // hegel_settings_set_database_key
using hegel_jank_fn_settings_set_derandomize = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint8_t); // hegel_settings_set_derandomize
using hegel_jank_fn_settings_set_phases = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint32_t); // hegel_settings_set_phases
using hegel_jank_fn_settings_set_report_multiple_failures = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint8_t); // hegel_settings_set_report_multiple_failures
using hegel_jank_fn_settings_set_seed = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint64_t, std::uint8_t); // hegel_settings_set_seed
using hegel_jank_fn_settings_set_show_statistics = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint8_t); // hegel_settings_set_show_statistics
using hegel_jank_fn_settings_set_stateful_step_count = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::int64_t); // hegel_settings_set_stateful_step_count
using hegel_jank_fn_settings_set_suppress_health_check = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint32_t); // hegel_settings_set_suppress_health_check
using hegel_jank_fn_settings_set_test_cases = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint64_t); // hegel_settings_set_test_cases
using hegel_jank_fn_settings_set_verbosity = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, std::uint32_t); // hegel_settings_set_verbosity
using hegel_jank_fn_start_span = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint64_t); // hegel_start_span
using hegel_jank_fn_state_machine_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_state_machine *); // hegel_state_machine_free
using hegel_jank_fn_state_machine_next_group = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_state_machine *, std::int64_t *); // hegel_state_machine_next_group
using hegel_jank_fn_state_machine_next_rule = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_state_machine *, std::int64_t, std::int64_t *); // hegel_state_machine_next_rule
using hegel_jank_fn_state_machine_rule_rejected = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_state_machine *, std::int64_t); // hegel_state_machine_rule_rejected
using hegel_jank_fn_state_machine_should_check_invariant = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_state_machine *, std::int64_t, std::uint8_t *); // hegel_state_machine_should_check_invariant
using hegel_jank_fn_stop_span = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t); // hegel_stop_span
using hegel_jank_fn_string_generator_domain = std::int32_t (*) (hegel_jank_hegel_context *, std::uint64_t, hegel_jank_hegel_string_generator * *); // hegel_string_generator_domain
using hegel_jank_fn_string_generator_email = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_string_generator * *); // hegel_string_generator_email
using hegel_jank_fn_string_generator_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_string_generator *); // hegel_string_generator_free
using hegel_jank_fn_string_generator_regex = std::int32_t (*) (hegel_jank_hegel_context *, char const *, std::uint8_t, hegel_jank_hegel_string_generator *, hegel_jank_hegel_string_generator * *); // hegel_string_generator_regex
using hegel_jank_fn_string_generator_text = std::int32_t (*) (hegel_jank_hegel_context *, std::uint64_t, std::uint64_t, char const *, std::uint32_t, std::uint32_t, char const * *, std::size_t, char const * *, std::size_t, std::uint8_t *, std::size_t, std::uint8_t *, std::size_t, hegel_jank_hegel_string_generator * *); // hegel_string_generator_text
using hegel_jank_fn_string_generator_url = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_string_generator * *); // hegel_string_generator_url
using hegel_jank_fn_target = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, double, char const *); // hegel_target
using hegel_jank_fn_test_case_clone = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_test_case * *); // hegel_test_case_clone
using hegel_jank_fn_test_case_free = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *); // hegel_test_case_free
using hegel_jank_fn_test_case_from_blob = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_settings *, char const *, hegel_jank_hegel_output_callback, void *, hegel_jank_hegel_test_case * *); // hegel_test_case_from_blob
using hegel_jank_fn_test_case_is_nondeterministic = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, std::uint8_t *); // hegel_test_case_is_nondeterministic
using hegel_jank_fn_test_case_printer = std::int32_t (*) (hegel_jank_hegel_context *, hegel_jank_hegel_test_case *, hegel_jank_hegel_printer_options *, hegel_jank_hegel_printer * *); // hegel_test_case_printer
using hegel_jank_fn_version = std::int32_t (*) (hegel_jank_hegel_context *, char const * *); // hegel_version
}

struct hegel_jank_bindings
{
  void *library{};
  hegel_jank_fn_collection_free collection_free{};
  hegel_jank_fn_collection_more collection_more{};
  hegel_jank_fn_collection_reject collection_reject{};
  hegel_jank_fn_context_free context_free{};
  hegel_jank_fn_context_last_error context_last_error{};
  hegel_jank_fn_context_new context_new{};
  hegel_jank_fn_event event{};
  hegel_jank_fn_event_value event_value{};
  hegel_jank_fn_failure_free failure_free{};
  hegel_jank_fn_failure_origin failure_origin{};
  hegel_jank_fn_failure_reproduction_blob failure_reproduction_blob{};
  hegel_jank_fn_generate_boolean generate_boolean{};
  hegel_jank_fn_generate_bytes generate_bytes{};
  hegel_jank_fn_generate_bytes_result_free generate_bytes_result_free{};
  hegel_jank_fn_generate_date generate_date{};
  hegel_jank_fn_generate_datetime generate_datetime{};
  hegel_jank_fn_generate_float generate_float{};
  hegel_jank_fn_generate_integer generate_integer{};
  hegel_jank_fn_generate_integer_big generate_integer_big{};
  hegel_jank_fn_generate_ipv4 generate_ipv4{};
  hegel_jank_fn_generate_ipv6 generate_ipv6{};
  hegel_jank_fn_generate_string generate_string{};
  hegel_jank_fn_generate_string_result_free generate_string_result_free{};
  hegel_jank_fn_generate_time generate_time{};
  hegel_jank_fn_generate_uuid generate_uuid{};
  hegel_jank_fn_mark_complete mark_complete{};
  hegel_jank_fn_new_collection new_collection{};
  hegel_jank_fn_new_pool new_pool{};
  hegel_jank_fn_new_recursion new_recursion{};
  hegel_jank_fn_new_state_machine new_state_machine{};
  hegel_jank_fn_next_test_case next_test_case{};
  hegel_jank_fn_note note{};
  hegel_jank_fn_pool_add pool_add{};
  hegel_jank_fn_pool_free pool_free{};
  hegel_jank_fn_pool_generate pool_generate{};
  hegel_jank_fn_printer_abort_speculative printer_abort_speculative{};
  hegel_jank_fn_printer_begin_group printer_begin_group{};
  hegel_jank_fn_printer_begin_speculative printer_begin_speculative{};
  hegel_jank_fn_printer_breakable printer_breakable{};
  hegel_jank_fn_printer_comment printer_comment{};
  hegel_jank_fn_printer_commit_speculative printer_commit_speculative{};
  hegel_jank_fn_printer_deferred printer_deferred{};
  hegel_jank_fn_printer_end_group printer_end_group{};
  hegel_jank_fn_printer_free printer_free{};
  hegel_jank_fn_printer_hard_break printer_hard_break{};
  hegel_jank_fn_printer_if_break printer_if_break{};
  hegel_jank_fn_printer_is_live printer_is_live{};
  hegel_jank_fn_printer_new printer_new{};
  hegel_jank_fn_printer_options_free printer_options_free{};
  hegel_jank_fn_printer_options_new printer_options_new{};
  hegel_jank_fn_printer_options_set_max_width printer_options_set_max_width{};
  hegel_jank_fn_printer_resolve printer_resolve{};
  hegel_jank_fn_printer_shift_indent printer_shift_indent{};
  hegel_jank_fn_printer_text printer_text{};
  hegel_jank_fn_printer_value printer_value{};
  hegel_jank_fn_printer_value_result_free printer_value_result_free{};
  hegel_jank_fn_recursion_branch recursion_branch{};
  hegel_jank_fn_recursion_finish recursion_finish{};
  hegel_jank_fn_recursion_free recursion_free{};
  hegel_jank_fn_recursion_leaf recursion_leaf{};
  hegel_jank_fn_recursion_retry recursion_retry{};
  hegel_jank_fn_run_free run_free{};
  hegel_jank_fn_run_result run_result{};
  hegel_jank_fn_run_result_error run_result_error{};
  hegel_jank_fn_run_result_failure run_result_failure{};
  hegel_jank_fn_run_result_failure_count run_result_failure_count{};
  hegel_jank_fn_run_result_free run_result_free{};
  hegel_jank_fn_run_result_status run_result_status{};
  hegel_jank_fn_run_start run_start{};
  hegel_jank_fn_settings_free settings_free{};
  hegel_jank_fn_settings_new settings_new{};
  hegel_jank_fn_settings_set_backend settings_set_backend{};
  hegel_jank_fn_settings_set_database settings_set_database{};
  hegel_jank_fn_settings_set_database_key settings_set_database_key{};
  hegel_jank_fn_settings_set_derandomize settings_set_derandomize{};
  hegel_jank_fn_settings_set_phases settings_set_phases{};
  hegel_jank_fn_settings_set_report_multiple_failures settings_set_report_multiple_failures{};
  hegel_jank_fn_settings_set_seed settings_set_seed{};
  hegel_jank_fn_settings_set_show_statistics settings_set_show_statistics{};
  hegel_jank_fn_settings_set_stateful_step_count settings_set_stateful_step_count{};
  hegel_jank_fn_settings_set_suppress_health_check settings_set_suppress_health_check{};
  hegel_jank_fn_settings_set_test_cases settings_set_test_cases{};
  hegel_jank_fn_settings_set_verbosity settings_set_verbosity{};
  hegel_jank_fn_start_span start_span{};
  hegel_jank_fn_state_machine_free state_machine_free{};
  hegel_jank_fn_state_machine_next_group state_machine_next_group{};
  hegel_jank_fn_state_machine_next_rule state_machine_next_rule{};
  hegel_jank_fn_state_machine_rule_rejected state_machine_rule_rejected{};
  hegel_jank_fn_state_machine_should_check_invariant state_machine_should_check_invariant{};
  hegel_jank_fn_stop_span stop_span{};
  hegel_jank_fn_string_generator_domain string_generator_domain{};
  hegel_jank_fn_string_generator_email string_generator_email{};
  hegel_jank_fn_string_generator_free string_generator_free{};
  hegel_jank_fn_string_generator_regex string_generator_regex{};
  hegel_jank_fn_string_generator_text string_generator_text{};
  hegel_jank_fn_string_generator_url string_generator_url{};
  hegel_jank_fn_target target{};
  hegel_jank_fn_test_case_clone test_case_clone{};
  hegel_jank_fn_test_case_free test_case_free{};
  hegel_jank_fn_test_case_from_blob test_case_from_blob{};
  hegel_jank_fn_test_case_is_nondeterministic test_case_is_nondeterministic{};
  hegel_jank_fn_test_case_printer test_case_printer{};
  hegel_jank_fn_version version{};
};

inline hegel_jank_bindings *hegel_jank_active_bindings{};

inline void *hegel_jank_open_library(std::string const &path)
{
#if defined(_WIN32)
  auto handle = LoadLibraryA(path.c_str());
  if(handle == nullptr) { throw std::runtime_error{"LoadLibraryA failed for " + path}; }
  return reinterpret_cast<void *>(handle);
#else
  auto handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
  if(handle == nullptr) { throw std::runtime_error{dlerror()}; }
  return handle;
#endif
}

inline void *hegel_jank_find_symbol(void *library, char const *symbol)
{
#if defined(_WIN32)
  auto address = GetProcAddress(reinterpret_cast<HMODULE>(library), symbol);
#else
  dlerror();
  auto address = dlsym(library, symbol);
#endif
  if(address == nullptr) { throw std::runtime_error{std::string{"libhegel symbol not found: "} + symbol}; }
  return reinterpret_cast<void *>(address);
}

inline hegel_jank_bindings *hegel_jank_load_bindings(std::string const &path)
{
  if(hegel_jank_active_bindings != nullptr) { return hegel_jank_active_bindings; }
  auto *bindings = new hegel_jank_bindings{};
  bindings->library = hegel_jank_open_library(path);
  bindings->collection_free = reinterpret_cast<hegel_jank_fn_collection_free>(hegel_jank_find_symbol(bindings->library, "hegel_collection_free"));
  bindings->collection_more = reinterpret_cast<hegel_jank_fn_collection_more>(hegel_jank_find_symbol(bindings->library, "hegel_collection_more"));
  bindings->collection_reject = reinterpret_cast<hegel_jank_fn_collection_reject>(hegel_jank_find_symbol(bindings->library, "hegel_collection_reject"));
  bindings->context_free = reinterpret_cast<hegel_jank_fn_context_free>(hegel_jank_find_symbol(bindings->library, "hegel_context_free"));
  bindings->context_last_error = reinterpret_cast<hegel_jank_fn_context_last_error>(hegel_jank_find_symbol(bindings->library, "hegel_context_last_error"));
  bindings->context_new = reinterpret_cast<hegel_jank_fn_context_new>(hegel_jank_find_symbol(bindings->library, "hegel_context_new"));
  bindings->event = reinterpret_cast<hegel_jank_fn_event>(hegel_jank_find_symbol(bindings->library, "hegel_event"));
  bindings->event_value = reinterpret_cast<hegel_jank_fn_event_value>(hegel_jank_find_symbol(bindings->library, "hegel_event_value"));
  bindings->failure_free = reinterpret_cast<hegel_jank_fn_failure_free>(hegel_jank_find_symbol(bindings->library, "hegel_failure_free"));
  bindings->failure_origin = reinterpret_cast<hegel_jank_fn_failure_origin>(hegel_jank_find_symbol(bindings->library, "hegel_failure_origin"));
  bindings->failure_reproduction_blob = reinterpret_cast<hegel_jank_fn_failure_reproduction_blob>(hegel_jank_find_symbol(bindings->library, "hegel_failure_reproduction_blob"));
  bindings->generate_boolean = reinterpret_cast<hegel_jank_fn_generate_boolean>(hegel_jank_find_symbol(bindings->library, "hegel_generate_boolean"));
  bindings->generate_bytes = reinterpret_cast<hegel_jank_fn_generate_bytes>(hegel_jank_find_symbol(bindings->library, "hegel_generate_bytes"));
  bindings->generate_bytes_result_free = reinterpret_cast<hegel_jank_fn_generate_bytes_result_free>(hegel_jank_find_symbol(bindings->library, "hegel_generate_bytes_result_free"));
  bindings->generate_date = reinterpret_cast<hegel_jank_fn_generate_date>(hegel_jank_find_symbol(bindings->library, "hegel_generate_date"));
  bindings->generate_datetime = reinterpret_cast<hegel_jank_fn_generate_datetime>(hegel_jank_find_symbol(bindings->library, "hegel_generate_datetime"));
  bindings->generate_float = reinterpret_cast<hegel_jank_fn_generate_float>(hegel_jank_find_symbol(bindings->library, "hegel_generate_float"));
  bindings->generate_integer = reinterpret_cast<hegel_jank_fn_generate_integer>(hegel_jank_find_symbol(bindings->library, "hegel_generate_integer"));
  bindings->generate_integer_big = reinterpret_cast<hegel_jank_fn_generate_integer_big>(hegel_jank_find_symbol(bindings->library, "hegel_generate_integer_big"));
  bindings->generate_ipv4 = reinterpret_cast<hegel_jank_fn_generate_ipv4>(hegel_jank_find_symbol(bindings->library, "hegel_generate_ipv4"));
  bindings->generate_ipv6 = reinterpret_cast<hegel_jank_fn_generate_ipv6>(hegel_jank_find_symbol(bindings->library, "hegel_generate_ipv6"));
  bindings->generate_string = reinterpret_cast<hegel_jank_fn_generate_string>(hegel_jank_find_symbol(bindings->library, "hegel_generate_string"));
  bindings->generate_string_result_free = reinterpret_cast<hegel_jank_fn_generate_string_result_free>(hegel_jank_find_symbol(bindings->library, "hegel_generate_string_result_free"));
  bindings->generate_time = reinterpret_cast<hegel_jank_fn_generate_time>(hegel_jank_find_symbol(bindings->library, "hegel_generate_time"));
  bindings->generate_uuid = reinterpret_cast<hegel_jank_fn_generate_uuid>(hegel_jank_find_symbol(bindings->library, "hegel_generate_uuid"));
  bindings->mark_complete = reinterpret_cast<hegel_jank_fn_mark_complete>(hegel_jank_find_symbol(bindings->library, "hegel_mark_complete"));
  bindings->new_collection = reinterpret_cast<hegel_jank_fn_new_collection>(hegel_jank_find_symbol(bindings->library, "hegel_new_collection"));
  bindings->new_pool = reinterpret_cast<hegel_jank_fn_new_pool>(hegel_jank_find_symbol(bindings->library, "hegel_new_pool"));
  bindings->new_recursion = reinterpret_cast<hegel_jank_fn_new_recursion>(hegel_jank_find_symbol(bindings->library, "hegel_new_recursion"));
  bindings->new_state_machine = reinterpret_cast<hegel_jank_fn_new_state_machine>(hegel_jank_find_symbol(bindings->library, "hegel_new_state_machine"));
  bindings->next_test_case = reinterpret_cast<hegel_jank_fn_next_test_case>(hegel_jank_find_symbol(bindings->library, "hegel_next_test_case"));
  bindings->note = reinterpret_cast<hegel_jank_fn_note>(hegel_jank_find_symbol(bindings->library, "hegel_note"));
  bindings->pool_add = reinterpret_cast<hegel_jank_fn_pool_add>(hegel_jank_find_symbol(bindings->library, "hegel_pool_add"));
  bindings->pool_free = reinterpret_cast<hegel_jank_fn_pool_free>(hegel_jank_find_symbol(bindings->library, "hegel_pool_free"));
  bindings->pool_generate = reinterpret_cast<hegel_jank_fn_pool_generate>(hegel_jank_find_symbol(bindings->library, "hegel_pool_generate"));
  bindings->printer_abort_speculative = reinterpret_cast<hegel_jank_fn_printer_abort_speculative>(hegel_jank_find_symbol(bindings->library, "hegel_printer_abort_speculative"));
  bindings->printer_begin_group = reinterpret_cast<hegel_jank_fn_printer_begin_group>(hegel_jank_find_symbol(bindings->library, "hegel_printer_begin_group"));
  bindings->printer_begin_speculative = reinterpret_cast<hegel_jank_fn_printer_begin_speculative>(hegel_jank_find_symbol(bindings->library, "hegel_printer_begin_speculative"));
  bindings->printer_breakable = reinterpret_cast<hegel_jank_fn_printer_breakable>(hegel_jank_find_symbol(bindings->library, "hegel_printer_breakable"));
  bindings->printer_comment = reinterpret_cast<hegel_jank_fn_printer_comment>(hegel_jank_find_symbol(bindings->library, "hegel_printer_comment"));
  bindings->printer_commit_speculative = reinterpret_cast<hegel_jank_fn_printer_commit_speculative>(hegel_jank_find_symbol(bindings->library, "hegel_printer_commit_speculative"));
  bindings->printer_deferred = reinterpret_cast<hegel_jank_fn_printer_deferred>(hegel_jank_find_symbol(bindings->library, "hegel_printer_deferred"));
  bindings->printer_end_group = reinterpret_cast<hegel_jank_fn_printer_end_group>(hegel_jank_find_symbol(bindings->library, "hegel_printer_end_group"));
  bindings->printer_free = reinterpret_cast<hegel_jank_fn_printer_free>(hegel_jank_find_symbol(bindings->library, "hegel_printer_free"));
  bindings->printer_hard_break = reinterpret_cast<hegel_jank_fn_printer_hard_break>(hegel_jank_find_symbol(bindings->library, "hegel_printer_hard_break"));
  bindings->printer_if_break = reinterpret_cast<hegel_jank_fn_printer_if_break>(hegel_jank_find_symbol(bindings->library, "hegel_printer_if_break"));
  bindings->printer_is_live = reinterpret_cast<hegel_jank_fn_printer_is_live>(hegel_jank_find_symbol(bindings->library, "hegel_printer_is_live"));
  bindings->printer_new = reinterpret_cast<hegel_jank_fn_printer_new>(hegel_jank_find_symbol(bindings->library, "hegel_printer_new"));
  bindings->printer_options_free = reinterpret_cast<hegel_jank_fn_printer_options_free>(hegel_jank_find_symbol(bindings->library, "hegel_printer_options_free"));
  bindings->printer_options_new = reinterpret_cast<hegel_jank_fn_printer_options_new>(hegel_jank_find_symbol(bindings->library, "hegel_printer_options_new"));
  bindings->printer_options_set_max_width = reinterpret_cast<hegel_jank_fn_printer_options_set_max_width>(hegel_jank_find_symbol(bindings->library, "hegel_printer_options_set_max_width"));
  bindings->printer_resolve = reinterpret_cast<hegel_jank_fn_printer_resolve>(hegel_jank_find_symbol(bindings->library, "hegel_printer_resolve"));
  bindings->printer_shift_indent = reinterpret_cast<hegel_jank_fn_printer_shift_indent>(hegel_jank_find_symbol(bindings->library, "hegel_printer_shift_indent"));
  bindings->printer_text = reinterpret_cast<hegel_jank_fn_printer_text>(hegel_jank_find_symbol(bindings->library, "hegel_printer_text"));
  bindings->printer_value = reinterpret_cast<hegel_jank_fn_printer_value>(hegel_jank_find_symbol(bindings->library, "hegel_printer_value"));
  bindings->printer_value_result_free = reinterpret_cast<hegel_jank_fn_printer_value_result_free>(hegel_jank_find_symbol(bindings->library, "hegel_printer_value_result_free"));
  bindings->recursion_branch = reinterpret_cast<hegel_jank_fn_recursion_branch>(hegel_jank_find_symbol(bindings->library, "hegel_recursion_branch"));
  bindings->recursion_finish = reinterpret_cast<hegel_jank_fn_recursion_finish>(hegel_jank_find_symbol(bindings->library, "hegel_recursion_finish"));
  bindings->recursion_free = reinterpret_cast<hegel_jank_fn_recursion_free>(hegel_jank_find_symbol(bindings->library, "hegel_recursion_free"));
  bindings->recursion_leaf = reinterpret_cast<hegel_jank_fn_recursion_leaf>(hegel_jank_find_symbol(bindings->library, "hegel_recursion_leaf"));
  bindings->recursion_retry = reinterpret_cast<hegel_jank_fn_recursion_retry>(hegel_jank_find_symbol(bindings->library, "hegel_recursion_retry"));
  bindings->run_free = reinterpret_cast<hegel_jank_fn_run_free>(hegel_jank_find_symbol(bindings->library, "hegel_run_free"));
  bindings->run_result = reinterpret_cast<hegel_jank_fn_run_result>(hegel_jank_find_symbol(bindings->library, "hegel_run_result"));
  bindings->run_result_error = reinterpret_cast<hegel_jank_fn_run_result_error>(hegel_jank_find_symbol(bindings->library, "hegel_run_result_error"));
  bindings->run_result_failure = reinterpret_cast<hegel_jank_fn_run_result_failure>(hegel_jank_find_symbol(bindings->library, "hegel_run_result_failure"));
  bindings->run_result_failure_count = reinterpret_cast<hegel_jank_fn_run_result_failure_count>(hegel_jank_find_symbol(bindings->library, "hegel_run_result_failure_count"));
  bindings->run_result_free = reinterpret_cast<hegel_jank_fn_run_result_free>(hegel_jank_find_symbol(bindings->library, "hegel_run_result_free"));
  bindings->run_result_status = reinterpret_cast<hegel_jank_fn_run_result_status>(hegel_jank_find_symbol(bindings->library, "hegel_run_result_status"));
  bindings->run_start = reinterpret_cast<hegel_jank_fn_run_start>(hegel_jank_find_symbol(bindings->library, "hegel_run_start"));
  bindings->settings_free = reinterpret_cast<hegel_jank_fn_settings_free>(hegel_jank_find_symbol(bindings->library, "hegel_settings_free"));
  bindings->settings_new = reinterpret_cast<hegel_jank_fn_settings_new>(hegel_jank_find_symbol(bindings->library, "hegel_settings_new"));
  bindings->settings_set_backend = reinterpret_cast<hegel_jank_fn_settings_set_backend>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_backend"));
  bindings->settings_set_database = reinterpret_cast<hegel_jank_fn_settings_set_database>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_database"));
  bindings->settings_set_database_key = reinterpret_cast<hegel_jank_fn_settings_set_database_key>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_database_key"));
  bindings->settings_set_derandomize = reinterpret_cast<hegel_jank_fn_settings_set_derandomize>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_derandomize"));
  bindings->settings_set_phases = reinterpret_cast<hegel_jank_fn_settings_set_phases>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_phases"));
  bindings->settings_set_report_multiple_failures = reinterpret_cast<hegel_jank_fn_settings_set_report_multiple_failures>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_report_multiple_failures"));
  bindings->settings_set_seed = reinterpret_cast<hegel_jank_fn_settings_set_seed>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_seed"));
  bindings->settings_set_show_statistics = reinterpret_cast<hegel_jank_fn_settings_set_show_statistics>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_show_statistics"));
  bindings->settings_set_stateful_step_count = reinterpret_cast<hegel_jank_fn_settings_set_stateful_step_count>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_stateful_step_count"));
  bindings->settings_set_suppress_health_check = reinterpret_cast<hegel_jank_fn_settings_set_suppress_health_check>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_suppress_health_check"));
  bindings->settings_set_test_cases = reinterpret_cast<hegel_jank_fn_settings_set_test_cases>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_test_cases"));
  bindings->settings_set_verbosity = reinterpret_cast<hegel_jank_fn_settings_set_verbosity>(hegel_jank_find_symbol(bindings->library, "hegel_settings_set_verbosity"));
  bindings->start_span = reinterpret_cast<hegel_jank_fn_start_span>(hegel_jank_find_symbol(bindings->library, "hegel_start_span"));
  bindings->state_machine_free = reinterpret_cast<hegel_jank_fn_state_machine_free>(hegel_jank_find_symbol(bindings->library, "hegel_state_machine_free"));
  bindings->state_machine_next_group = reinterpret_cast<hegel_jank_fn_state_machine_next_group>(hegel_jank_find_symbol(bindings->library, "hegel_state_machine_next_group"));
  bindings->state_machine_next_rule = reinterpret_cast<hegel_jank_fn_state_machine_next_rule>(hegel_jank_find_symbol(bindings->library, "hegel_state_machine_next_rule"));
  bindings->state_machine_rule_rejected = reinterpret_cast<hegel_jank_fn_state_machine_rule_rejected>(hegel_jank_find_symbol(bindings->library, "hegel_state_machine_rule_rejected"));
  bindings->state_machine_should_check_invariant = reinterpret_cast<hegel_jank_fn_state_machine_should_check_invariant>(hegel_jank_find_symbol(bindings->library, "hegel_state_machine_should_check_invariant"));
  bindings->stop_span = reinterpret_cast<hegel_jank_fn_stop_span>(hegel_jank_find_symbol(bindings->library, "hegel_stop_span"));
  bindings->string_generator_domain = reinterpret_cast<hegel_jank_fn_string_generator_domain>(hegel_jank_find_symbol(bindings->library, "hegel_string_generator_domain"));
  bindings->string_generator_email = reinterpret_cast<hegel_jank_fn_string_generator_email>(hegel_jank_find_symbol(bindings->library, "hegel_string_generator_email"));
  bindings->string_generator_free = reinterpret_cast<hegel_jank_fn_string_generator_free>(hegel_jank_find_symbol(bindings->library, "hegel_string_generator_free"));
  bindings->string_generator_regex = reinterpret_cast<hegel_jank_fn_string_generator_regex>(hegel_jank_find_symbol(bindings->library, "hegel_string_generator_regex"));
  bindings->string_generator_text = reinterpret_cast<hegel_jank_fn_string_generator_text>(hegel_jank_find_symbol(bindings->library, "hegel_string_generator_text"));
  bindings->string_generator_url = reinterpret_cast<hegel_jank_fn_string_generator_url>(hegel_jank_find_symbol(bindings->library, "hegel_string_generator_url"));
  bindings->target = reinterpret_cast<hegel_jank_fn_target>(hegel_jank_find_symbol(bindings->library, "hegel_target"));
  bindings->test_case_clone = reinterpret_cast<hegel_jank_fn_test_case_clone>(hegel_jank_find_symbol(bindings->library, "hegel_test_case_clone"));
  bindings->test_case_free = reinterpret_cast<hegel_jank_fn_test_case_free>(hegel_jank_find_symbol(bindings->library, "hegel_test_case_free"));
  bindings->test_case_from_blob = reinterpret_cast<hegel_jank_fn_test_case_from_blob>(hegel_jank_find_symbol(bindings->library, "hegel_test_case_from_blob"));
  bindings->test_case_is_nondeterministic = reinterpret_cast<hegel_jank_fn_test_case_is_nondeterministic>(hegel_jank_find_symbol(bindings->library, "hegel_test_case_is_nondeterministic"));
  bindings->test_case_printer = reinterpret_cast<hegel_jank_fn_test_case_printer>(hegel_jank_find_symbol(bindings->library, "hegel_test_case_printer"));
  bindings->version = reinterpret_cast<hegel_jank_fn_version>(hegel_jank_find_symbol(bindings->library, "hegel_version"));
  hegel_jank_active_bindings = bindings;
  return bindings;
}

inline hegel_jank_bindings *hegel_jank_current_bindings()
{
  if(hegel_jank_active_bindings == nullptr) { throw std::runtime_error{"libhegel bindings are not loaded"}; }
  return hegel_jank_active_bindings;
}

inline std::int32_t hegel_jank_call_collection_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_collection * arg1)
{
  return bindings->collection_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_collection_more(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_collection * arg2, std::uint8_t * arg3)
{
  return bindings->collection_more(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_collection_reject(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_collection * arg2, char const * arg3)
{
  return bindings->collection_reject(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_context_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0)
{
  return bindings->context_free(arg0);
}

inline char const * hegel_jank_call_context_last_error(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0)
{
  return bindings->context_last_error(arg0);
}

inline hegel_jank_hegel_context * hegel_jank_call_context_new(hegel_jank_bindings *bindings)
{
  return bindings->context_new();
}

inline std::int32_t hegel_jank_call_event(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, char const * arg2)
{
  return bindings->event(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_event_value(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, double arg2, char const * arg3)
{
  return bindings->event_value(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_failure_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_failure * arg1)
{
  return bindings->failure_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_failure_origin(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_failure * arg1, char const * * arg2)
{
  return bindings->failure_origin(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_failure_reproduction_blob(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_failure * arg1, char const * * arg2)
{
  return bindings->failure_reproduction_blob(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_generate_boolean(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, double arg2, std::uint8_t arg3, std::uint8_t arg4, std::uint8_t * arg5)
{
  return bindings->generate_boolean(arg0, arg1, arg2, arg3, arg4, arg5);
}

inline std::int32_t hegel_jank_call_generate_bytes(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint64_t arg2, std::uint64_t arg3, hegel_jank_hegel_bytes_result * arg4)
{
  return bindings->generate_bytes(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_generate_bytes_result_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_bytes_result * arg1)
{
  return bindings->generate_bytes_result_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_generate_date(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_date arg2, hegel_jank_hegel_date arg3, hegel_jank_hegel_date * arg4)
{
  return bindings->generate_date(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_generate_datetime(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_datetime arg2, hegel_jank_hegel_datetime arg3, hegel_jank_hegel_datetime * arg4)
{
  return bindings->generate_datetime(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_generate_float(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint32_t arg2, double arg3, double arg4, std::uint8_t arg5, std::uint8_t arg6, std::uint8_t arg7, std::uint8_t arg8, double arg9, double * arg10)
{
  return bindings->generate_float(arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
}

inline std::int32_t hegel_jank_call_generate_integer(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::int64_t arg2, std::int64_t arg3, std::int64_t * arg4)
{
  return bindings->generate_integer(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_generate_integer_big(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t * arg2, std::size_t arg3, std::uint8_t * arg4, std::size_t arg5, std::uint8_t * arg6, std::size_t arg7, std::size_t * arg8)
{
  return bindings->generate_integer_big(arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
}

inline std::int32_t hegel_jank_call_generate_ipv4(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t * arg2)
{
  return bindings->generate_ipv4(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_generate_ipv6(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t * arg2)
{
  return bindings->generate_ipv6(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_generate_string(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_string_generator * arg2, hegel_jank_hegel_string_result * arg3)
{
  return bindings->generate_string(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_generate_string_result_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_string_result * arg1)
{
  return bindings->generate_string_result_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_generate_time(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_time arg2, hegel_jank_hegel_time arg3, hegel_jank_hegel_time * arg4)
{
  return bindings->generate_time(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_generate_uuid(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t arg2, std::uint8_t arg3, std::uint8_t * arg4)
{
  return bindings->generate_uuid(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_mark_complete(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint32_t arg2, char const * arg3)
{
  return bindings->mark_complete(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_new_collection(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint64_t arg2, std::uint64_t arg3, hegel_jank_hegel_collection * * arg4)
{
  return bindings->new_collection(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_new_pool(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_pool * * arg2)
{
  return bindings->new_pool(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_new_recursion(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint64_t arg2, std::uint64_t arg3, hegel_jank_hegel_recursion * * arg4)
{
  return bindings->new_recursion(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_new_state_machine(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, char const * * arg2, std::int64_t * arg3, std::size_t arg4, char const * * arg5, std::size_t arg6, std::int64_t arg7, std::int64_t arg8, hegel_jank_hegel_state_machine * * arg9, std::int64_t * arg10)
{
  return bindings->new_state_machine(arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
}

inline std::int32_t hegel_jank_call_next_test_case(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run * arg1, hegel_jank_hegel_test_case * * arg2)
{
  return bindings->next_test_case(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_note(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t * arg2, std::size_t arg3)
{
  return bindings->note(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_pool_add(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_pool * arg2, std::int64_t * arg3)
{
  return bindings->pool_add(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_pool_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_pool * arg1)
{
  return bindings->pool_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_pool_generate(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_pool * arg2, std::uint8_t arg3, std::int64_t * arg4)
{
  return bindings->pool_generate(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_printer_abort_speculative(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1)
{
  return bindings->printer_abort_speculative(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_begin_group(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint64_t arg2, std::uint8_t * arg3, std::size_t arg4)
{
  return bindings->printer_begin_group(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_printer_begin_speculative(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1)
{
  return bindings->printer_begin_speculative(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_breakable(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint8_t * arg2, std::size_t arg3)
{
  return bindings->printer_breakable(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_printer_comment(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint8_t * arg2, std::size_t arg3)
{
  return bindings->printer_comment(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_printer_commit_speculative(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1)
{
  return bindings->printer_commit_speculative(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_deferred(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, hegel_jank_hegel_printer * * arg2)
{
  return bindings->printer_deferred(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_printer_end_group(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint8_t * arg2, std::size_t arg3)
{
  return bindings->printer_end_group(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_printer_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1)
{
  return bindings->printer_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_hard_break(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1)
{
  return bindings->printer_hard_break(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_if_break(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint8_t * arg2, std::size_t arg3)
{
  return bindings->printer_if_break(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_printer_is_live(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint8_t * arg2)
{
  return bindings->printer_is_live(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_printer_new(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer_options * arg1, hegel_jank_hegel_printer * * arg2)
{
  return bindings->printer_new(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_printer_options_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer_options * arg1)
{
  return bindings->printer_options_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_options_new(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer_options * * arg1)
{
  return bindings->printer_options_new(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_options_set_max_width(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer_options * arg1, std::uint64_t arg2)
{
  return bindings->printer_options_set_max_width(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_printer_resolve(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1)
{
  return bindings->printer_resolve(arg0, arg1);
}

inline std::int32_t hegel_jank_call_printer_shift_indent(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::int64_t arg2)
{
  return bindings->printer_shift_indent(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_printer_text(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, std::uint8_t * arg2, std::size_t arg3)
{
  return bindings->printer_text(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_printer_value(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer * arg1, hegel_jank_hegel_printer_value_result * arg2)
{
  return bindings->printer_value(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_printer_value_result_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_printer_value_result * arg1)
{
  return bindings->printer_value_result_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_recursion_branch(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_recursion * arg2, std::uint64_t arg3, std::uint8_t * arg4)
{
  return bindings->recursion_branch(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_recursion_finish(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_recursion * arg2)
{
  return bindings->recursion_finish(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_recursion_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_recursion * arg1)
{
  return bindings->recursion_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_recursion_leaf(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_recursion * arg2)
{
  return bindings->recursion_leaf(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_recursion_retry(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_recursion * arg2)
{
  return bindings->recursion_retry(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_run_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run * arg1)
{
  return bindings->run_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_run_result(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run * arg1, hegel_jank_hegel_run_result * * arg2)
{
  return bindings->run_result(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_run_result_error(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run_result * arg1, char const * * arg2)
{
  return bindings->run_result_error(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_run_result_failure(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run_result * arg1, std::size_t arg2, hegel_jank_hegel_failure * * arg3)
{
  return bindings->run_result_failure(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_run_result_failure_count(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run_result * arg1, std::size_t * arg2)
{
  return bindings->run_result_failure_count(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_run_result_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run_result * arg1)
{
  return bindings->run_result_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_run_result_status(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_run_result * arg1, std::int32_t * arg2)
{
  return bindings->run_result_status(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_run_start(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, hegel_jank_hegel_output_callback arg2, void * arg3, hegel_jank_hegel_run * * arg4)
{
  return bindings->run_start(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_settings_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1)
{
  return bindings->settings_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_settings_new(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * * arg1)
{
  return bindings->settings_new(arg0, arg1);
}

inline std::int32_t hegel_jank_call_settings_set_backend(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint32_t arg2)
{
  return bindings->settings_set_backend(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_database(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, char const * arg2)
{
  return bindings->settings_set_database(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_database_key(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, char const * arg2)
{
  return bindings->settings_set_database_key(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_derandomize(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint8_t arg2)
{
  return bindings->settings_set_derandomize(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_phases(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint32_t arg2)
{
  return bindings->settings_set_phases(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_report_multiple_failures(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint8_t arg2)
{
  return bindings->settings_set_report_multiple_failures(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_seed(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint64_t arg2, std::uint8_t arg3)
{
  return bindings->settings_set_seed(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_settings_set_show_statistics(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint8_t arg2)
{
  return bindings->settings_set_show_statistics(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_stateful_step_count(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::int64_t arg2)
{
  return bindings->settings_set_stateful_step_count(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_suppress_health_check(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint32_t arg2)
{
  return bindings->settings_set_suppress_health_check(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_test_cases(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint64_t arg2)
{
  return bindings->settings_set_test_cases(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_settings_set_verbosity(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, std::uint32_t arg2)
{
  return bindings->settings_set_verbosity(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_start_span(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint64_t arg2)
{
  return bindings->start_span(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_state_machine_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_state_machine * arg1)
{
  return bindings->state_machine_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_state_machine_next_group(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_state_machine * arg2, std::int64_t * arg3)
{
  return bindings->state_machine_next_group(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_state_machine_next_rule(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_state_machine * arg2, std::int64_t arg3, std::int64_t * arg4)
{
  return bindings->state_machine_next_rule(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_state_machine_rule_rejected(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_state_machine * arg2, std::int64_t arg3)
{
  return bindings->state_machine_rule_rejected(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_state_machine_should_check_invariant(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_state_machine * arg2, std::int64_t arg3, std::uint8_t * arg4)
{
  return bindings->state_machine_should_check_invariant(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_stop_span(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t arg2)
{
  return bindings->stop_span(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_string_generator_domain(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, std::uint64_t arg1, hegel_jank_hegel_string_generator * * arg2)
{
  return bindings->string_generator_domain(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_string_generator_email(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_string_generator * * arg1)
{
  return bindings->string_generator_email(arg0, arg1);
}

inline std::int32_t hegel_jank_call_string_generator_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_string_generator * arg1)
{
  return bindings->string_generator_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_string_generator_regex(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, char const * arg1, std::uint8_t arg2, hegel_jank_hegel_string_generator * arg3, hegel_jank_hegel_string_generator * * arg4)
{
  return bindings->string_generator_regex(arg0, arg1, arg2, arg3, arg4);
}

inline std::int32_t hegel_jank_call_string_generator_text(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, std::uint64_t arg1, std::uint64_t arg2, char const * arg3, std::uint32_t arg4, std::uint32_t arg5, char const * * arg6, std::size_t arg7, char const * * arg8, std::size_t arg9, std::uint8_t * arg10, std::size_t arg11, std::uint8_t * arg12, std::size_t arg13, hegel_jank_hegel_string_generator * * arg14)
{
  return bindings->string_generator_text(arg0, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
}

inline std::int32_t hegel_jank_call_string_generator_url(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_string_generator * * arg1)
{
  return bindings->string_generator_url(arg0, arg1);
}

inline std::int32_t hegel_jank_call_target(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, double arg2, char const * arg3)
{
  return bindings->target(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_test_case_clone(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_test_case * * arg2)
{
  return bindings->test_case_clone(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_test_case_free(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1)
{
  return bindings->test_case_free(arg0, arg1);
}

inline std::int32_t hegel_jank_call_test_case_from_blob(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_settings * arg1, char const * arg2, hegel_jank_hegel_output_callback arg3, void * arg4, hegel_jank_hegel_test_case * * arg5)
{
  return bindings->test_case_from_blob(arg0, arg1, arg2, arg3, arg4, arg5);
}

inline std::int32_t hegel_jank_call_test_case_is_nondeterministic(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, std::uint8_t * arg2)
{
  return bindings->test_case_is_nondeterministic(arg0, arg1, arg2);
}

inline std::int32_t hegel_jank_call_test_case_printer(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, hegel_jank_hegel_test_case * arg1, hegel_jank_hegel_printer_options * arg2, hegel_jank_hegel_printer * * arg3)
{
  return bindings->test_case_printer(arg0, arg1, arg2, arg3);
}

inline std::int32_t hegel_jank_call_version(hegel_jank_bindings *bindings, hegel_jank_hegel_context * arg0, char const * * arg1)
{
  return bindings->version(arg0, arg1);
}

inline void *hegel_jank_null_pointer() { return nullptr; }
inline void *hegel_jank_alloc(std::size_t size) { return std::calloc(1, size == 0 ? 1 : size); }
inline void hegel_jank_free(void *pointer) { std::free(pointer); }
inline bool hegel_jank_null_pointer_p(void *pointer) { return pointer == nullptr; }
inline void *hegel_jank_read_pointer(void *pointer, std::size_t offset) { return *reinterpret_cast<void **>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_pointer(void *pointer, std::size_t offset, void *value) { *reinterpret_cast<void **>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::uint8_t hegel_jank_read_c_bool(void *pointer, std::size_t offset) { return *reinterpret_cast<std::uint8_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_bool(void *pointer, std::size_t offset, std::uint8_t value) { *reinterpret_cast<std::uint8_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline double hegel_jank_read_c_double(void *pointer, std::size_t offset) { return *reinterpret_cast<double *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_double(void *pointer, std::size_t offset, double value) { *reinterpret_cast<double *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline float hegel_jank_read_c_float(void *pointer, std::size_t offset) { return *reinterpret_cast<float *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_float(void *pointer, std::size_t offset, float value) { *reinterpret_cast<float *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::int16_t hegel_jank_read_c_int16(void *pointer, std::size_t offset) { return *reinterpret_cast<std::int16_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_int16(void *pointer, std::size_t offset, std::int16_t value) { *reinterpret_cast<std::int16_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::int32_t hegel_jank_read_c_int32(void *pointer, std::size_t offset) { return *reinterpret_cast<std::int32_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_int32(void *pointer, std::size_t offset, std::int32_t value) { *reinterpret_cast<std::int32_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::int64_t hegel_jank_read_c_int64(void *pointer, std::size_t offset) { return *reinterpret_cast<std::int64_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_int64(void *pointer, std::size_t offset, std::int64_t value) { *reinterpret_cast<std::int64_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::int8_t hegel_jank_read_c_int8(void *pointer, std::size_t offset) { return *reinterpret_cast<std::int8_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_int8(void *pointer, std::size_t offset, std::int8_t value) { *reinterpret_cast<std::int8_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::size_t hegel_jank_read_c_size(void *pointer, std::size_t offset) { return *reinterpret_cast<std::size_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_size(void *pointer, std::size_t offset, std::size_t value) { *reinterpret_cast<std::size_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::uint16_t hegel_jank_read_c_uint16(void *pointer, std::size_t offset) { return *reinterpret_cast<std::uint16_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_uint16(void *pointer, std::size_t offset, std::uint16_t value) { *reinterpret_cast<std::uint16_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::uint32_t hegel_jank_read_c_uint32(void *pointer, std::size_t offset) { return *reinterpret_cast<std::uint32_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_uint32(void *pointer, std::size_t offset, std::uint32_t value) { *reinterpret_cast<std::uint32_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::uint64_t hegel_jank_read_c_uint64(void *pointer, std::size_t offset) { return *reinterpret_cast<std::uint64_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_uint64(void *pointer, std::size_t offset, std::uint64_t value) { *reinterpret_cast<std::uint64_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::uint8_t hegel_jank_read_c_uint8(void *pointer, std::size_t offset) { return *reinterpret_cast<std::uint8_t *>(static_cast<std::uint8_t *>(pointer) + offset); }
inline void hegel_jank_write_c_uint8(void *pointer, std::size_t offset, std::uint8_t value) { *reinterpret_cast<std::uint8_t *>(static_cast<std::uint8_t *>(pointer) + offset) = value; }
inline std::string hegel_jank_read_utf8(void *pointer, std::size_t length) { return std::string{static_cast<char const *>(pointer), length}; }
inline std::size_t hegel_jank_write_utf8(void *pointer, ::jank::runtime::object_ref value) { auto string = ::jank::runtime::try_object<::jank::runtime::obj::persistent_string>(value); std::memcpy(pointer, string->data.data(), string->data.size()); return string->data.size(); }
inline void *hegel_jank_string_dup(::jank::runtime::object_ref value) { auto string = ::jank::runtime::try_object<::jank::runtime::obj::persistent_string>(value); auto *result = static_cast<char *>(hegel_jank_alloc(string->data.size() + 1)); std::memcpy(result, string->data.data(), string->data.size()); result[string->data.size()] = '\0'; return result; }
inline std::string hegel_jank_native_string(void *pointer) { return pointer == nullptr ? std::string{} : std::string{static_cast<char const *>(pointer)}; }

inline std::uint64_t hegel_jank_to_uint64(::jank::runtime::object_ref value) { if(value.get_type() == ::jank::runtime::object_type::big_integer) { return ::jank::runtime::try_object<::jank::runtime::obj::big_integer>(value)->data.convert_to<std::uint64_t>(); } return ::jank::runtime::convert<std::uint64_t>::from_object(value); }

static_assert(std::is_standard_layout_v<hegel_jank_hegel_bytes_result>);
static_assert(sizeof(hegel_jank_hegel_bytes_result) == 16);
static_assert(alignof(hegel_jank_hegel_bytes_result) == 8);
static_assert(offsetof(hegel_jank_hegel_bytes_result, data) == 0);
static_assert(offsetof(hegel_jank_hegel_bytes_result, len) == 8);

static_assert(std::is_standard_layout_v<hegel_jank_hegel_date>);
static_assert(sizeof(hegel_jank_hegel_date) == 8);
static_assert(alignof(hegel_jank_hegel_date) == 4);
static_assert(offsetof(hegel_jank_hegel_date, year) == 0);
static_assert(offsetof(hegel_jank_hegel_date, month) == 4);
static_assert(offsetof(hegel_jank_hegel_date, day) == 5);

static_assert(std::is_standard_layout_v<hegel_jank_hegel_printer_value_result>);
static_assert(sizeof(hegel_jank_hegel_printer_value_result) == 16);
static_assert(alignof(hegel_jank_hegel_printer_value_result) == 8);
static_assert(offsetof(hegel_jank_hegel_printer_value_result, data) == 0);
static_assert(offsetof(hegel_jank_hegel_printer_value_result, len) == 8);

static_assert(std::is_standard_layout_v<hegel_jank_hegel_string_result>);
static_assert(sizeof(hegel_jank_hegel_string_result) == 16);
static_assert(alignof(hegel_jank_hegel_string_result) == 8);
static_assert(offsetof(hegel_jank_hegel_string_result, data) == 0);
static_assert(offsetof(hegel_jank_hegel_string_result, len) == 8);

static_assert(std::is_standard_layout_v<hegel_jank_hegel_time>);
static_assert(sizeof(hegel_jank_hegel_time) == 8);
static_assert(alignof(hegel_jank_hegel_time) == 4);
static_assert(offsetof(hegel_jank_hegel_time, hour) == 0);
static_assert(offsetof(hegel_jank_hegel_time, minute) == 1);
static_assert(offsetof(hegel_jank_hegel_time, second) == 2);
static_assert(offsetof(hegel_jank_hegel_time, nanosecond) == 4);

static_assert(std::is_standard_layout_v<hegel_jank_hegel_datetime>);
static_assert(sizeof(hegel_jank_hegel_datetime) == 16);
static_assert(alignof(hegel_jank_hegel_datetime) == 4);
static_assert(offsetof(hegel_jank_hegel_datetime, date) == 0);
static_assert(offsetof(hegel_jank_hegel_datetime, time) == 8);
