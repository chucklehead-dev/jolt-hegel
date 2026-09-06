using Hegel.Native;

// The workflow consumes this path as an assertion only. ClojureCLR itself
// must discover the same DLL through its package-copied load-path root, with
// HEGEL_CLR_BRIDGE_ASSEMBLY deliberately unset.
Console.WriteLine(typeof(Bridge).Assembly.Location);
