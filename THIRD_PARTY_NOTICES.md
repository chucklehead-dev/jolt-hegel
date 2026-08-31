# Third-party notices

`jolt-hegel` does not bundle Jolt, Babashka, a JDK, or libhegel in its source
distribution. The Clojure-family host is user-provided. The native installer
downloads libhegel directly from Hegel's official GitHub release and stores it
in a local cache.

## babashka.ffi

Source: <https://github.com/babashka/ffi>

MIT License, Copyright (c) 2026 Michiel Borkent.

JVM Clojure resolves this library as a source dependency. Babashka embeds the
same namespace in its runtime. See the upstream repository for the complete
license text.

## hegel-clj

Source: <https://github.com/aphyr/hegel-clj>

Copyright © 2026 Kyle Kingsbury

The original jolt-hegel design and implementation were based in part on Kyle
Kingsbury (Aphyr)'s hegel-clj. Both projects are licensed under the Eclipse
Public License 2.0.
hegel-clj is not bundled as a runtime dependency; see its source repository for
its license text and copyright notices.

## libhegel 0.33.3

Source: <https://github.com/hegeldev/hegel-rust>

MIT License

Copyright (c) 2026 Antithesis, LLC

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Jolt 0.7.23

Source: <https://github.com/jolt-lang/jolt>

Jolt is licensed under the Eclipse Public License 2.0. It is not redistributed
by `jolt-hegel`; the runtime is installed separately by the user or CI. See
Jolt's source repository for its copyright notices and license text.
