using System;
using System.Globalization;
using System.IO;
using System.Numerics;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;

namespace Hegel.Native;

public static partial class Bridge
{
    private static readonly object LoadLock = new();
    private static IntPtr libraryHandle;
    private static string? libraryPath;

    static Bridge()
    {
        NativeLibrary.SetDllImportResolver(typeof(Bridge).Assembly, ResolveLibrary);
    }

    public static bool IsLoaded => libraryHandle != IntPtr.Zero;

    public static string? LibraryPath => libraryPath;

    public static void Load(string path)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(path);
        var fullPath = Path.GetFullPath(path);

        lock (LoadLock)
        {
            if (libraryHandle != IntPtr.Zero)
            {
                if (!StringComparer.Ordinal.Equals(libraryPath, fullPath))
                {
                    throw new InvalidOperationException(
                        $"libhegel is already loaded from '{libraryPath}', not '{fullPath}'");
                }
                return;
            }

            var handle = NativeLibrary.Load(fullPath);
            try
            {
                foreach (var symbol in ExpectedSymbols)
                {
                    _ = NativeLibrary.GetExport(handle, symbol);
                }
            }
            catch
            {
                NativeLibrary.Free(handle);
                throw;
            }

            libraryPath = fullPath;
            libraryHandle = handle;
        }
    }

    public static IntPtr FindSymbol(string symbol)
    {
        EnsureLoaded();
        return NativeLibrary.GetExport(libraryHandle, symbol);
    }

    public static unsafe IntPtr Alloc(ulong size)
    {
        return (IntPtr)NativeMemory.AllocZeroed((nuint)Math.Max(1UL, size));
    }

    public static unsafe void Free(IntPtr pointer)
    {
        if (pointer != IntPtr.Zero)
        {
            NativeMemory.Free(pointer.ToPointer());
        }
    }

    public static object ReadValue(string type, IntPtr pointer, long offset = 0)
    {
        var nativeOffset = CheckedOffset(offset);
        switch (type)
        {
            case "bool":
            case "uint8": return Marshal.ReadByte(pointer, nativeOffset);
            case "int8": return unchecked((sbyte)Marshal.ReadByte(pointer, nativeOffset));
            case "int16": return Marshal.ReadInt16(pointer, nativeOffset);
            case "uint16": return unchecked((ushort)Marshal.ReadInt16(pointer, nativeOffset));
            case "int32": return Marshal.ReadInt32(pointer, nativeOffset);
            case "uint32": return unchecked((uint)Marshal.ReadInt32(pointer, nativeOffset));
            case "int64": return Marshal.ReadInt64(pointer, nativeOffset);
            case "uint64":
            case "size": return unchecked((ulong)Marshal.ReadInt64(pointer, nativeOffset));
            case "float": return BitConverter.Int32BitsToSingle(Marshal.ReadInt32(pointer, nativeOffset));
            case "double": return BitConverter.Int64BitsToDouble(Marshal.ReadInt64(pointer, nativeOffset));
            case "pointer":
            case "string": return Marshal.ReadIntPtr(pointer, nativeOffset);
            default: throw new ArgumentOutOfRangeException(nameof(type), type, "Unsupported native value type");
        }
    }

    public static void WriteValue(string type, IntPtr pointer, long offset, object value)
    {
        var nativeOffset = CheckedOffset(offset);
        switch (type)
        {
            case "bool":
            case "uint8": Marshal.WriteByte(pointer, nativeOffset, ToByte(value)); break;
            case "int8": Marshal.WriteByte(pointer, nativeOffset, unchecked((byte)Convert.ToSByte(value, CultureInfo.InvariantCulture))); break;
            case "int16": Marshal.WriteInt16(pointer, nativeOffset, Convert.ToInt16(value, CultureInfo.InvariantCulture)); break;
            case "uint16": Marshal.WriteInt16(pointer, nativeOffset, unchecked((short)Convert.ToUInt16(value, CultureInfo.InvariantCulture))); break;
            case "int32": Marshal.WriteInt32(pointer, nativeOffset, Convert.ToInt32(value, CultureInfo.InvariantCulture)); break;
            case "uint32": Marshal.WriteInt32(pointer, nativeOffset, unchecked((int)Convert.ToUInt32(value, CultureInfo.InvariantCulture))); break;
            case "int64": Marshal.WriteInt64(pointer, nativeOffset, Convert.ToInt64(value, CultureInfo.InvariantCulture)); break;
            case "uint64": Marshal.WriteInt64(pointer, nativeOffset, unchecked((long)ToUInt64(value))); break;
            case "size": Marshal.WriteInt64(pointer, nativeOffset, unchecked((long)ToNUInt(value))); break;
            case "float": Marshal.WriteInt32(pointer, nativeOffset, BitConverter.SingleToInt32Bits(Convert.ToSingle(value, CultureInfo.InvariantCulture))); break;
            case "double": Marshal.WriteInt64(pointer, nativeOffset, BitConverter.DoubleToInt64Bits(Convert.ToDouble(value, CultureInfo.InvariantCulture))); break;
            case "pointer":
            case "string": Marshal.WriteIntPtr(pointer, nativeOffset, ToIntPtr(value)); break;
            default: throw new ArgumentOutOfRangeException(nameof(type), type, "Unsupported native value type");
        }
    }

    public static byte[] ReadBytes(IntPtr pointer, int length)
    {
        ArgumentOutOfRangeException.ThrowIfNegative(length);
        var result = new byte[length];
        if (length > 0) Marshal.Copy(pointer, result, 0, length);
        return result;
    }

    public static void WriteBytes(IntPtr pointer, byte[] value)
    {
        ArgumentNullException.ThrowIfNull(value);
        if (value.Length > 0) Marshal.Copy(value, 0, pointer, value.Length);
    }

    public static string ReadUtf8(IntPtr pointer, int length)
    {
        return Encoding.UTF8.GetString(ReadBytes(pointer, length));
    }

    public static int WriteUtf8(IntPtr pointer, string value)
    {
        ArgumentNullException.ThrowIfNull(value);
        var bytes = Encoding.UTF8.GetBytes(value);
        WriteBytes(pointer, bytes);
        return bytes.Length;
    }

    public static IntPtr StringToNative(string value)
    {
        ArgumentNullException.ThrowIfNull(value);
        var bytes = Encoding.UTF8.GetBytes(value);
        var pointer = Alloc(checked((ulong)bytes.Length + 1));
        WriteBytes(pointer, bytes);
        Marshal.WriteByte(pointer, bytes.Length, 0);
        return pointer;
    }

    public static string? NativeToString(IntPtr pointer)
    {
        return pointer == IntPtr.Zero ? null : Marshal.PtrToStringUTF8(pointer);
    }

    private static IntPtr ResolveLibrary(string libraryName, Assembly assembly, DllImportSearchPath? searchPath)
    {
        return libraryName == NativeMethods.LibraryName ? libraryHandle : IntPtr.Zero;
    }

    private static void EnsureLoaded()
    {
        if (libraryHandle == IntPtr.Zero)
        {
            throw new InvalidOperationException("libhegel bindings are not loaded");
        }
    }

    private static void RequireArity(string functionId, object?[] args, int expected)
    {
        EnsureLoaded();
        if (args.Length != expected)
        {
            throw new ArgumentException(
                $"libhegel function '{functionId}' expects {expected} arguments, got {args.Length}",
                nameof(args));
        }
    }

    private static int CheckedOffset(long offset) => checked((int)offset);

    private static IntPtr ToIntPtr(object? value)
    {
        return value switch
        {
            null => IntPtr.Zero,
            IntPtr pointer => pointer,
            UIntPtr pointer => unchecked((IntPtr)(long)pointer.ToUInt64()),
            _ => new IntPtr(Convert.ToInt64(value, CultureInfo.InvariantCulture)),
        };
    }

    private static byte ToByte(object? value)
    {
        return value is bool flag ? (byte)(flag ? 1 : 0) : Convert.ToByte(value, CultureInfo.InvariantCulture);
    }

    private static ulong ToUInt64(object? value)
    {
        if (value is BigInteger integer) return checked((ulong)integer);
        if (value is ulong result) return result;
        return ulong.Parse(Convert.ToString(value, CultureInfo.InvariantCulture)!, CultureInfo.InvariantCulture);
    }

    private static nuint ToNUInt(object? value)
    {
        return checked((nuint)ToUInt64(value));
    }
}
