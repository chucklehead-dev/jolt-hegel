// Generated from resources/hegel/abi.edn. DO NOT EDIT.
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;

namespace Hegel.Native;

[StructLayout(LayoutKind.Sequential)]
public struct HegelBytesResult
{
    public IntPtr Data;
    public nuint Len;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelDate
{
    public int Year;
    public byte Month;
    public byte Day;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelPrinterValueResult
{
    public IntPtr Data;
    public nuint Len;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelStringResult
{
    public IntPtr Data;
    public nuint Len;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelTime
{
    public byte Hour;
    public byte Minute;
    public byte Second;
    public uint Nanosecond;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelDatetime
{
    public HegelDate Date;
    public HegelTime Time;
}

internal static partial class NativeMethods
{
    internal const string LibraryName = "hegel";

    [LibraryImport(LibraryName, EntryPoint = "hegel_collection_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int CollectionFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_collection_more")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int CollectionMore(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_collection_reject")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int CollectionReject(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_context_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ContextFree(IntPtr arg0);

    [LibraryImport(LibraryName, EntryPoint = "hegel_context_last_error")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial IntPtr ContextLastError(IntPtr arg0);

    [LibraryImport(LibraryName, EntryPoint = "hegel_context_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial IntPtr ContextNew();

    [LibraryImport(LibraryName, EntryPoint = "hegel_event")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Event(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_event_value")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int EventValue(IntPtr arg0, IntPtr arg1, double arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_origin")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureOrigin(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_reproduction_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureReproductionBlob(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_boolean")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateBoolean(IntPtr arg0, IntPtr arg1, double arg2, byte arg3, byte arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_bytes")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateBytes(IntPtr arg0, IntPtr arg1, ulong arg2, ulong arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_bytes_result_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateBytesResultFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_date")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateDate(IntPtr arg0, IntPtr arg1, HegelDate arg2, HegelDate arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_datetime")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateDatetime(IntPtr arg0, IntPtr arg1, HegelDatetime arg2, HegelDatetime arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_float")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateFloat(IntPtr arg0, IntPtr arg1, uint arg2, double arg3, double arg4, byte arg5, byte arg6, byte arg7, byte arg8, double arg9, IntPtr arg10);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_integer")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateInteger(IntPtr arg0, IntPtr arg1, long arg2, long arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_integer_big")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateIntegerBig(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3, IntPtr arg4, nuint arg5, IntPtr arg6, nuint arg7, IntPtr arg8);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_ipv4")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateIpv4(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_ipv6")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateIpv6(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_string")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateString(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_string_result_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateStringResultFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_time")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateTime(IntPtr arg0, IntPtr arg1, HegelTime arg2, HegelTime arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_uuid")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateUuid(IntPtr arg0, IntPtr arg1, byte arg2, byte arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_mark_complete")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int MarkComplete(IntPtr arg0, IntPtr arg1, uint arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_collection")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewCollection(IntPtr arg0, IntPtr arg1, ulong arg2, ulong arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_pool")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewPool(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_recursion")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewRecursion(IntPtr arg0, IntPtr arg1, ulong arg2, ulong arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_state_machine")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewStateMachine(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3, nuint arg4, IntPtr arg5, nuint arg6, long arg7, long arg8, IntPtr arg9, IntPtr arg10);

    [LibraryImport(LibraryName, EntryPoint = "hegel_next_test_case")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NextTestCase(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_note")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Note(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_add")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolAdd(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_generate")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolGenerate(IntPtr arg0, IntPtr arg1, IntPtr arg2, byte arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_abort_speculative")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterAbortSpeculative(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_begin_group")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterBeginGroup(IntPtr arg0, IntPtr arg1, ulong arg2, IntPtr arg3, nuint arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_begin_speculative")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterBeginSpeculative(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_breakable")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterBreakable(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_comment")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterComment(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_commit_speculative")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterCommitSpeculative(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_deferred")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterDeferred(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_end_group")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterEndGroup(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_hard_break")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterHardBreak(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_if_break")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterIfBreak(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_is_live")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterIsLive(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterNew(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_options_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterOptionsFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_options_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterOptionsNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_options_set_max_width")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterOptionsSetMaxWidth(IntPtr arg0, IntPtr arg1, ulong arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_resolve")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterResolve(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_shift_indent")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterShiftIndent(IntPtr arg0, IntPtr arg1, long arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_text")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterText(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_value")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterValue(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_value_result_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterValueResultFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_recursion_branch")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RecursionBranch(IntPtr arg0, IntPtr arg1, IntPtr arg2, ulong arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_recursion_finish")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RecursionFinish(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_recursion_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RecursionFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_recursion_leaf")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RecursionLeaf(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_recursion_retry")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RecursionRetry(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResult(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_error")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultError(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_failure")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultFailure(IntPtr arg0, IntPtr arg1, nuint arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_failure_count")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultFailureCount(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_status")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultStatus(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_start")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunStart(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_backend")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetBackend(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_database")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetDatabase(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_database_key")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetDatabaseKey(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_derandomize")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetDerandomize(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_phases")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetPhases(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_report_multiple_failures")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetReportMultipleFailures(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_seed")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetSeed(IntPtr arg0, IntPtr arg1, ulong arg2, byte arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_show_statistics")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetShowStatistics(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_stateful_step_count")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetStatefulStepCount(IntPtr arg0, IntPtr arg1, long arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_suppress_health_check")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetSuppressHealthCheck(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_test_cases")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetTestCases(IntPtr arg0, IntPtr arg1, ulong arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_verbosity")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetVerbosity(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_start_span")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StartSpan(IntPtr arg0, IntPtr arg1, ulong arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_next_group")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineNextGroup(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_next_rule")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineNextRule(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_rule_rejected")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineRuleRejected(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_should_check_invariant")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineShouldCheckInvariant(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_stop_span")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StopSpan(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_domain")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorDomain(IntPtr arg0, ulong arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_email")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorEmail(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_regex")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorRegex(IntPtr arg0, IntPtr arg1, byte arg2, IntPtr arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_text")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorText(IntPtr arg0, ulong arg1, ulong arg2, IntPtr arg3, uint arg4, uint arg5, IntPtr arg6, nuint arg7, IntPtr arg8, nuint arg9, IntPtr arg10, nuint arg11, IntPtr arg12, nuint arg13, IntPtr arg14);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_url")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorUrl(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_target")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Target(IntPtr arg0, IntPtr arg1, double arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_clone")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseClone(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_from_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseFromBlob(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_is_nondeterministic")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseIsNondeterministic(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_printer")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCasePrinter(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_version")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Version(IntPtr arg0, IntPtr arg1);
}

public static partial class Bridge
{
    public static IReadOnlyList<string> ExpectedSymbols { get; } = new string[]
    {
        "hegel_collection_free",
        "hegel_collection_more",
        "hegel_collection_reject",
        "hegel_context_free",
        "hegel_context_last_error",
        "hegel_context_new",
        "hegel_event",
        "hegel_event_value",
        "hegel_failure_free",
        "hegel_failure_origin",
        "hegel_failure_reproduction_blob",
        "hegel_generate_boolean",
        "hegel_generate_bytes",
        "hegel_generate_bytes_result_free",
        "hegel_generate_date",
        "hegel_generate_datetime",
        "hegel_generate_float",
        "hegel_generate_integer",
        "hegel_generate_integer_big",
        "hegel_generate_ipv4",
        "hegel_generate_ipv6",
        "hegel_generate_string",
        "hegel_generate_string_result_free",
        "hegel_generate_time",
        "hegel_generate_uuid",
        "hegel_mark_complete",
        "hegel_new_collection",
        "hegel_new_pool",
        "hegel_new_recursion",
        "hegel_new_state_machine",
        "hegel_next_test_case",
        "hegel_note",
        "hegel_pool_add",
        "hegel_pool_free",
        "hegel_pool_generate",
        "hegel_printer_abort_speculative",
        "hegel_printer_begin_group",
        "hegel_printer_begin_speculative",
        "hegel_printer_breakable",
        "hegel_printer_comment",
        "hegel_printer_commit_speculative",
        "hegel_printer_deferred",
        "hegel_printer_end_group",
        "hegel_printer_free",
        "hegel_printer_hard_break",
        "hegel_printer_if_break",
        "hegel_printer_is_live",
        "hegel_printer_new",
        "hegel_printer_options_free",
        "hegel_printer_options_new",
        "hegel_printer_options_set_max_width",
        "hegel_printer_resolve",
        "hegel_printer_shift_indent",
        "hegel_printer_text",
        "hegel_printer_value",
        "hegel_printer_value_result_free",
        "hegel_recursion_branch",
        "hegel_recursion_finish",
        "hegel_recursion_free",
        "hegel_recursion_leaf",
        "hegel_recursion_retry",
        "hegel_run_free",
        "hegel_run_result",
        "hegel_run_result_error",
        "hegel_run_result_failure",
        "hegel_run_result_failure_count",
        "hegel_run_result_free",
        "hegel_run_result_status",
        "hegel_run_start",
        "hegel_settings_free",
        "hegel_settings_new",
        "hegel_settings_set_backend",
        "hegel_settings_set_database",
        "hegel_settings_set_database_key",
        "hegel_settings_set_derandomize",
        "hegel_settings_set_phases",
        "hegel_settings_set_report_multiple_failures",
        "hegel_settings_set_seed",
        "hegel_settings_set_show_statistics",
        "hegel_settings_set_stateful_step_count",
        "hegel_settings_set_suppress_health_check",
        "hegel_settings_set_test_cases",
        "hegel_settings_set_verbosity",
        "hegel_start_span",
        "hegel_state_machine_free",
        "hegel_state_machine_next_group",
        "hegel_state_machine_next_rule",
        "hegel_state_machine_rule_rejected",
        "hegel_state_machine_should_check_invariant",
        "hegel_stop_span",
        "hegel_string_generator_domain",
        "hegel_string_generator_email",
        "hegel_string_generator_free",
        "hegel_string_generator_regex",
        "hegel_string_generator_text",
        "hegel_string_generator_url",
        "hegel_target",
        "hegel_test_case_clone",
        "hegel_test_case_free",
        "hegel_test_case_from_blob",
        "hegel_test_case_is_nondeterministic",
        "hegel_test_case_printer",
        "hegel_version",
    };

    public static object? Invoke(string functionId, object?[]? values)
    {
        var args = values ?? Array.Empty<object?>();
        return functionId switch
        {
            "collection-free" => InvokeCollectionFree(args),
            "collection-more" => InvokeCollectionMore(args),
            "collection-reject" => InvokeCollectionReject(args),
            "context-free" => InvokeContextFree(args),
            "context-last-error" => InvokeContextLastError(args),
            "context-new" => InvokeContextNew(args),
            "event" => InvokeEvent(args),
            "event-value" => InvokeEventValue(args),
            "failure-free" => InvokeFailureFree(args),
            "failure-origin" => InvokeFailureOrigin(args),
            "failure-reproduction-blob" => InvokeFailureReproductionBlob(args),
            "generate-boolean" => InvokeGenerateBoolean(args),
            "generate-bytes" => InvokeGenerateBytes(args),
            "generate-bytes-result-free" => InvokeGenerateBytesResultFree(args),
            "generate-date" => InvokeGenerateDate(args),
            "generate-datetime" => InvokeGenerateDatetime(args),
            "generate-float" => InvokeGenerateFloat(args),
            "generate-integer" => InvokeGenerateInteger(args),
            "generate-integer-big" => InvokeGenerateIntegerBig(args),
            "generate-ipv4" => InvokeGenerateIpv4(args),
            "generate-ipv6" => InvokeGenerateIpv6(args),
            "generate-string" => InvokeGenerateString(args),
            "generate-string-result-free" => InvokeGenerateStringResultFree(args),
            "generate-time" => InvokeGenerateTime(args),
            "generate-uuid" => InvokeGenerateUuid(args),
            "mark-complete" => InvokeMarkComplete(args),
            "new-collection" => InvokeNewCollection(args),
            "new-pool" => InvokeNewPool(args),
            "new-recursion" => InvokeNewRecursion(args),
            "new-state-machine" => InvokeNewStateMachine(args),
            "next-test-case" => InvokeNextTestCase(args),
            "note" => InvokeNote(args),
            "pool-add" => InvokePoolAdd(args),
            "pool-free" => InvokePoolFree(args),
            "pool-generate" => InvokePoolGenerate(args),
            "printer-abort-speculative" => InvokePrinterAbortSpeculative(args),
            "printer-begin-group" => InvokePrinterBeginGroup(args),
            "printer-begin-speculative" => InvokePrinterBeginSpeculative(args),
            "printer-breakable" => InvokePrinterBreakable(args),
            "printer-comment" => InvokePrinterComment(args),
            "printer-commit-speculative" => InvokePrinterCommitSpeculative(args),
            "printer-deferred" => InvokePrinterDeferred(args),
            "printer-end-group" => InvokePrinterEndGroup(args),
            "printer-free" => InvokePrinterFree(args),
            "printer-hard-break" => InvokePrinterHardBreak(args),
            "printer-if-break" => InvokePrinterIfBreak(args),
            "printer-is-live" => InvokePrinterIsLive(args),
            "printer-new" => InvokePrinterNew(args),
            "printer-options-free" => InvokePrinterOptionsFree(args),
            "printer-options-new" => InvokePrinterOptionsNew(args),
            "printer-options-set-max-width" => InvokePrinterOptionsSetMaxWidth(args),
            "printer-resolve" => InvokePrinterResolve(args),
            "printer-shift-indent" => InvokePrinterShiftIndent(args),
            "printer-text" => InvokePrinterText(args),
            "printer-value" => InvokePrinterValue(args),
            "printer-value-result-free" => InvokePrinterValueResultFree(args),
            "recursion-branch" => InvokeRecursionBranch(args),
            "recursion-finish" => InvokeRecursionFinish(args),
            "recursion-free" => InvokeRecursionFree(args),
            "recursion-leaf" => InvokeRecursionLeaf(args),
            "recursion-retry" => InvokeRecursionRetry(args),
            "run-free" => InvokeRunFree(args),
            "run-result" => InvokeRunResult(args),
            "run-result-error" => InvokeRunResultError(args),
            "run-result-failure" => InvokeRunResultFailure(args),
            "run-result-failure-count" => InvokeRunResultFailureCount(args),
            "run-result-free" => InvokeRunResultFree(args),
            "run-result-status" => InvokeRunResultStatus(args),
            "run-start" => InvokeRunStart(args),
            "settings-free" => InvokeSettingsFree(args),
            "settings-new" => InvokeSettingsNew(args),
            "settings-set-backend" => InvokeSettingsSetBackend(args),
            "settings-set-database" => InvokeSettingsSetDatabase(args),
            "settings-set-database-key" => InvokeSettingsSetDatabaseKey(args),
            "settings-set-derandomize" => InvokeSettingsSetDerandomize(args),
            "settings-set-phases" => InvokeSettingsSetPhases(args),
            "settings-set-report-multiple-failures" => InvokeSettingsSetReportMultipleFailures(args),
            "settings-set-seed" => InvokeSettingsSetSeed(args),
            "settings-set-show-statistics" => InvokeSettingsSetShowStatistics(args),
            "settings-set-stateful-step-count" => InvokeSettingsSetStatefulStepCount(args),
            "settings-set-suppress-health-check" => InvokeSettingsSetSuppressHealthCheck(args),
            "settings-set-test-cases" => InvokeSettingsSetTestCases(args),
            "settings-set-verbosity" => InvokeSettingsSetVerbosity(args),
            "start-span" => InvokeStartSpan(args),
            "state-machine-free" => InvokeStateMachineFree(args),
            "state-machine-next-group" => InvokeStateMachineNextGroup(args),
            "state-machine-next-rule" => InvokeStateMachineNextRule(args),
            "state-machine-rule-rejected" => InvokeStateMachineRuleRejected(args),
            "state-machine-should-check-invariant" => InvokeStateMachineShouldCheckInvariant(args),
            "stop-span" => InvokeStopSpan(args),
            "string-generator-domain" => InvokeStringGeneratorDomain(args),
            "string-generator-email" => InvokeStringGeneratorEmail(args),
            "string-generator-free" => InvokeStringGeneratorFree(args),
            "string-generator-regex" => InvokeStringGeneratorRegex(args),
            "string-generator-text" => InvokeStringGeneratorText(args),
            "string-generator-url" => InvokeStringGeneratorUrl(args),
            "target" => InvokeTarget(args),
            "test-case-clone" => InvokeTestCaseClone(args),
            "test-case-free" => InvokeTestCaseFree(args),
            "test-case-from-blob" => InvokeTestCaseFromBlob(args),
            "test-case-is-nondeterministic" => InvokeTestCaseIsNondeterministic(args),
            "test-case-printer" => InvokeTestCasePrinter(args),
            "version" => InvokeVersion(args),
            _ => throw new ArgumentOutOfRangeException(nameof(functionId), functionId, "Unknown libhegel function"),
        };
    }

    private static object? InvokeCollectionFree(object?[] args)
    {
        RequireArity("collection-free", args, 2);
        return NativeMethods.CollectionFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeCollectionMore(object?[] args)
    {
        RequireArity("collection-more", args, 4);
        return NativeMethods.CollectionMore(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeCollectionReject(object?[] args)
    {
        RequireArity("collection-reject", args, 4);
        return NativeMethods.CollectionReject(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeContextFree(object?[] args)
    {
        RequireArity("context-free", args, 1);
        return NativeMethods.ContextFree(ToIntPtr(args[0]));
    }

    private static object? InvokeContextLastError(object?[] args)
    {
        RequireArity("context-last-error", args, 1);
        return NativeMethods.ContextLastError(ToIntPtr(args[0]));
    }

    private static object? InvokeContextNew(object?[] args)
    {
        RequireArity("context-new", args, 0);
        return NativeMethods.ContextNew();
    }

    private static object? InvokeEvent(object?[] args)
    {
        RequireArity("event", args, 3);
        return NativeMethods.Event(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeEventValue(object?[] args)
    {
        RequireArity("event-value", args, 4);
        return NativeMethods.EventValue(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToDouble(args[2], CultureInfo.InvariantCulture), ToIntPtr(args[3]));
    }

    private static object? InvokeFailureFree(object?[] args)
    {
        RequireArity("failure-free", args, 2);
        return NativeMethods.FailureFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeFailureOrigin(object?[] args)
    {
        RequireArity("failure-origin", args, 3);
        return NativeMethods.FailureOrigin(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeFailureReproductionBlob(object?[] args)
    {
        RequireArity("failure-reproduction-blob", args, 3);
        return NativeMethods.FailureReproductionBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeGenerateBoolean(object?[] args)
    {
        RequireArity("generate-boolean", args, 6);
        return NativeMethods.GenerateBoolean(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToDouble(args[2], CultureInfo.InvariantCulture), ToByte(args[3]), ToByte(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateBytes(object?[] args)
    {
        RequireArity("generate-bytes", args, 5);
        return NativeMethods.GenerateBytes(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToUInt64(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeGenerateBytesResultFree(object?[] args)
    {
        RequireArity("generate-bytes-result-free", args, 2);
        return NativeMethods.GenerateBytesResultFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeGenerateDate(object?[] args)
    {
        RequireArity("generate-date", args, 5);
        return NativeMethods.GenerateDate(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[3])), ToIntPtr(args[4]));
    }

    private static object? InvokeGenerateDatetime(object?[] args)
    {
        RequireArity("generate-datetime", args, 5);
        return NativeMethods.GenerateDatetime(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[3])), ToIntPtr(args[4]));
    }

    private static object? InvokeGenerateFloat(object?[] args)
    {
        RequireArity("generate-float", args, 11);
        return NativeMethods.GenerateFloat(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture), Convert.ToDouble(args[3], CultureInfo.InvariantCulture), Convert.ToDouble(args[4], CultureInfo.InvariantCulture), ToByte(args[5]), ToByte(args[6]), ToByte(args[7]), ToByte(args[8]), Convert.ToDouble(args[9], CultureInfo.InvariantCulture), ToIntPtr(args[10]));
    }

    private static object? InvokeGenerateInteger(object?[] args)
    {
        RequireArity("generate-integer", args, 5);
        return NativeMethods.GenerateInteger(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToInt64(args[2], CultureInfo.InvariantCulture), Convert.ToInt64(args[3], CultureInfo.InvariantCulture), ToIntPtr(args[4]));
    }

    private static object? InvokeGenerateIntegerBig(object?[] args)
    {
        RequireArity("generate-integer-big", args, 9);
        return NativeMethods.GenerateIntegerBig(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]), ToIntPtr(args[4]), ToNUInt(args[5]), ToIntPtr(args[6]), ToNUInt(args[7]), ToIntPtr(args[8]));
    }

    private static object? InvokeGenerateIpv4(object?[] args)
    {
        RequireArity("generate-ipv4", args, 3);
        return NativeMethods.GenerateIpv4(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeGenerateIpv6(object?[] args)
    {
        RequireArity("generate-ipv6", args, 3);
        return NativeMethods.GenerateIpv6(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeGenerateString(object?[] args)
    {
        RequireArity("generate-string", args, 4);
        return NativeMethods.GenerateString(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGenerateStringResultFree(object?[] args)
    {
        RequireArity("generate-string-result-free", args, 2);
        return NativeMethods.GenerateStringResultFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeGenerateTime(object?[] args)
    {
        RequireArity("generate-time", args, 5);
        return NativeMethods.GenerateTime(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[3])), ToIntPtr(args[4]));
    }

    private static object? InvokeGenerateUuid(object?[] args)
    {
        RequireArity("generate-uuid", args, 5);
        return NativeMethods.GenerateUuid(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]), ToByte(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeMarkComplete(object?[] args)
    {
        RequireArity("mark-complete", args, 4);
        return NativeMethods.MarkComplete(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture), ToIntPtr(args[3]));
    }

    private static object? InvokeNewCollection(object?[] args)
    {
        RequireArity("new-collection", args, 5);
        return NativeMethods.NewCollection(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToUInt64(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeNewPool(object?[] args)
    {
        RequireArity("new-pool", args, 3);
        return NativeMethods.NewPool(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeNewRecursion(object?[] args)
    {
        RequireArity("new-recursion", args, 5);
        return NativeMethods.NewRecursion(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToUInt64(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeNewStateMachine(object?[] args)
    {
        RequireArity("new-state-machine", args, 11);
        return NativeMethods.NewStateMachine(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]), ToNUInt(args[4]), ToIntPtr(args[5]), ToNUInt(args[6]), Convert.ToInt64(args[7], CultureInfo.InvariantCulture), Convert.ToInt64(args[8], CultureInfo.InvariantCulture), ToIntPtr(args[9]), ToIntPtr(args[10]));
    }

    private static object? InvokeNextTestCase(object?[] args)
    {
        RequireArity("next-test-case", args, 3);
        return NativeMethods.NextTestCase(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeNote(object?[] args)
    {
        RequireArity("note", args, 4);
        return NativeMethods.Note(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokePoolAdd(object?[] args)
    {
        RequireArity("pool-add", args, 4);
        return NativeMethods.PoolAdd(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokePoolFree(object?[] args)
    {
        RequireArity("pool-free", args, 2);
        return NativeMethods.PoolFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePoolGenerate(object?[] args)
    {
        RequireArity("pool-generate", args, 5);
        return NativeMethods.PoolGenerate(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToByte(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokePrinterAbortSpeculative(object?[] args)
    {
        RequireArity("printer-abort-speculative", args, 2);
        return NativeMethods.PrinterAbortSpeculative(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterBeginGroup(object?[] args)
    {
        RequireArity("printer-begin-group", args, 5);
        return NativeMethods.PrinterBeginGroup(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToIntPtr(args[3]), ToNUInt(args[4]));
    }

    private static object? InvokePrinterBeginSpeculative(object?[] args)
    {
        RequireArity("printer-begin-speculative", args, 2);
        return NativeMethods.PrinterBeginSpeculative(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterBreakable(object?[] args)
    {
        RequireArity("printer-breakable", args, 4);
        return NativeMethods.PrinterBreakable(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokePrinterComment(object?[] args)
    {
        RequireArity("printer-comment", args, 4);
        return NativeMethods.PrinterComment(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokePrinterCommitSpeculative(object?[] args)
    {
        RequireArity("printer-commit-speculative", args, 2);
        return NativeMethods.PrinterCommitSpeculative(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterDeferred(object?[] args)
    {
        RequireArity("printer-deferred", args, 3);
        return NativeMethods.PrinterDeferred(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokePrinterEndGroup(object?[] args)
    {
        RequireArity("printer-end-group", args, 4);
        return NativeMethods.PrinterEndGroup(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokePrinterFree(object?[] args)
    {
        RequireArity("printer-free", args, 2);
        return NativeMethods.PrinterFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterHardBreak(object?[] args)
    {
        RequireArity("printer-hard-break", args, 2);
        return NativeMethods.PrinterHardBreak(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterIfBreak(object?[] args)
    {
        RequireArity("printer-if-break", args, 4);
        return NativeMethods.PrinterIfBreak(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokePrinterIsLive(object?[] args)
    {
        RequireArity("printer-is-live", args, 3);
        return NativeMethods.PrinterIsLive(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokePrinterNew(object?[] args)
    {
        RequireArity("printer-new", args, 3);
        return NativeMethods.PrinterNew(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokePrinterOptionsFree(object?[] args)
    {
        RequireArity("printer-options-free", args, 2);
        return NativeMethods.PrinterOptionsFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterOptionsNew(object?[] args)
    {
        RequireArity("printer-options-new", args, 2);
        return NativeMethods.PrinterOptionsNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterOptionsSetMaxWidth(object?[] args)
    {
        RequireArity("printer-options-set-max-width", args, 3);
        return NativeMethods.PrinterOptionsSetMaxWidth(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]));
    }

    private static object? InvokePrinterResolve(object?[] args)
    {
        RequireArity("printer-resolve", args, 2);
        return NativeMethods.PrinterResolve(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterShiftIndent(object?[] args)
    {
        RequireArity("printer-shift-indent", args, 3);
        return NativeMethods.PrinterShiftIndent(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToInt64(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokePrinterText(object?[] args)
    {
        RequireArity("printer-text", args, 4);
        return NativeMethods.PrinterText(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokePrinterValue(object?[] args)
    {
        RequireArity("printer-value", args, 3);
        return NativeMethods.PrinterValue(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokePrinterValueResultFree(object?[] args)
    {
        RequireArity("printer-value-result-free", args, 2);
        return NativeMethods.PrinterValueResultFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeRecursionBranch(object?[] args)
    {
        RequireArity("recursion-branch", args, 5);
        return NativeMethods.RecursionBranch(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToUInt64(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeRecursionFinish(object?[] args)
    {
        RequireArity("recursion-finish", args, 3);
        return NativeMethods.RecursionFinish(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRecursionFree(object?[] args)
    {
        RequireArity("recursion-free", args, 2);
        return NativeMethods.RecursionFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeRecursionLeaf(object?[] args)
    {
        RequireArity("recursion-leaf", args, 3);
        return NativeMethods.RecursionLeaf(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRecursionRetry(object?[] args)
    {
        RequireArity("recursion-retry", args, 3);
        return NativeMethods.RecursionRetry(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRunFree(object?[] args)
    {
        RequireArity("run-free", args, 2);
        return NativeMethods.RunFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeRunResult(object?[] args)
    {
        RequireArity("run-result", args, 3);
        return NativeMethods.RunResult(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRunResultError(object?[] args)
    {
        RequireArity("run-result-error", args, 3);
        return NativeMethods.RunResultError(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRunResultFailure(object?[] args)
    {
        RequireArity("run-result-failure", args, 4);
        return NativeMethods.RunResultFailure(ToIntPtr(args[0]), ToIntPtr(args[1]), ToNUInt(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeRunResultFailureCount(object?[] args)
    {
        RequireArity("run-result-failure-count", args, 3);
        return NativeMethods.RunResultFailureCount(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRunResultFree(object?[] args)
    {
        RequireArity("run-result-free", args, 2);
        return NativeMethods.RunResultFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeRunResultStatus(object?[] args)
    {
        RequireArity("run-result-status", args, 3);
        return NativeMethods.RunResultStatus(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRunStart(object?[] args)
    {
        RequireArity("run-start", args, 5);
        return NativeMethods.RunStart(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeSettingsFree(object?[] args)
    {
        RequireArity("settings-free", args, 2);
        return NativeMethods.SettingsFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSettingsNew(object?[] args)
    {
        RequireArity("settings-new", args, 2);
        return NativeMethods.SettingsNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSettingsSetBackend(object?[] args)
    {
        RequireArity("settings-set-backend", args, 3);
        return NativeMethods.SettingsSetBackend(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSettingsSetDatabase(object?[] args)
    {
        RequireArity("settings-set-database", args, 3);
        return NativeMethods.SettingsSetDatabase(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsSetDatabaseKey(object?[] args)
    {
        RequireArity("settings-set-database-key", args, 3);
        return NativeMethods.SettingsSetDatabaseKey(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsSetDerandomize(object?[] args)
    {
        RequireArity("settings-set-derandomize", args, 3);
        return NativeMethods.SettingsSetDerandomize(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeSettingsSetPhases(object?[] args)
    {
        RequireArity("settings-set-phases", args, 3);
        return NativeMethods.SettingsSetPhases(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSettingsSetReportMultipleFailures(object?[] args)
    {
        RequireArity("settings-set-report-multiple-failures", args, 3);
        return NativeMethods.SettingsSetReportMultipleFailures(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeSettingsSetSeed(object?[] args)
    {
        RequireArity("settings-set-seed", args, 4);
        return NativeMethods.SettingsSetSeed(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToByte(args[3]));
    }

    private static object? InvokeSettingsSetShowStatistics(object?[] args)
    {
        RequireArity("settings-set-show-statistics", args, 3);
        return NativeMethods.SettingsSetShowStatistics(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeSettingsSetStatefulStepCount(object?[] args)
    {
        RequireArity("settings-set-stateful-step-count", args, 3);
        return NativeMethods.SettingsSetStatefulStepCount(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToInt64(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSettingsSetSuppressHealthCheck(object?[] args)
    {
        RequireArity("settings-set-suppress-health-check", args, 3);
        return NativeMethods.SettingsSetSuppressHealthCheck(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSettingsSetTestCases(object?[] args)
    {
        RequireArity("settings-set-test-cases", args, 3);
        return NativeMethods.SettingsSetTestCases(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]));
    }

    private static object? InvokeSettingsSetVerbosity(object?[] args)
    {
        RequireArity("settings-set-verbosity", args, 3);
        return NativeMethods.SettingsSetVerbosity(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeStartSpan(object?[] args)
    {
        RequireArity("start-span", args, 3);
        return NativeMethods.StartSpan(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]));
    }

    private static object? InvokeStateMachineFree(object?[] args)
    {
        RequireArity("state-machine-free", args, 2);
        return NativeMethods.StateMachineFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeStateMachineNextGroup(object?[] args)
    {
        RequireArity("state-machine-next-group", args, 4);
        return NativeMethods.StateMachineNextGroup(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeStateMachineNextRule(object?[] args)
    {
        RequireArity("state-machine-next-rule", args, 5);
        return NativeMethods.StateMachineNextRule(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Convert.ToInt64(args[3], CultureInfo.InvariantCulture), ToIntPtr(args[4]));
    }

    private static object? InvokeStateMachineRuleRejected(object?[] args)
    {
        RequireArity("state-machine-rule-rejected", args, 4);
        return NativeMethods.StateMachineRuleRejected(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Convert.ToInt64(args[3], CultureInfo.InvariantCulture));
    }

    private static object? InvokeStateMachineShouldCheckInvariant(object?[] args)
    {
        RequireArity("state-machine-should-check-invariant", args, 5);
        return NativeMethods.StateMachineShouldCheckInvariant(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Convert.ToInt64(args[3], CultureInfo.InvariantCulture), ToIntPtr(args[4]));
    }

    private static object? InvokeStopSpan(object?[] args)
    {
        RequireArity("stop-span", args, 3);
        return NativeMethods.StopSpan(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeStringGeneratorDomain(object?[] args)
    {
        RequireArity("string-generator-domain", args, 3);
        return NativeMethods.StringGeneratorDomain(ToIntPtr(args[0]), ToUInt64(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeStringGeneratorEmail(object?[] args)
    {
        RequireArity("string-generator-email", args, 2);
        return NativeMethods.StringGeneratorEmail(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeStringGeneratorFree(object?[] args)
    {
        RequireArity("string-generator-free", args, 2);
        return NativeMethods.StringGeneratorFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeStringGeneratorRegex(object?[] args)
    {
        RequireArity("string-generator-regex", args, 5);
        return NativeMethods.StringGeneratorRegex(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeStringGeneratorText(object?[] args)
    {
        RequireArity("string-generator-text", args, 15);
        return NativeMethods.StringGeneratorText(ToIntPtr(args[0]), ToUInt64(args[1]), ToUInt64(args[2]), ToIntPtr(args[3]), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), Convert.ToUInt32(args[5], CultureInfo.InvariantCulture), ToIntPtr(args[6]), ToNUInt(args[7]), ToIntPtr(args[8]), ToNUInt(args[9]), ToIntPtr(args[10]), ToNUInt(args[11]), ToIntPtr(args[12]), ToNUInt(args[13]), ToIntPtr(args[14]));
    }

    private static object? InvokeStringGeneratorUrl(object?[] args)
    {
        RequireArity("string-generator-url", args, 2);
        return NativeMethods.StringGeneratorUrl(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeTarget(object?[] args)
    {
        RequireArity("target", args, 4);
        return NativeMethods.Target(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToDouble(args[2], CultureInfo.InvariantCulture), ToIntPtr(args[3]));
    }

    private static object? InvokeTestCaseClone(object?[] args)
    {
        RequireArity("test-case-clone", args, 3);
        return NativeMethods.TestCaseClone(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeTestCaseFree(object?[] args)
    {
        RequireArity("test-case-free", args, 2);
        return NativeMethods.TestCaseFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeTestCaseFromBlob(object?[] args)
    {
        RequireArity("test-case-from-blob", args, 6);
        return NativeMethods.TestCaseFromBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeTestCaseIsNondeterministic(object?[] args)
    {
        RequireArity("test-case-is-nondeterministic", args, 3);
        return NativeMethods.TestCaseIsNondeterministic(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeTestCasePrinter(object?[] args)
    {
        RequireArity("test-case-printer", args, 4);
        return NativeMethods.TestCasePrinter(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeVersion(object?[] args)
    {
        RequireArity("version", args, 2);
        return NativeMethods.Version(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }
}
