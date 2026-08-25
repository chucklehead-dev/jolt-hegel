using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using Hegel.Native;

internal static class Program
{
    private static int Main(string[] args)
    {
        if (args.Length != 1)
        {
            Console.Error.WriteLine("usage: Hegel.Native.Smoke <absolute-libhegel-path>");
            return 2;
        }

        Bridge.Load(args[0]);
        Require("all descriptor symbols loaded", Bridge.ExpectedSymbols.Count == 71);
        CheckLayouts();
        CheckMemory();
        CheckNativeCalls();
        Console.WriteLine("PASS CLR bridge smoke");
        return 0;
    }

    private static void CheckLayouts()
    {
        Require("date size", Marshal.SizeOf<HegelDate>() == 8);
        Require("date month offset", Marshal.OffsetOf<HegelDate>(nameof(HegelDate.Month)).ToInt32() == 4);
        Require("date day offset", Marshal.OffsetOf<HegelDate>(nameof(HegelDate.Day)).ToInt32() == 5);
        Require("time size", Marshal.SizeOf<HegelTime>() == 8);
        Require("time microsecond offset", Marshal.OffsetOf<HegelTime>(nameof(HegelTime.Microsecond)).ToInt32() == 4);
        Require("datetime size", Marshal.SizeOf<HegelDatetime>() == 16);
        Require("datetime time offset", Marshal.OffsetOf<HegelDatetime>(nameof(HegelDatetime.Time)).ToInt32() == 8);
    }

    private static void CheckMemory()
    {
        var pointer = Bridge.Alloc(32);
        try
        {
            Bridge.WriteValue("uint64", pointer, 0, ulong.MaxValue);
            Require("uint64 round trip", (ulong)Bridge.ReadValue("uint64", pointer) == ulong.MaxValue);

            const string text = "CLR λ\0Hegel";
            var byteCount = Bridge.WriteUtf8(pointer, text);
            Require("length-delimited UTF-8", Bridge.ReadUtf8(pointer, byteCount) == text);
        }
        finally
        {
            Bridge.Free(pointer);
        }
    }

    private static void CheckNativeCalls()
    {
        var context = PointerResult("context-new");
        Require("context allocated", context != IntPtr.Zero);

        var settings = IntPtr.Zero;
        var run = IntPtr.Zero;
        var testCase = IntPtr.Zero;
        try
        {
            settings = OutPointer("settings-new", context);
            Ok("settings-set-test-cases", context, settings, 1UL);

            var versionOut = Bridge.Alloc((ulong)IntPtr.Size);
            try
            {
                Ok("version", context, versionOut);
                var versionPointer = (IntPtr)Bridge.ReadValue("pointer", versionOut);
                Require("libhegel version", Bridge.NativeToString(versionPointer) == "0.33.0");
            }
            finally
            {
                Bridge.Free(versionOut);
            }

            run = OutPointer("run-start", context, settings, IntPtr.Zero, IntPtr.Zero);
            testCase = OutPointer("next-test-case", context, run);

            Ok("start-span", context, testCase, ulong.MaxValue);
            Ok("stop-span", context, testCase, (byte)0);
            CheckPrimitiveDraws(context, testCase);
            CheckAggregateDraws(context, testCase);
        }
        finally
        {
            if (testCase != IntPtr.Zero) Ok("test-case-free", context, testCase);
            if (run != IntPtr.Zero) Ok("run-free", context, run);
            if (settings != IntPtr.Zero) Ok("settings-free", context, settings);
            Ok("context-free", context);
        }
    }

    private static void CheckPrimitiveDraws(IntPtr context, IntPtr testCase)
    {
        var integerOut = Bridge.Alloc(8);
        var floatOut = Bridge.Alloc(8);
        try
        {
            Ok("generate-integer", context, testCase, 17L, 17L, integerOut);
            Require("integer downcall", (long)Bridge.ReadValue("int64", integerOut) == 17L);

            Ok("generate-float", context, testCase, 64U, 1.5, 2.5,
               (byte)0, (byte)0, (byte)0, (byte)0, double.Epsilon, floatOut);
            var value = (double)Bridge.ReadValue("double", floatOut);
            Require("wide mixed floating-point downcall", value >= 1.5 && value <= 2.5);
        }
        finally
        {
            Bridge.Free(floatOut);
            Bridge.Free(integerOut);
        }
    }

