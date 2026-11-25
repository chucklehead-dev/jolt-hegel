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
public struct HegelDuration
{
    public long Seconds;
    public int Nanoseconds;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelOffset
{
    public int Seconds;
    public byte Known;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelPrinterValueResult
{
    public IntPtr Data;
    public nuint Len;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelSpan
{
    public long Years;
    public long Months;
    public long Weeks;
    public long Days;
    public long Hours;
    public long Minutes;
    public long Seconds;
    public long Milliseconds;
    public long Microseconds;
    public long Nanoseconds;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelString
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
public struct HegelTimestamp
{
    public long Second;
    public uint Nanosecond;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelZoneOracle
{
    public IntPtr OffsetAt;
    public IntPtr NextTransition;
    public IntPtr UserData;
    public int FirstYear;
    public int LastYear;
    public uint After;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelZoneRuleDate
{
    public uint Kind;
    public ushort Day;
    public byte Month;
    public byte Week;
    public byte Weekday;
    public int Time;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelDatetime
{
    public HegelDate Date;
    public HegelTime Time;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelTimezoneInfo
{
    public byte Known;
    public HegelString PrimaryName;
    public byte IsLink;
    public ushort Rank;
    public uint Tags;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelTimezoneOffset
{
    public int OffsetSeconds;
    public byte OffsetKnown;
    public byte IsDst;
    public HegelString Abbreviation;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelTimezoneResolution
{
    public int Kind;
    public int OffsetBefore;
    public int OffsetAfter;
    public int OffsetSeconds;
    public byte OffsetKnown;
    public HegelTimestamp Timestamp;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelZoneType
{
    public int Utoff;
    public byte IsDst;
    public HegelString Abbreviation;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelZoneData
{
    public HegelZoneType Initial;
    public IntPtr Transitions;
    public nuint TransitionCount;
    public uint After;
    public HegelZoneType Standard;
    public byte HasDst;
    public HegelZoneType Dst;
    public HegelZoneRuleDate DstStart;
    public HegelZoneRuleDate DstEnd;
    public int LastYear;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelZoneTransition
{
    public long Instant;
    public HegelZoneType LocalType;
}

[StructLayout(LayoutKind.Sequential)]
public struct HegelZonedDatetime
{
    public HegelDatetime Civil;
    public int OffsetSeconds;
    public byte OffsetKnown;
    public byte Fold;
    public int Kind;
    public int OffsetBefore;
    public int OffsetAfter;
    public HegelTimestamp Timestamp;
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
    internal static partial HegelString ContextLastError(IntPtr arg0);

    [LibraryImport(LibraryName, EntryPoint = "hegel_context_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial IntPtr ContextNew();

    [LibraryImport(LibraryName, EntryPoint = "hegel_context_set_error")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ContextSetError(IntPtr arg0, HegelString arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_date_options_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int DateOptionsFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_date_options_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int DateOptionsNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_date_options_set_months")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int DateOptionsSetMonths(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_date_options_set_weekdays")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int DateOptionsSetWeekdays(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_event")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Event(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_event_value")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int EventValue(IntPtr arg0, IntPtr arg1, double arg2, HegelString arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_caveat")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureCaveat(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_diagnostic")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureDiagnostic(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_origin")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureOrigin(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_output")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureOutput(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_failure_reproduction_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FailureReproductionBlob(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_date")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatDate(IntPtr arg0, HegelDate arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_datetime")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatDatetime(IntPtr arg0, HegelDatetime arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_duration")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatDuration(IntPtr arg0, HegelDuration arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_offset")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatOffset(IntPtr arg0, HegelOffset arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_span")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatSpan(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_time")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatTime(IntPtr arg0, HegelTime arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_timestamp")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatTimestamp(IntPtr arg0, HegelTimestamp arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_format_zoned_datetime")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int FormatZonedDatetime(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3);

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
    internal static partial int GenerateDate(IntPtr arg0, IntPtr arg1, HegelDate arg2, HegelDate arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_datetime")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateDatetime(IntPtr arg0, IntPtr arg1, HegelDatetime arg2, HegelDatetime arg3, uint arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_duration")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateDuration(IntPtr arg0, IntPtr arg1, HegelDuration arg2, HegelDuration arg3, uint arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_float")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateFloat(IntPtr arg0, IntPtr arg1, uint arg2, double arg3, double arg4, byte arg5, byte arg6, byte arg7, byte arg8, double arg9, IntPtr arg10);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_grammar")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateGrammar(IntPtr arg0, IntPtr arg1, IntPtr arg2, ulong arg3, IntPtr arg4);

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

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_offset")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateOffset(IntPtr arg0, IntPtr arg1, int arg2, int arg3, uint arg4, byte arg5, IntPtr arg6);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_span")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateSpan(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_string")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateString(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_time")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateTime(IntPtr arg0, IntPtr arg1, HegelTime arg2, HegelTime arg3, uint arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_timestamp")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateTimestamp(IntPtr arg0, IntPtr arg1, HegelTimestamp arg2, HegelTimestamp arg3, uint arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_timezone")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateTimezone(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_uuid")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateUuid(IntPtr arg0, IntPtr arg1, byte arg2, byte arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_zoned_datetime")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateZonedDatetime(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_generate_zoned_datetime_for_zone")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GenerateZonedDatetimeForZone(IntPtr arg0, IntPtr arg1, IntPtr arg2, HegelString arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_builder_file")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarBuilderFile(IntPtr arg0, IntPtr arg1, nuint arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_builder_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarBuilderFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_builder_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarBuilderNew(IntPtr arg0, HegelString arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_builder_provide")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarBuilderProvide(IntPtr arg0, IntPtr arg1, nuint arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_draw_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarDrawFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_draw_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarDrawNew(IntPtr arg0, IntPtr arg1, ulong arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_draw_next")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarDrawNext(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_draw_provide")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarDrawProvide(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_draw_result")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarDrawResult(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarNew(IntPtr arg0, IntPtr arg1, IntPtr arg2, ulong arg3, uint arg4, IntPtr arg5, IntPtr arg6, nuint arg7, IntPtr arg8);

    [LibraryImport(LibraryName, EntryPoint = "hegel_grammar_placeholder")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int GrammarPlaceholder(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_label_combine")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int LabelCombine(IntPtr arg0, IntPtr arg1, nuint arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_label_from_name")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int LabelFromName(IntPtr arg0, HegelString arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_mark_complete")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int MarkComplete(IntPtr arg0, IntPtr arg1, uint arg2, IntPtr arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_collection")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewCollection(IntPtr arg0, IntPtr arg1, ulong arg2, ulong arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_pool")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewPool(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_recursion")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewRecursion(IntPtr arg0, IntPtr arg1, ulong arg2, ulong arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_repeat")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewRepeat(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_new_state_machine")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NewStateMachine(IntPtr arg0, IntPtr arg1, long arg2, long arg3, long arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_next_test_case")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int NextTestCase(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_note")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Note(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_output_stderr")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial void OutputStderr(IntPtr arg0, IntPtr arg1, nuint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_output_stderr_callback")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial IntPtr OutputStderrCallback();

    [LibraryImport(LibraryName, EntryPoint = "hegel_owned_string_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int OwnedStringFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pause_faults")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PauseFaults(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_add")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolAdd(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_generate")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolGenerate(IntPtr arg0, IntPtr arg1, IntPtr arg2, byte arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_pool_restore")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PoolRestore(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_abort_speculative")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterAbortSpeculative(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_begin_group")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterBeginGroup(IntPtr arg0, IntPtr arg1, ulong arg2, HegelString arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_begin_speculative")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterBeginSpeculative(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_breakable")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterBreakable(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_comment")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterComment(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_commit_speculative")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterCommitSpeculative(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_deferred")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterDeferred(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_end_group")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterEndGroup(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_hard_break")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterHardBreak(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_if_break")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterIfBreak(IntPtr arg0, IntPtr arg1, HegelString arg2);

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

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_reflow")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterReflow(IntPtr arg0, IntPtr arg1, IntPtr arg2, HegelString arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_resolve")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterResolve(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_shift_indent")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterShiftIndent(IntPtr arg0, IntPtr arg1, long arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_printer_text")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int PrinterText(IntPtr arg0, IntPtr arg1, HegelString arg2);

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

    [LibraryImport(LibraryName, EntryPoint = "hegel_reflow_options_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ReflowOptionsFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_reflow_options_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ReflowOptionsNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_repeat_failed")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RepeatFailed(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_repeat_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RepeatFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_repeat_more")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RepeatMore(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResult(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_error")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultError(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_result_error_kind")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunResultErrorKind(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

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

    [LibraryImport(LibraryName, EntryPoint = "hegel_run_start_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int RunStartBlob(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_set_default_profile")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SetDefaultProfile(IntPtr arg0, HegelString arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_set_default_zone_source")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SetDefaultZoneSource(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_set_unicode_version")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SetUnicodeVersion(IntPtr arg0, HegelString arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_backend")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetBackend(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_database")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetDatabase(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_derandomize")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetDerandomize(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_nondeterminism_strictness")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetNondeterminismStrictness(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_phases")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetPhases(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_print_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetPrintBlob(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_report_multiple_failures")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetReportMultipleFailures(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_seed")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetSeed(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_show_statistics")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetShowStatistics(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_stop_on_first_failure")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetStopOnFirstFailure(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_suppress_health_check")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetSuppressHealthCheck(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_test_cases")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetTestCases(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_unbounded_choices")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetUnboundedChoices(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_get_verbosity")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsGetVerbosity(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_new_for_profile")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsNewForProfile(IntPtr arg0, HegelString arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_register_profile")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsRegisterProfile(IntPtr arg0, HegelString arg1, IntPtr arg2);

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

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_from_string")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetFromString(IntPtr arg0, IntPtr arg1, HegelString arg2, HegelString arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_nondeterminism_strictness")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetNondeterminismStrictness(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_phases")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetPhases(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_print_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetPrintBlob(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_report_multiple_failures")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetReportMultipleFailures(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_seed")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetSeed(IntPtr arg0, IntPtr arg1, ulong arg2, byte arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_show_statistics")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetShowStatistics(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_stop_on_first_failure")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetStopOnFirstFailure(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_suppress_health_check")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetSuppressHealthCheck(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_test_cases")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetTestCases(IntPtr arg0, IntPtr arg1, ulong arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_test_location")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetTestLocation(IntPtr arg0, IntPtr arg1, HegelString arg2, uint arg3, HegelString arg4, HegelString arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_unbounded_choices")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetUnboundedChoices(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_settings_set_verbosity")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SettingsSetVerbosity(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_span_spec_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SpanSpecFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_span_spec_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SpanSpecNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_span_spec_set_sign")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SpanSpecSetSign(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_span_spec_set_unit_bounds")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SpanSpecSetUnitBounds(IntPtr arg0, IntPtr arg1, uint arg2, long arg3, long arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_span_spec_validate")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int SpanSpecValidate(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_start_span")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StartSpan(IntPtr arg0, IntPtr arg1, ulong arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_add_invariant")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineAddInvariant(IntPtr arg0, IntPtr arg1, IntPtr arg2, HegelString arg3, byte arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_add_rule")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineAddRule(IntPtr arg0, IntPtr arg1, IntPtr arg2, HegelString arg3, double arg4, IntPtr arg5, nuint arg6);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_next_group")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineNextGroup(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_next_rule")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineNextRule(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_rule_rejected")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineRuleRejected(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_should_check_invariant")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineShouldCheckInvariant(IntPtr arg0, IntPtr arg1, IntPtr arg2, long arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_state_machine_start")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StateMachineStart(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

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
    internal static partial int StringGeneratorRegex(IntPtr arg0, HegelString arg1, byte arg2, IntPtr arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_text")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorText(IntPtr arg0, ulong arg1, ulong arg2, IntPtr arg3, uint arg4, uint arg5, IntPtr arg6, nuint arg7, IntPtr arg8, nuint arg9, HegelString arg10, HegelString arg11, IntPtr arg12);

    [LibraryImport(LibraryName, EntryPoint = "hegel_string_generator_url")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int StringGeneratorUrl(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_target")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Target(IntPtr arg0, IntPtr arg1, double arg2, HegelString arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_block")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseBlock(IntPtr arg0, IntPtr arg1, ulong arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_clone")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseClone(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_from_blob")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseFromBlob(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3, IntPtr arg4, IntPtr arg5);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_printer")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCasePrinter(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_set_worker")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseSetWorker(IntPtr arg0, IntPtr arg1, long arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_wants_detail")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseWantsDetail(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_test_case_wants_output")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TestCaseWantsOutput(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_info")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneInfo(IntPtr arg0, HegelString arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_info_in")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneInfoIn(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_offset_at")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneOffsetAt(IntPtr arg0, HegelString arg1, HegelTimestamp arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_offset_at_in")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneOffsetAtIn(IntPtr arg0, IntPtr arg1, HegelString arg2, HegelTimestamp arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_resolve")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneResolve(IntPtr arg0, HegelString arg1, HegelDatetime arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_resolve_in")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneResolveIn(IntPtr arg0, IntPtr arg1, HegelString arg2, HegelDatetime arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_set_legacy")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecSetLegacy(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_set_links")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecSetLinks(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_set_names")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecSetNames(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_set_opaque")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecSetOpaque(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_set_source")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecSetSource(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_set_unknown")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecSetUnknown(IntPtr arg0, IntPtr arg1, byte arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_timezone_spec_validate")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TimezoneSpecValidate(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_tzdb_version")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int TzdbVersion(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_unpause_faults")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int UnpauseFaults(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_validate_date")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ValidateDate(IntPtr arg0, HegelDate arg1, HegelDate arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_validate_datetime")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ValidateDatetime(IntPtr arg0, HegelDatetime arg1, HegelDatetime arg2, uint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_validate_duration")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ValidateDuration(IntPtr arg0, HegelDuration arg1, HegelDuration arg2, uint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_validate_offset")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ValidateOffset(IntPtr arg0, int arg1, int arg2, uint arg3, byte arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_validate_time")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ValidateTime(IntPtr arg0, HegelTime arg1, HegelTime arg2, uint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_validate_timestamp")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ValidateTimestamp(IntPtr arg0, HegelTimestamp arg1, HegelTimestamp arg2, uint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_version")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int Version(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_add_link")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceAddLink(IntPtr arg0, IntPtr arg1, HegelString arg2, HegelString arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_add_oracle")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceAddOracle(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_add_tzif")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceAddTzif(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3, nuint arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_add_zone")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceAddZone(IntPtr arg0, IntPtr arg1, HegelString arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_bundled")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceBundled(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_declare")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceDeclare(IntPtr arg0, IntPtr arg1, IntPtr arg2, nuint arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_name")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceName(IntPtr arg0, IntPtr arg1, nuint arg2, IntPtr arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_name_count")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceNameCount(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_set_loader")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceSetLoader(IntPtr arg0, IntPtr arg1, IntPtr arg2, IntPtr arg3, IntPtr arg4);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_set_version")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceSetVersion(IntPtr arg0, IntPtr arg1, HegelString arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zone_source_version")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZoneSourceVersion(IntPtr arg0, IntPtr arg1, IntPtr arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_free")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecFree(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_new")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecNew(IntPtr arg0, IntPtr arg1);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_set_ambiguous")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecSetAmbiguous(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_set_civil_bounds")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecSetCivilBounds(IntPtr arg0, IntPtr arg1, HegelDatetime arg2, HegelDatetime arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_set_imaginary")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecSetImaginary(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_set_instant_bounds")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecSetInstantBounds(IntPtr arg0, IntPtr arg1, HegelTimestamp arg2, HegelTimestamp arg3);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_set_precision")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecSetPrecision(IntPtr arg0, IntPtr arg1, uint arg2);

    [LibraryImport(LibraryName, EntryPoint = "hegel_zoned_spec_validate")]
    [UnmanagedCallConv(CallConvs = new[] { typeof(CallConvCdecl) })]
    internal static partial int ZonedSpecValidate(IntPtr arg0, IntPtr arg1);
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
        "hegel_context_set_error",
        "hegel_date_options_free",
        "hegel_date_options_new",
        "hegel_date_options_set_months",
        "hegel_date_options_set_weekdays",
        "hegel_event",
        "hegel_event_value",
        "hegel_failure_caveat",
        "hegel_failure_diagnostic",
        "hegel_failure_free",
        "hegel_failure_origin",
        "hegel_failure_output",
        "hegel_failure_reproduction_blob",
        "hegel_format_date",
        "hegel_format_datetime",
        "hegel_format_duration",
        "hegel_format_offset",
        "hegel_format_span",
        "hegel_format_time",
        "hegel_format_timestamp",
        "hegel_format_zoned_datetime",
        "hegel_generate_boolean",
        "hegel_generate_bytes",
        "hegel_generate_bytes_result_free",
        "hegel_generate_date",
        "hegel_generate_datetime",
        "hegel_generate_duration",
        "hegel_generate_float",
        "hegel_generate_grammar",
        "hegel_generate_integer",
        "hegel_generate_integer_big",
        "hegel_generate_ipv4",
        "hegel_generate_ipv6",
        "hegel_generate_offset",
        "hegel_generate_span",
        "hegel_generate_string",
        "hegel_generate_time",
        "hegel_generate_timestamp",
        "hegel_generate_timezone",
        "hegel_generate_uuid",
        "hegel_generate_zoned_datetime",
        "hegel_generate_zoned_datetime_for_zone",
        "hegel_grammar_builder_file",
        "hegel_grammar_builder_free",
        "hegel_grammar_builder_new",
        "hegel_grammar_builder_provide",
        "hegel_grammar_draw_free",
        "hegel_grammar_draw_new",
        "hegel_grammar_draw_next",
        "hegel_grammar_draw_provide",
        "hegel_grammar_draw_result",
        "hegel_grammar_free",
        "hegel_grammar_new",
        "hegel_grammar_placeholder",
        "hegel_label_combine",
        "hegel_label_from_name",
        "hegel_mark_complete",
        "hegel_new_collection",
        "hegel_new_pool",
        "hegel_new_recursion",
        "hegel_new_repeat",
        "hegel_new_state_machine",
        "hegel_next_test_case",
        "hegel_note",
        "hegel_output_stderr",
        "hegel_output_stderr_callback",
        "hegel_owned_string_free",
        "hegel_pause_faults",
        "hegel_pool_add",
        "hegel_pool_free",
        "hegel_pool_generate",
        "hegel_pool_restore",
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
        "hegel_printer_reflow",
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
        "hegel_reflow_options_free",
        "hegel_reflow_options_new",
        "hegel_repeat_failed",
        "hegel_repeat_free",
        "hegel_repeat_more",
        "hegel_run_free",
        "hegel_run_result",
        "hegel_run_result_error",
        "hegel_run_result_error_kind",
        "hegel_run_result_failure",
        "hegel_run_result_failure_count",
        "hegel_run_result_free",
        "hegel_run_result_status",
        "hegel_run_start",
        "hegel_run_start_blob",
        "hegel_set_default_profile",
        "hegel_set_default_zone_source",
        "hegel_set_unicode_version",
        "hegel_settings_free",
        "hegel_settings_get_backend",
        "hegel_settings_get_database",
        "hegel_settings_get_derandomize",
        "hegel_settings_get_nondeterminism_strictness",
        "hegel_settings_get_phases",
        "hegel_settings_get_print_blob",
        "hegel_settings_get_report_multiple_failures",
        "hegel_settings_get_seed",
        "hegel_settings_get_show_statistics",
        "hegel_settings_get_stop_on_first_failure",
        "hegel_settings_get_suppress_health_check",
        "hegel_settings_get_test_cases",
        "hegel_settings_get_unbounded_choices",
        "hegel_settings_get_verbosity",
        "hegel_settings_new",
        "hegel_settings_new_for_profile",
        "hegel_settings_register_profile",
        "hegel_settings_set_backend",
        "hegel_settings_set_database",
        "hegel_settings_set_database_key",
        "hegel_settings_set_derandomize",
        "hegel_settings_set_from_string",
        "hegel_settings_set_nondeterminism_strictness",
        "hegel_settings_set_phases",
        "hegel_settings_set_print_blob",
        "hegel_settings_set_report_multiple_failures",
        "hegel_settings_set_seed",
        "hegel_settings_set_show_statistics",
        "hegel_settings_set_stop_on_first_failure",
        "hegel_settings_set_suppress_health_check",
        "hegel_settings_set_test_cases",
        "hegel_settings_set_test_location",
        "hegel_settings_set_unbounded_choices",
        "hegel_settings_set_verbosity",
        "hegel_span_spec_free",
        "hegel_span_spec_new",
        "hegel_span_spec_set_sign",
        "hegel_span_spec_set_unit_bounds",
        "hegel_span_spec_validate",
        "hegel_start_span",
        "hegel_state_machine_add_invariant",
        "hegel_state_machine_add_rule",
        "hegel_state_machine_free",
        "hegel_state_machine_next_group",
        "hegel_state_machine_next_rule",
        "hegel_state_machine_rule_rejected",
        "hegel_state_machine_should_check_invariant",
        "hegel_state_machine_start",
        "hegel_stop_span",
        "hegel_string_generator_domain",
        "hegel_string_generator_email",
        "hegel_string_generator_free",
        "hegel_string_generator_regex",
        "hegel_string_generator_text",
        "hegel_string_generator_url",
        "hegel_target",
        "hegel_test_case_block",
        "hegel_test_case_clone",
        "hegel_test_case_free",
        "hegel_test_case_from_blob",
        "hegel_test_case_printer",
        "hegel_test_case_set_worker",
        "hegel_test_case_wants_detail",
        "hegel_test_case_wants_output",
        "hegel_timezone_info",
        "hegel_timezone_info_in",
        "hegel_timezone_offset_at",
        "hegel_timezone_offset_at_in",
        "hegel_timezone_resolve",
        "hegel_timezone_resolve_in",
        "hegel_timezone_spec_free",
        "hegel_timezone_spec_new",
        "hegel_timezone_spec_set_legacy",
        "hegel_timezone_spec_set_links",
        "hegel_timezone_spec_set_names",
        "hegel_timezone_spec_set_opaque",
        "hegel_timezone_spec_set_source",
        "hegel_timezone_spec_set_unknown",
        "hegel_timezone_spec_validate",
        "hegel_tzdb_version",
        "hegel_unpause_faults",
        "hegel_validate_date",
        "hegel_validate_datetime",
        "hegel_validate_duration",
        "hegel_validate_offset",
        "hegel_validate_time",
        "hegel_validate_timestamp",
        "hegel_version",
        "hegel_zone_source_add_link",
        "hegel_zone_source_add_oracle",
        "hegel_zone_source_add_tzif",
        "hegel_zone_source_add_zone",
        "hegel_zone_source_bundled",
        "hegel_zone_source_declare",
        "hegel_zone_source_free",
        "hegel_zone_source_name",
        "hegel_zone_source_name_count",
        "hegel_zone_source_new",
        "hegel_zone_source_set_loader",
        "hegel_zone_source_set_version",
        "hegel_zone_source_version",
        "hegel_zoned_spec_free",
        "hegel_zoned_spec_new",
        "hegel_zoned_spec_set_ambiguous",
        "hegel_zoned_spec_set_civil_bounds",
        "hegel_zoned_spec_set_imaginary",
        "hegel_zoned_spec_set_instant_bounds",
        "hegel_zoned_spec_set_precision",
        "hegel_zoned_spec_validate",
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
            "context-set-error" => InvokeContextSetError(args),
            "date-options-free" => InvokeDateOptionsFree(args),
            "date-options-new" => InvokeDateOptionsNew(args),
            "date-options-set-months" => InvokeDateOptionsSetMonths(args),
            "date-options-set-weekdays" => InvokeDateOptionsSetWeekdays(args),
            "event" => InvokeEvent(args),
            "event-value" => InvokeEventValue(args),
            "failure-caveat" => InvokeFailureCaveat(args),
            "failure-diagnostic" => InvokeFailureDiagnostic(args),
            "failure-free" => InvokeFailureFree(args),
            "failure-origin" => InvokeFailureOrigin(args),
            "failure-output" => InvokeFailureOutput(args),
            "failure-reproduction-blob" => InvokeFailureReproductionBlob(args),
            "format-date" => InvokeFormatDate(args),
            "format-datetime" => InvokeFormatDatetime(args),
            "format-duration" => InvokeFormatDuration(args),
            "format-offset" => InvokeFormatOffset(args),
            "format-span" => InvokeFormatSpan(args),
            "format-time" => InvokeFormatTime(args),
            "format-timestamp" => InvokeFormatTimestamp(args),
            "format-zoned-datetime" => InvokeFormatZonedDatetime(args),
            "generate-boolean" => InvokeGenerateBoolean(args),
            "generate-bytes" => InvokeGenerateBytes(args),
            "generate-bytes-result-free" => InvokeGenerateBytesResultFree(args),
            "generate-date" => InvokeGenerateDate(args),
            "generate-datetime" => InvokeGenerateDatetime(args),
            "generate-duration" => InvokeGenerateDuration(args),
            "generate-float" => InvokeGenerateFloat(args),
            "generate-grammar" => InvokeGenerateGrammar(args),
            "generate-integer" => InvokeGenerateInteger(args),
            "generate-integer-big" => InvokeGenerateIntegerBig(args),
            "generate-ipv4" => InvokeGenerateIpv4(args),
            "generate-ipv6" => InvokeGenerateIpv6(args),
            "generate-offset" => InvokeGenerateOffset(args),
            "generate-span" => InvokeGenerateSpan(args),
            "generate-string" => InvokeGenerateString(args),
            "generate-time" => InvokeGenerateTime(args),
            "generate-timestamp" => InvokeGenerateTimestamp(args),
            "generate-timezone" => InvokeGenerateTimezone(args),
            "generate-uuid" => InvokeGenerateUuid(args),
            "generate-zoned-datetime" => InvokeGenerateZonedDatetime(args),
            "generate-zoned-datetime-for-zone" => InvokeGenerateZonedDatetimeForZone(args),
            "grammar-builder-file" => InvokeGrammarBuilderFile(args),
            "grammar-builder-free" => InvokeGrammarBuilderFree(args),
            "grammar-builder-new" => InvokeGrammarBuilderNew(args),
            "grammar-builder-provide" => InvokeGrammarBuilderProvide(args),
            "grammar-draw-free" => InvokeGrammarDrawFree(args),
            "grammar-draw-new" => InvokeGrammarDrawNew(args),
            "grammar-draw-next" => InvokeGrammarDrawNext(args),
            "grammar-draw-provide" => InvokeGrammarDrawProvide(args),
            "grammar-draw-result" => InvokeGrammarDrawResult(args),
            "grammar-free" => InvokeGrammarFree(args),
            "grammar-new" => InvokeGrammarNew(args),
            "grammar-placeholder" => InvokeGrammarPlaceholder(args),
            "label-combine" => InvokeLabelCombine(args),
            "label-from-name" => InvokeLabelFromName(args),
            "mark-complete" => InvokeMarkComplete(args),
            "new-collection" => InvokeNewCollection(args),
            "new-pool" => InvokeNewPool(args),
            "new-recursion" => InvokeNewRecursion(args),
            "new-repeat" => InvokeNewRepeat(args),
            "new-state-machine" => InvokeNewStateMachine(args),
            "next-test-case" => InvokeNextTestCase(args),
            "note" => InvokeNote(args),
            "output-stderr" => InvokeOutputStderr(args),
            "output-stderr-callback" => InvokeOutputStderrCallback(args),
            "owned-string-free" => InvokeOwnedStringFree(args),
            "pause-faults" => InvokePauseFaults(args),
            "pool-add" => InvokePoolAdd(args),
            "pool-free" => InvokePoolFree(args),
            "pool-generate" => InvokePoolGenerate(args),
            "pool-restore" => InvokePoolRestore(args),
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
            "printer-reflow" => InvokePrinterReflow(args),
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
            "reflow-options-free" => InvokeReflowOptionsFree(args),
            "reflow-options-new" => InvokeReflowOptionsNew(args),
            "repeat-failed" => InvokeRepeatFailed(args),
            "repeat-free" => InvokeRepeatFree(args),
            "repeat-more" => InvokeRepeatMore(args),
            "run-free" => InvokeRunFree(args),
            "run-result" => InvokeRunResult(args),
            "run-result-error" => InvokeRunResultError(args),
            "run-result-error-kind" => InvokeRunResultErrorKind(args),
            "run-result-failure" => InvokeRunResultFailure(args),
            "run-result-failure-count" => InvokeRunResultFailureCount(args),
            "run-result-free" => InvokeRunResultFree(args),
            "run-result-status" => InvokeRunResultStatus(args),
            "run-start" => InvokeRunStart(args),
            "run-start-blob" => InvokeRunStartBlob(args),
            "set-default-profile" => InvokeSetDefaultProfile(args),
            "set-default-zone-source" => InvokeSetDefaultZoneSource(args),
            "set-unicode-version" => InvokeSetUnicodeVersion(args),
            "settings-free" => InvokeSettingsFree(args),
            "settings-get-backend" => InvokeSettingsGetBackend(args),
            "settings-get-database" => InvokeSettingsGetDatabase(args),
            "settings-get-derandomize" => InvokeSettingsGetDerandomize(args),
            "settings-get-nondeterminism-strictness" => InvokeSettingsGetNondeterminismStrictness(args),
            "settings-get-phases" => InvokeSettingsGetPhases(args),
            "settings-get-print-blob" => InvokeSettingsGetPrintBlob(args),
            "settings-get-report-multiple-failures" => InvokeSettingsGetReportMultipleFailures(args),
            "settings-get-seed" => InvokeSettingsGetSeed(args),
            "settings-get-show-statistics" => InvokeSettingsGetShowStatistics(args),
            "settings-get-stop-on-first-failure" => InvokeSettingsGetStopOnFirstFailure(args),
            "settings-get-suppress-health-check" => InvokeSettingsGetSuppressHealthCheck(args),
            "settings-get-test-cases" => InvokeSettingsGetTestCases(args),
            "settings-get-unbounded-choices" => InvokeSettingsGetUnboundedChoices(args),
            "settings-get-verbosity" => InvokeSettingsGetVerbosity(args),
            "settings-new" => InvokeSettingsNew(args),
            "settings-new-for-profile" => InvokeSettingsNewForProfile(args),
            "settings-register-profile" => InvokeSettingsRegisterProfile(args),
            "settings-set-backend" => InvokeSettingsSetBackend(args),
            "settings-set-database" => InvokeSettingsSetDatabase(args),
            "settings-set-database-key" => InvokeSettingsSetDatabaseKey(args),
            "settings-set-derandomize" => InvokeSettingsSetDerandomize(args),
            "settings-set-from-string" => InvokeSettingsSetFromString(args),
            "settings-set-nondeterminism-strictness" => InvokeSettingsSetNondeterminismStrictness(args),
            "settings-set-phases" => InvokeSettingsSetPhases(args),
            "settings-set-print-blob" => InvokeSettingsSetPrintBlob(args),
            "settings-set-report-multiple-failures" => InvokeSettingsSetReportMultipleFailures(args),
            "settings-set-seed" => InvokeSettingsSetSeed(args),
            "settings-set-show-statistics" => InvokeSettingsSetShowStatistics(args),
            "settings-set-stop-on-first-failure" => InvokeSettingsSetStopOnFirstFailure(args),
            "settings-set-suppress-health-check" => InvokeSettingsSetSuppressHealthCheck(args),
            "settings-set-test-cases" => InvokeSettingsSetTestCases(args),
            "settings-set-test-location" => InvokeSettingsSetTestLocation(args),
            "settings-set-unbounded-choices" => InvokeSettingsSetUnboundedChoices(args),
            "settings-set-verbosity" => InvokeSettingsSetVerbosity(args),
            "span-spec-free" => InvokeSpanSpecFree(args),
            "span-spec-new" => InvokeSpanSpecNew(args),
            "span-spec-set-sign" => InvokeSpanSpecSetSign(args),
            "span-spec-set-unit-bounds" => InvokeSpanSpecSetUnitBounds(args),
            "span-spec-validate" => InvokeSpanSpecValidate(args),
            "start-span" => InvokeStartSpan(args),
            "state-machine-add-invariant" => InvokeStateMachineAddInvariant(args),
            "state-machine-add-rule" => InvokeStateMachineAddRule(args),
            "state-machine-free" => InvokeStateMachineFree(args),
            "state-machine-next-group" => InvokeStateMachineNextGroup(args),
            "state-machine-next-rule" => InvokeStateMachineNextRule(args),
            "state-machine-rule-rejected" => InvokeStateMachineRuleRejected(args),
            "state-machine-should-check-invariant" => InvokeStateMachineShouldCheckInvariant(args),
            "state-machine-start" => InvokeStateMachineStart(args),
            "stop-span" => InvokeStopSpan(args),
            "string-generator-domain" => InvokeStringGeneratorDomain(args),
            "string-generator-email" => InvokeStringGeneratorEmail(args),
            "string-generator-free" => InvokeStringGeneratorFree(args),
            "string-generator-regex" => InvokeStringGeneratorRegex(args),
            "string-generator-text" => InvokeStringGeneratorText(args),
            "string-generator-url" => InvokeStringGeneratorUrl(args),
            "target" => InvokeTarget(args),
            "test-case-block" => InvokeTestCaseBlock(args),
            "test-case-clone" => InvokeTestCaseClone(args),
            "test-case-free" => InvokeTestCaseFree(args),
            "test-case-from-blob" => InvokeTestCaseFromBlob(args),
            "test-case-printer" => InvokeTestCasePrinter(args),
            "test-case-set-worker" => InvokeTestCaseSetWorker(args),
            "test-case-wants-detail" => InvokeTestCaseWantsDetail(args),
            "test-case-wants-output" => InvokeTestCaseWantsOutput(args),
            "timezone-info" => InvokeTimezoneInfo(args),
            "timezone-info-in" => InvokeTimezoneInfoIn(args),
            "timezone-offset-at" => InvokeTimezoneOffsetAt(args),
            "timezone-offset-at-in" => InvokeTimezoneOffsetAtIn(args),
            "timezone-resolve" => InvokeTimezoneResolve(args),
            "timezone-resolve-in" => InvokeTimezoneResolveIn(args),
            "timezone-spec-free" => InvokeTimezoneSpecFree(args),
            "timezone-spec-new" => InvokeTimezoneSpecNew(args),
            "timezone-spec-set-legacy" => InvokeTimezoneSpecSetLegacy(args),
            "timezone-spec-set-links" => InvokeTimezoneSpecSetLinks(args),
            "timezone-spec-set-names" => InvokeTimezoneSpecSetNames(args),
            "timezone-spec-set-opaque" => InvokeTimezoneSpecSetOpaque(args),
            "timezone-spec-set-source" => InvokeTimezoneSpecSetSource(args),
            "timezone-spec-set-unknown" => InvokeTimezoneSpecSetUnknown(args),
            "timezone-spec-validate" => InvokeTimezoneSpecValidate(args),
            "tzdb-version" => InvokeTzdbVersion(args),
            "unpause-faults" => InvokeUnpauseFaults(args),
            "validate-date" => InvokeValidateDate(args),
            "validate-datetime" => InvokeValidateDatetime(args),
            "validate-duration" => InvokeValidateDuration(args),
            "validate-offset" => InvokeValidateOffset(args),
            "validate-time" => InvokeValidateTime(args),
            "validate-timestamp" => InvokeValidateTimestamp(args),
            "version" => InvokeVersion(args),
            "zone-source-add-link" => InvokeZoneSourceAddLink(args),
            "zone-source-add-oracle" => InvokeZoneSourceAddOracle(args),
            "zone-source-add-tzif" => InvokeZoneSourceAddTzif(args),
            "zone-source-add-zone" => InvokeZoneSourceAddZone(args),
            "zone-source-bundled" => InvokeZoneSourceBundled(args),
            "zone-source-declare" => InvokeZoneSourceDeclare(args),
            "zone-source-free" => InvokeZoneSourceFree(args),
            "zone-source-name" => InvokeZoneSourceName(args),
            "zone-source-name-count" => InvokeZoneSourceNameCount(args),
            "zone-source-new" => InvokeZoneSourceNew(args),
            "zone-source-set-loader" => InvokeZoneSourceSetLoader(args),
            "zone-source-set-version" => InvokeZoneSourceSetVersion(args),
            "zone-source-version" => InvokeZoneSourceVersion(args),
            "zoned-spec-free" => InvokeZonedSpecFree(args),
            "zoned-spec-new" => InvokeZonedSpecNew(args),
            "zoned-spec-set-ambiguous" => InvokeZonedSpecSetAmbiguous(args),
            "zoned-spec-set-civil-bounds" => InvokeZonedSpecSetCivilBounds(args),
            "zoned-spec-set-imaginary" => InvokeZonedSpecSetImaginary(args),
            "zoned-spec-set-instant-bounds" => InvokeZonedSpecSetInstantBounds(args),
            "zoned-spec-set-precision" => InvokeZonedSpecSetPrecision(args),
            "zoned-spec-validate" => InvokeZonedSpecValidate(args),
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
        RequireArity("context-last-error", args, 2);
        Marshal.StructureToPtr(NativeMethods.ContextLastError(ToIntPtr(args[1])), ToIntPtr(args[0]), false);
        return null;
    }

    private static object? InvokeContextNew(object?[] args)
    {
        RequireArity("context-new", args, 0);
        return NativeMethods.ContextNew();
    }

    private static object? InvokeContextSetError(object?[] args)
    {
        RequireArity("context-set-error", args, 2);
        return NativeMethods.ContextSetError(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])));
    }

    private static object? InvokeDateOptionsFree(object?[] args)
    {
        RequireArity("date-options-free", args, 2);
        return NativeMethods.DateOptionsFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeDateOptionsNew(object?[] args)
    {
        RequireArity("date-options-new", args, 2);
        return NativeMethods.DateOptionsNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeDateOptionsSetMonths(object?[] args)
    {
        RequireArity("date-options-set-months", args, 3);
        return NativeMethods.DateOptionsSetMonths(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeDateOptionsSetWeekdays(object?[] args)
    {
        RequireArity("date-options-set-weekdays", args, 3);
        return NativeMethods.DateOptionsSetWeekdays(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeEvent(object?[] args)
    {
        RequireArity("event", args, 3);
        return NativeMethods.Event(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
    }

    private static object? InvokeEventValue(object?[] args)
    {
        RequireArity("event-value", args, 4);
        return NativeMethods.EventValue(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToDouble(args[2], CultureInfo.InvariantCulture), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])));
    }

    private static object? InvokeFailureCaveat(object?[] args)
    {
        RequireArity("failure-caveat", args, 3);
        return NativeMethods.FailureCaveat(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeFailureDiagnostic(object?[] args)
    {
        RequireArity("failure-diagnostic", args, 3);
        return NativeMethods.FailureDiagnostic(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
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

    private static object? InvokeFailureOutput(object?[] args)
    {
        RequireArity("failure-output", args, 3);
        return NativeMethods.FailureOutput(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeFailureReproductionBlob(object?[] args)
    {
        RequireArity("failure-reproduction-blob", args, 3);
        return NativeMethods.FailureReproductionBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatDate(object?[] args)
    {
        RequireArity("format-date", args, 3);
        return NativeMethods.FormatDate(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatDatetime(object?[] args)
    {
        RequireArity("format-datetime", args, 3);
        return NativeMethods.FormatDatetime(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatDuration(object?[] args)
    {
        RequireArity("format-duration", args, 3);
        return NativeMethods.FormatDuration(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelDuration>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatOffset(object?[] args)
    {
        RequireArity("format-offset", args, 3);
        return NativeMethods.FormatOffset(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelOffset>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatSpan(object?[] args)
    {
        RequireArity("format-span", args, 3);
        return NativeMethods.FormatSpan(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatTime(object?[] args)
    {
        RequireArity("format-time", args, 3);
        return NativeMethods.FormatTime(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatTimestamp(object?[] args)
    {
        RequireArity("format-timestamp", args, 3);
        return NativeMethods.FormatTimestamp(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeFormatZonedDatetime(object?[] args)
    {
        RequireArity("format-zoned-datetime", args, 4);
        return NativeMethods.FormatZonedDatetime(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]));
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
        RequireArity("generate-date", args, 6);
        return NativeMethods.GenerateDate(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[3])), ToIntPtr(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateDatetime(object?[] args)
    {
        RequireArity("generate-datetime", args, 6);
        return NativeMethods.GenerateDatetime(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[3])), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateDuration(object?[] args)
    {
        RequireArity("generate-duration", args, 6);
        return NativeMethods.GenerateDuration(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelDuration>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDuration>(ToIntPtr(args[3])), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateFloat(object?[] args)
    {
        RequireArity("generate-float", args, 11);
        return NativeMethods.GenerateFloat(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture), Convert.ToDouble(args[3], CultureInfo.InvariantCulture), Convert.ToDouble(args[4], CultureInfo.InvariantCulture), ToByte(args[5]), ToByte(args[6]), ToByte(args[7]), ToByte(args[8]), Convert.ToDouble(args[9], CultureInfo.InvariantCulture), ToIntPtr(args[10]));
    }

    private static object? InvokeGenerateGrammar(object?[] args)
    {
        RequireArity("generate-grammar", args, 5);
        return NativeMethods.GenerateGrammar(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToUInt64(args[3]), ToIntPtr(args[4]));
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

    private static object? InvokeGenerateOffset(object?[] args)
    {
        RequireArity("generate-offset", args, 7);
        return NativeMethods.GenerateOffset(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToInt32(args[2], CultureInfo.InvariantCulture), Convert.ToInt32(args[3], CultureInfo.InvariantCulture), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), ToByte(args[5]), ToIntPtr(args[6]));
    }

    private static object? InvokeGenerateSpan(object?[] args)
    {
        RequireArity("generate-span", args, 4);
        return NativeMethods.GenerateSpan(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGenerateString(object?[] args)
    {
        RequireArity("generate-string", args, 4);
        return NativeMethods.GenerateString(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGenerateTime(object?[] args)
    {
        RequireArity("generate-time", args, 6);
        return NativeMethods.GenerateTime(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[3])), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateTimestamp(object?[] args)
    {
        RequireArity("generate-timestamp", args, 6);
        return NativeMethods.GenerateTimestamp(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[3])), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateTimezone(object?[] args)
    {
        RequireArity("generate-timezone", args, 4);
        return NativeMethods.GenerateTimezone(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGenerateUuid(object?[] args)
    {
        RequireArity("generate-uuid", args, 5);
        return NativeMethods.GenerateUuid(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]), ToByte(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeGenerateZonedDatetime(object?[] args)
    {
        RequireArity("generate-zoned-datetime", args, 6);
        return NativeMethods.GenerateZonedDatetime(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeGenerateZonedDatetimeForZone(object?[] args)
    {
        RequireArity("generate-zoned-datetime-for-zone", args, 6);
        return NativeMethods.GenerateZonedDatetimeForZone(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])), ToIntPtr(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeGrammarBuilderFile(object?[] args)
    {
        RequireArity("grammar-builder-file", args, 4);
        return NativeMethods.GrammarBuilderFile(ToIntPtr(args[0]), ToIntPtr(args[1]), ToNUInt(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGrammarBuilderFree(object?[] args)
    {
        RequireArity("grammar-builder-free", args, 2);
        return NativeMethods.GrammarBuilderFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeGrammarBuilderNew(object?[] args)
    {
        RequireArity("grammar-builder-new", args, 3);
        return NativeMethods.GrammarBuilderNew(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeGrammarBuilderProvide(object?[] args)
    {
        RequireArity("grammar-builder-provide", args, 4);
        return NativeMethods.GrammarBuilderProvide(ToIntPtr(args[0]), ToIntPtr(args[1]), ToNUInt(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGrammarDrawFree(object?[] args)
    {
        RequireArity("grammar-draw-free", args, 2);
        return NativeMethods.GrammarDrawFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeGrammarDrawNew(object?[] args)
    {
        RequireArity("grammar-draw-new", args, 4);
        return NativeMethods.GrammarDrawNew(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGrammarDrawNext(object?[] args)
    {
        RequireArity("grammar-draw-next", args, 4);
        return NativeMethods.GrammarDrawNext(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeGrammarDrawProvide(object?[] args)
    {
        RequireArity("grammar-draw-provide", args, 3);
        return NativeMethods.GrammarDrawProvide(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
    }

    private static object? InvokeGrammarDrawResult(object?[] args)
    {
        RequireArity("grammar-draw-result", args, 3);
        return NativeMethods.GrammarDrawResult(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeGrammarFree(object?[] args)
    {
        RequireArity("grammar-free", args, 2);
        return NativeMethods.GrammarFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeGrammarNew(object?[] args)
    {
        RequireArity("grammar-new", args, 9);
        return NativeMethods.GrammarNew(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToUInt64(args[3]), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]), ToIntPtr(args[6]), ToNUInt(args[7]), ToIntPtr(args[8]));
    }

    private static object? InvokeGrammarPlaceholder(object?[] args)
    {
        RequireArity("grammar-placeholder", args, 4);
        return NativeMethods.GrammarPlaceholder(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeLabelCombine(object?[] args)
    {
        RequireArity("label-combine", args, 4);
        return NativeMethods.LabelCombine(ToIntPtr(args[0]), ToIntPtr(args[1]), ToNUInt(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeLabelFromName(object?[] args)
    {
        RequireArity("label-from-name", args, 3);
        return NativeMethods.LabelFromName(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeMarkComplete(object?[] args)
    {
        RequireArity("mark-complete", args, 6);
        return NativeMethods.MarkComplete(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture), ToIntPtr(args[3]), ToIntPtr(args[4]), ToIntPtr(args[5]));
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

    private static object? InvokeNewRepeat(object?[] args)
    {
        RequireArity("new-repeat", args, 3);
        return NativeMethods.NewRepeat(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeNewStateMachine(object?[] args)
    {
        RequireArity("new-state-machine", args, 6);
        return NativeMethods.NewStateMachine(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToInt64(args[2], CultureInfo.InvariantCulture), Convert.ToInt64(args[3], CultureInfo.InvariantCulture), Convert.ToInt64(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]));
    }

    private static object? InvokeNextTestCase(object?[] args)
    {
        RequireArity("next-test-case", args, 3);
        return NativeMethods.NextTestCase(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeNote(object?[] args)
    {
        RequireArity("note", args, 3);
        return NativeMethods.Note(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
    }

    private static object? InvokeOutputStderr(object?[] args)
    {
        RequireArity("output-stderr", args, 3);
        NativeMethods.OutputStderr(ToIntPtr(args[0]), ToIntPtr(args[1]), ToNUInt(args[2]));
        return null;
    }

    private static object? InvokeOutputStderrCallback(object?[] args)
    {
        RequireArity("output-stderr-callback", args, 0);
        return NativeMethods.OutputStderrCallback();
    }

    private static object? InvokeOwnedStringFree(object?[] args)
    {
        RequireArity("owned-string-free", args, 2);
        return NativeMethods.OwnedStringFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePauseFaults(object?[] args)
    {
        RequireArity("pause-faults", args, 2);
        return NativeMethods.PauseFaults(ToIntPtr(args[0]), ToIntPtr(args[1]));
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

    private static object? InvokePoolRestore(object?[] args)
    {
        RequireArity("pool-restore", args, 4);
        return NativeMethods.PoolRestore(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Convert.ToInt64(args[3], CultureInfo.InvariantCulture));
    }

    private static object? InvokePrinterAbortSpeculative(object?[] args)
    {
        RequireArity("printer-abort-speculative", args, 2);
        return NativeMethods.PrinterAbortSpeculative(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterBeginGroup(object?[] args)
    {
        RequireArity("printer-begin-group", args, 4);
        return NativeMethods.PrinterBeginGroup(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])));
    }

    private static object? InvokePrinterBeginSpeculative(object?[] args)
    {
        RequireArity("printer-begin-speculative", args, 2);
        return NativeMethods.PrinterBeginSpeculative(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokePrinterBreakable(object?[] args)
    {
        RequireArity("printer-breakable", args, 3);
        return NativeMethods.PrinterBreakable(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
    }

    private static object? InvokePrinterComment(object?[] args)
    {
        RequireArity("printer-comment", args, 3);
        return NativeMethods.PrinterComment(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
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
        RequireArity("printer-end-group", args, 3);
        return NativeMethods.PrinterEndGroup(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
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
        RequireArity("printer-if-break", args, 3);
        return NativeMethods.PrinterIfBreak(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
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

    private static object? InvokePrinterReflow(object?[] args)
    {
        RequireArity("printer-reflow", args, 4);
        return NativeMethods.PrinterReflow(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])));
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
        RequireArity("printer-text", args, 3);
        return NativeMethods.PrinterText(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
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

    private static object? InvokeReflowOptionsFree(object?[] args)
    {
        RequireArity("reflow-options-free", args, 2);
        return NativeMethods.ReflowOptionsFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeReflowOptionsNew(object?[] args)
    {
        RequireArity("reflow-options-new", args, 2);
        return NativeMethods.ReflowOptionsNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeRepeatFailed(object?[] args)
    {
        RequireArity("repeat-failed", args, 3);
        return NativeMethods.RepeatFailed(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeRepeatFree(object?[] args)
    {
        RequireArity("repeat-free", args, 2);
        return NativeMethods.RepeatFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeRepeatMore(object?[] args)
    {
        RequireArity("repeat-more", args, 4);
        return NativeMethods.RepeatMore(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
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

    private static object? InvokeRunResultErrorKind(object?[] args)
    {
        RequireArity("run-result-error-kind", args, 4);
        return NativeMethods.RunResultErrorKind(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
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

    private static object? InvokeRunStartBlob(object?[] args)
    {
        RequireArity("run-start-blob", args, 6);
        return NativeMethods.RunStartBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]), ToIntPtr(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeSetDefaultProfile(object?[] args)
    {
        RequireArity("set-default-profile", args, 2);
        return NativeMethods.SetDefaultProfile(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])));
    }

    private static object? InvokeSetDefaultZoneSource(object?[] args)
    {
        RequireArity("set-default-zone-source", args, 2);
        return NativeMethods.SetDefaultZoneSource(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSetUnicodeVersion(object?[] args)
    {
        RequireArity("set-unicode-version", args, 2);
        return NativeMethods.SetUnicodeVersion(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])));
    }

    private static object? InvokeSettingsFree(object?[] args)
    {
        RequireArity("settings-free", args, 2);
        return NativeMethods.SettingsFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSettingsGetBackend(object?[] args)
    {
        RequireArity("settings-get-backend", args, 3);
        return NativeMethods.SettingsGetBackend(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetDatabase(object?[] args)
    {
        RequireArity("settings-get-database", args, 3);
        return NativeMethods.SettingsGetDatabase(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetDerandomize(object?[] args)
    {
        RequireArity("settings-get-derandomize", args, 3);
        return NativeMethods.SettingsGetDerandomize(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetNondeterminismStrictness(object?[] args)
    {
        RequireArity("settings-get-nondeterminism-strictness", args, 3);
        return NativeMethods.SettingsGetNondeterminismStrictness(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetPhases(object?[] args)
    {
        RequireArity("settings-get-phases", args, 3);
        return NativeMethods.SettingsGetPhases(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetPrintBlob(object?[] args)
    {
        RequireArity("settings-get-print-blob", args, 3);
        return NativeMethods.SettingsGetPrintBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetReportMultipleFailures(object?[] args)
    {
        RequireArity("settings-get-report-multiple-failures", args, 3);
        return NativeMethods.SettingsGetReportMultipleFailures(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetSeed(object?[] args)
    {
        RequireArity("settings-get-seed", args, 4);
        return NativeMethods.SettingsGetSeed(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeSettingsGetShowStatistics(object?[] args)
    {
        RequireArity("settings-get-show-statistics", args, 3);
        return NativeMethods.SettingsGetShowStatistics(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetStopOnFirstFailure(object?[] args)
    {
        RequireArity("settings-get-stop-on-first-failure", args, 3);
        return NativeMethods.SettingsGetStopOnFirstFailure(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetSuppressHealthCheck(object?[] args)
    {
        RequireArity("settings-get-suppress-health-check", args, 3);
        return NativeMethods.SettingsGetSuppressHealthCheck(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetTestCases(object?[] args)
    {
        RequireArity("settings-get-test-cases", args, 3);
        return NativeMethods.SettingsGetTestCases(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetUnboundedChoices(object?[] args)
    {
        RequireArity("settings-get-unbounded-choices", args, 3);
        return NativeMethods.SettingsGetUnboundedChoices(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsGetVerbosity(object?[] args)
    {
        RequireArity("settings-get-verbosity", args, 3);
        return NativeMethods.SettingsGetVerbosity(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsNew(object?[] args)
    {
        RequireArity("settings-new", args, 2);
        return NativeMethods.SettingsNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSettingsNewForProfile(object?[] args)
    {
        RequireArity("settings-new-for-profile", args, 3);
        return NativeMethods.SettingsNewForProfile(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeSettingsRegisterProfile(object?[] args)
    {
        RequireArity("settings-register-profile", args, 3);
        return NativeMethods.SettingsRegisterProfile(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), ToIntPtr(args[2]));
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

    private static object? InvokeSettingsSetFromString(object?[] args)
    {
        RequireArity("settings-set-from-string", args, 4);
        return NativeMethods.SettingsSetFromString(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])));
    }

    private static object? InvokeSettingsSetNondeterminismStrictness(object?[] args)
    {
        RequireArity("settings-set-nondeterminism-strictness", args, 3);
        return NativeMethods.SettingsSetNondeterminismStrictness(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSettingsSetPhases(object?[] args)
    {
        RequireArity("settings-set-phases", args, 3);
        return NativeMethods.SettingsSetPhases(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSettingsSetPrintBlob(object?[] args)
    {
        RequireArity("settings-set-print-blob", args, 3);
        return NativeMethods.SettingsSetPrintBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
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

    private static object? InvokeSettingsSetStopOnFirstFailure(object?[] args)
    {
        RequireArity("settings-set-stop-on-first-failure", args, 3);
        return NativeMethods.SettingsSetStopOnFirstFailure(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
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

    private static object? InvokeSettingsSetTestLocation(object?[] args)
    {
        RequireArity("settings-set-test-location", args, 6);
        return NativeMethods.SettingsSetTestLocation(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), Convert.ToUInt32(args[3], CultureInfo.InvariantCulture), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[4])), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[5])));
    }

    private static object? InvokeSettingsSetUnboundedChoices(object?[] args)
    {
        RequireArity("settings-set-unbounded-choices", args, 3);
        return NativeMethods.SettingsSetUnboundedChoices(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeSettingsSetVerbosity(object?[] args)
    {
        RequireArity("settings-set-verbosity", args, 3);
        return NativeMethods.SettingsSetVerbosity(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSpanSpecFree(object?[] args)
    {
        RequireArity("span-spec-free", args, 2);
        return NativeMethods.SpanSpecFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSpanSpecNew(object?[] args)
    {
        RequireArity("span-spec-new", args, 2);
        return NativeMethods.SpanSpecNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeSpanSpecSetSign(object?[] args)
    {
        RequireArity("span-spec-set-sign", args, 3);
        return NativeMethods.SpanSpecSetSign(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSpanSpecSetUnitBounds(object?[] args)
    {
        RequireArity("span-spec-set-unit-bounds", args, 5);
        return NativeMethods.SpanSpecSetUnitBounds(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture), Convert.ToInt64(args[3], CultureInfo.InvariantCulture), Convert.ToInt64(args[4], CultureInfo.InvariantCulture));
    }

    private static object? InvokeSpanSpecValidate(object?[] args)
    {
        RequireArity("span-spec-validate", args, 2);
        return NativeMethods.SpanSpecValidate(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeStartSpan(object?[] args)
    {
        RequireArity("start-span", args, 3);
        return NativeMethods.StartSpan(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]));
    }

    private static object? InvokeStateMachineAddInvariant(object?[] args)
    {
        RequireArity("state-machine-add-invariant", args, 5);
        return NativeMethods.StateMachineAddInvariant(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])), ToByte(args[4]));
    }

    private static object? InvokeStateMachineAddRule(object?[] args)
    {
        RequireArity("state-machine-add-rule", args, 7);
        return NativeMethods.StateMachineAddRule(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])), Convert.ToDouble(args[4], CultureInfo.InvariantCulture), ToIntPtr(args[5]), ToNUInt(args[6]));
    }

    private static object? InvokeStateMachineFree(object?[] args)
    {
        RequireArity("state-machine-free", args, 2);
        return NativeMethods.StateMachineFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeStateMachineNextGroup(object?[] args)
    {
        RequireArity("state-machine-next-group", args, 5);
        return NativeMethods.StateMachineNextGroup(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]));
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

    private static object? InvokeStateMachineStart(object?[] args)
    {
        RequireArity("state-machine-start", args, 4);
        return NativeMethods.StateMachineStart(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
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
        return NativeMethods.StringGeneratorRegex(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), ToByte(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeStringGeneratorText(object?[] args)
    {
        RequireArity("string-generator-text", args, 13);
        return NativeMethods.StringGeneratorText(ToIntPtr(args[0]), ToUInt64(args[1]), ToUInt64(args[2]), ToIntPtr(args[3]), Convert.ToUInt32(args[4], CultureInfo.InvariantCulture), Convert.ToUInt32(args[5], CultureInfo.InvariantCulture), ToIntPtr(args[6]), ToNUInt(args[7]), ToIntPtr(args[8]), ToNUInt(args[9]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[10])), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[11])), ToIntPtr(args[12]));
    }

    private static object? InvokeStringGeneratorUrl(object?[] args)
    {
        RequireArity("string-generator-url", args, 2);
        return NativeMethods.StringGeneratorUrl(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeTarget(object?[] args)
    {
        RequireArity("target", args, 4);
        return NativeMethods.Target(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToDouble(args[2], CultureInfo.InvariantCulture), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])));
    }

    private static object? InvokeTestCaseBlock(object?[] args)
    {
        RequireArity("test-case-block", args, 4);
        return NativeMethods.TestCaseBlock(ToIntPtr(args[0]), ToIntPtr(args[1]), ToUInt64(args[2]), ToIntPtr(args[3]));
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
        return NativeMethods.TestCaseFromBlob(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]), ToIntPtr(args[4]), ToIntPtr(args[5]));
    }

    private static object? InvokeTestCasePrinter(object?[] args)
    {
        RequireArity("test-case-printer", args, 4);
        return NativeMethods.TestCasePrinter(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeTestCaseSetWorker(object?[] args)
    {
        RequireArity("test-case-set-worker", args, 3);
        return NativeMethods.TestCaseSetWorker(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToInt64(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeTestCaseWantsDetail(object?[] args)
    {
        RequireArity("test-case-wants-detail", args, 3);
        return NativeMethods.TestCaseWantsDetail(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeTestCaseWantsOutput(object?[] args)
    {
        RequireArity("test-case-wants-output", args, 3);
        return NativeMethods.TestCaseWantsOutput(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeTimezoneInfo(object?[] args)
    {
        RequireArity("timezone-info", args, 3);
        return NativeMethods.TimezoneInfo(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), ToIntPtr(args[2]));
    }

    private static object? InvokeTimezoneInfoIn(object?[] args)
    {
        RequireArity("timezone-info-in", args, 4);
        return NativeMethods.TimezoneInfoIn(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeTimezoneOffsetAt(object?[] args)
    {
        RequireArity("timezone-offset-at", args, 4);
        return NativeMethods.TimezoneOffsetAt(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeTimezoneOffsetAtIn(object?[] args)
    {
        RequireArity("timezone-offset-at-in", args, 5);
        return NativeMethods.TimezoneOffsetAtIn(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[3])), ToIntPtr(args[4]));
    }

    private static object? InvokeTimezoneResolve(object?[] args)
    {
        RequireArity("timezone-resolve", args, 4);
        return NativeMethods.TimezoneResolve(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeTimezoneResolveIn(object?[] args)
    {
        RequireArity("timezone-resolve-in", args, 5);
        return NativeMethods.TimezoneResolveIn(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[3])), ToIntPtr(args[4]));
    }

    private static object? InvokeTimezoneSpecFree(object?[] args)
    {
        RequireArity("timezone-spec-free", args, 2);
        return NativeMethods.TimezoneSpecFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeTimezoneSpecNew(object?[] args)
    {
        RequireArity("timezone-spec-new", args, 2);
        return NativeMethods.TimezoneSpecNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeTimezoneSpecSetLegacy(object?[] args)
    {
        RequireArity("timezone-spec-set-legacy", args, 3);
        return NativeMethods.TimezoneSpecSetLegacy(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeTimezoneSpecSetLinks(object?[] args)
    {
        RequireArity("timezone-spec-set-links", args, 3);
        return NativeMethods.TimezoneSpecSetLinks(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeTimezoneSpecSetNames(object?[] args)
    {
        RequireArity("timezone-spec-set-names", args, 4);
        return NativeMethods.TimezoneSpecSetNames(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokeTimezoneSpecSetOpaque(object?[] args)
    {
        RequireArity("timezone-spec-set-opaque", args, 3);
        return NativeMethods.TimezoneSpecSetOpaque(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeTimezoneSpecSetSource(object?[] args)
    {
        RequireArity("timezone-spec-set-source", args, 3);
        return NativeMethods.TimezoneSpecSetSource(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeTimezoneSpecSetUnknown(object?[] args)
    {
        RequireArity("timezone-spec-set-unknown", args, 3);
        return NativeMethods.TimezoneSpecSetUnknown(ToIntPtr(args[0]), ToIntPtr(args[1]), ToByte(args[2]));
    }

    private static object? InvokeTimezoneSpecValidate(object?[] args)
    {
        RequireArity("timezone-spec-validate", args, 2);
        return NativeMethods.TimezoneSpecValidate(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeTzdbVersion(object?[] args)
    {
        RequireArity("tzdb-version", args, 2);
        return NativeMethods.TzdbVersion(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeUnpauseFaults(object?[] args)
    {
        RequireArity("unpause-faults", args, 2);
        return NativeMethods.UnpauseFaults(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeValidateDate(object?[] args)
    {
        RequireArity("validate-date", args, 4);
        return NativeMethods.ValidateDate(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelDate>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeValidateDatetime(object?[] args)
    {
        RequireArity("validate-datetime", args, 4);
        return NativeMethods.ValidateDatetime(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[2])), Convert.ToUInt32(args[3], CultureInfo.InvariantCulture));
    }

    private static object? InvokeValidateDuration(object?[] args)
    {
        RequireArity("validate-duration", args, 4);
        return NativeMethods.ValidateDuration(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelDuration>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelDuration>(ToIntPtr(args[2])), Convert.ToUInt32(args[3], CultureInfo.InvariantCulture));
    }

    private static object? InvokeValidateOffset(object?[] args)
    {
        RequireArity("validate-offset", args, 5);
        return NativeMethods.ValidateOffset(ToIntPtr(args[0]), Convert.ToInt32(args[1], CultureInfo.InvariantCulture), Convert.ToInt32(args[2], CultureInfo.InvariantCulture), Convert.ToUInt32(args[3], CultureInfo.InvariantCulture), ToByte(args[4]));
    }

    private static object? InvokeValidateTime(object?[] args)
    {
        RequireArity("validate-time", args, 4);
        return NativeMethods.ValidateTime(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelTime>(ToIntPtr(args[2])), Convert.ToUInt32(args[3], CultureInfo.InvariantCulture));
    }

    private static object? InvokeValidateTimestamp(object?[] args)
    {
        RequireArity("validate-timestamp", args, 4);
        return NativeMethods.ValidateTimestamp(ToIntPtr(args[0]), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[1])), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[2])), Convert.ToUInt32(args[3], CultureInfo.InvariantCulture));
    }

    private static object? InvokeVersion(object?[] args)
    {
        RequireArity("version", args, 2);
        return NativeMethods.Version(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeZoneSourceAddLink(object?[] args)
    {
        RequireArity("zone-source-add-link", args, 4);
        return NativeMethods.ZoneSourceAddLink(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[3])));
    }

    private static object? InvokeZoneSourceAddOracle(object?[] args)
    {
        RequireArity("zone-source-add-oracle", args, 4);
        return NativeMethods.ZoneSourceAddOracle(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeZoneSourceAddTzif(object?[] args)
    {
        RequireArity("zone-source-add-tzif", args, 5);
        return NativeMethods.ZoneSourceAddTzif(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]), ToNUInt(args[4]));
    }

    private static object? InvokeZoneSourceAddZone(object?[] args)
    {
        RequireArity("zone-source-add-zone", args, 4);
        return NativeMethods.ZoneSourceAddZone(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])), ToIntPtr(args[3]));
    }

    private static object? InvokeZoneSourceBundled(object?[] args)
    {
        RequireArity("zone-source-bundled", args, 2);
        return NativeMethods.ZoneSourceBundled(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeZoneSourceDeclare(object?[] args)
    {
        RequireArity("zone-source-declare", args, 4);
        return NativeMethods.ZoneSourceDeclare(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToNUInt(args[3]));
    }

    private static object? InvokeZoneSourceFree(object?[] args)
    {
        RequireArity("zone-source-free", args, 2);
        return NativeMethods.ZoneSourceFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeZoneSourceName(object?[] args)
    {
        RequireArity("zone-source-name", args, 4);
        return NativeMethods.ZoneSourceName(ToIntPtr(args[0]), ToIntPtr(args[1]), ToNUInt(args[2]), ToIntPtr(args[3]));
    }

    private static object? InvokeZoneSourceNameCount(object?[] args)
    {
        RequireArity("zone-source-name-count", args, 3);
        return NativeMethods.ZoneSourceNameCount(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeZoneSourceNew(object?[] args)
    {
        RequireArity("zone-source-new", args, 2);
        return NativeMethods.ZoneSourceNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeZoneSourceSetLoader(object?[] args)
    {
        RequireArity("zone-source-set-loader", args, 5);
        return NativeMethods.ZoneSourceSetLoader(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]), ToIntPtr(args[3]), ToIntPtr(args[4]));
    }

    private static object? InvokeZoneSourceSetVersion(object?[] args)
    {
        RequireArity("zone-source-set-version", args, 3);
        return NativeMethods.ZoneSourceSetVersion(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelString>(ToIntPtr(args[2])));
    }

    private static object? InvokeZoneSourceVersion(object?[] args)
    {
        RequireArity("zone-source-version", args, 3);
        return NativeMethods.ZoneSourceVersion(ToIntPtr(args[0]), ToIntPtr(args[1]), ToIntPtr(args[2]));
    }

    private static object? InvokeZonedSpecFree(object?[] args)
    {
        RequireArity("zoned-spec-free", args, 2);
        return NativeMethods.ZonedSpecFree(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeZonedSpecNew(object?[] args)
    {
        RequireArity("zoned-spec-new", args, 2);
        return NativeMethods.ZonedSpecNew(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }

    private static object? InvokeZonedSpecSetAmbiguous(object?[] args)
    {
        RequireArity("zoned-spec-set-ambiguous", args, 3);
        return NativeMethods.ZonedSpecSetAmbiguous(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeZonedSpecSetCivilBounds(object?[] args)
    {
        RequireArity("zoned-spec-set-civil-bounds", args, 4);
        return NativeMethods.ZonedSpecSetCivilBounds(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelDatetime>(ToIntPtr(args[3])));
    }

    private static object? InvokeZonedSpecSetImaginary(object?[] args)
    {
        RequireArity("zoned-spec-set-imaginary", args, 3);
        return NativeMethods.ZonedSpecSetImaginary(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeZonedSpecSetInstantBounds(object?[] args)
    {
        RequireArity("zoned-spec-set-instant-bounds", args, 4);
        return NativeMethods.ZonedSpecSetInstantBounds(ToIntPtr(args[0]), ToIntPtr(args[1]), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[2])), Marshal.PtrToStructure<HegelTimestamp>(ToIntPtr(args[3])));
    }

    private static object? InvokeZonedSpecSetPrecision(object?[] args)
    {
        RequireArity("zoned-spec-set-precision", args, 3);
        return NativeMethods.ZonedSpecSetPrecision(ToIntPtr(args[0]), ToIntPtr(args[1]), Convert.ToUInt32(args[2], CultureInfo.InvariantCulture));
    }

    private static object? InvokeZonedSpecValidate(object?[] args)
    {
        RequireArity("zoned-spec-validate", args, 2);
        return NativeMethods.ZonedSpecValidate(ToIntPtr(args[0]), ToIntPtr(args[1]));
    }
}