    private static void CheckAggregateDraws(IntPtr context, IntPtr testCase)
    {
        var pointers = new List<IntPtr>();
        try
        {
            IntPtr New(int size) { var pointer = Bridge.Alloc((ulong)size); pointers.Add(pointer); return pointer; }

            var minDate = New(8); var maxDate = New(8); var dateOut = New(8);
            var minTime = New(8); var maxTime = New(8); var timeOut = New(8);
            var minDatetime = New(16); var maxDatetime = New(16); var datetimeOut = New(16);

            WriteDate(minDate, 0); WriteDate(maxDate, 0);
            WriteTime(minTime, 0); WriteTime(maxTime, 0);
            WriteDate(minDatetime, 0); WriteDate(maxDatetime, 0);
            WriteTime(minDatetime, 8); WriteTime(maxDatetime, 8);

            Ok("generate-date", context, testCase, minDate, maxDate, dateOut);
            Require("date by value", ReadDate(dateOut, 0));
            Ok("generate-time", context, testCase, minTime, maxTime, timeOut);
            Require("time by value", ReadTime(timeOut, 0));
            Ok("generate-datetime", context, testCase, minDatetime, maxDatetime, datetimeOut);
            Require("nested datetime by value", ReadDate(datetimeOut, 0) && ReadTime(datetimeOut, 8));
        }
        finally
        {
            for (var index = pointers.Count - 1; index >= 0; index--) Bridge.Free(pointers[index]);
        }
    }

    private static void WriteDate(IntPtr pointer, long offset)
    {
        Bridge.WriteValue("int32", pointer, offset, 2024);
        Bridge.WriteValue("uint8", pointer, offset + 4, (byte)2);
        Bridge.WriteValue("uint8", pointer, offset + 5, (byte)29);
    }

    private static void WriteTime(IntPtr pointer, long offset)
    {
        Bridge.WriteValue("uint8", pointer, offset, (byte)12);
        Bridge.WriteValue("uint8", pointer, offset + 1, (byte)34);
        Bridge.WriteValue("uint8", pointer, offset + 2, (byte)56);
        Bridge.WriteValue("uint32", pointer, offset + 4, 789U);
    }

    private static bool ReadDate(IntPtr pointer, long offset)
    {
        return (int)Bridge.ReadValue("int32", pointer, offset) == 2024
            && (byte)Bridge.ReadValue("uint8", pointer, offset + 4) == 2
            && (byte)Bridge.ReadValue("uint8", pointer, offset + 5) == 29;
    }

    private static bool ReadTime(IntPtr pointer, long offset)
    {
        return (byte)Bridge.ReadValue("uint8", pointer, offset) == 12
            && (byte)Bridge.ReadValue("uint8", pointer, offset + 1) == 34
            && (byte)Bridge.ReadValue("uint8", pointer, offset + 2) == 56
            && (uint)Bridge.ReadValue("uint32", pointer, offset + 4) == 789U;
    }

    private static IntPtr PointerResult(string functionId, params object?[] args)
    {
        return (IntPtr)(Bridge.Invoke(functionId, args)
            ?? throw new InvalidOperationException($"{functionId} returned null"));
    }

    private static IntPtr OutPointer(string functionId, params object?[] args)
    {
        var output = Bridge.Alloc((ulong)IntPtr.Size);
        try
        {
            var withOutput = new object?[args.Length + 1];
            Array.Copy(args, withOutput, args.Length);
            withOutput[^1] = output;
            Ok(functionId, withOutput);
            return (IntPtr)Bridge.ReadValue("pointer", output);
        }
        finally
        {
            Bridge.Free(output);
        }
    }

    private static void Ok(string functionId, params object?[] args)
    {
        var result = Convert.ToInt32(Bridge.Invoke(functionId, args));
        Require(functionId, result == 0);
    }

    private static void Require(string description, bool condition)
    {
        if (!condition) throw new InvalidOperationException($"CLR bridge smoke failed: {description}");
        Console.WriteLine($"PASS {description}");
    }
}
