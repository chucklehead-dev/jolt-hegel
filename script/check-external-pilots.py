#!/usr/bin/env python3
"""Run isolated, pinned external packs and replay artifacts in fresh processes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', choices=['all', 'instaparse', 'yyjson'], default='all')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/external-pilot')
    parser.add_argument('--timeout', type=int, default=240)
    args = parser.parse_args()
    jolt = shutil.which('jolt')
    if not jolt:
        parser.error('Jolt 0.8.31 must be on PATH')
    if subprocess.check_output([jolt, '--version'], text=True).strip() != 'jolt v0.8.31':
        parser.error('This pilot is pinned to Jolt 0.8.31')
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain', '--', 'src', 'resources', 'deps.edn'], cwd=ROOT, text=True)
    if dirty:
        parser.error('Commit Hegel source changes before claiming replay provenance')
    sources = sorted([ROOT / 'external/targets.edn', *ROOT.glob('external/packs/**/deps.edn'),
                      *ROOT.glob('external/packs/**/src/**/*.clj'), *ROOT.glob('external/support/**/*.clj')])
    digest = hashlib.sha256()
    for source in sources:
        digest.update(str(source.relative_to(ROOT)).encode())
        digest.update(b'\0')
        digest.update(source.read_bytes())
        digest.update(b'\0')
    revision = digest.hexdigest()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, PILOT_HEGEL_SHA=sha, PILOT_REVISION=revision)
    # Avoid accidentally qualifying a caller's alternative native build.
    env.pop('HEGEL_LIBHEGEL_LIBRARY', None)
    env.pop('HEGEL_CACHE_DIR', None)
    env.pop('HEGEL_PROJECT_ROOT', None)
    records = []
    packs = ['instaparse', 'yyjson'] if args.pack == 'all' else [args.pack]
    try:
        for pack in packs:
            destination = output / pack
            destination.mkdir(exist_ok=True)
            for mode in ['run', 'replay']:
                command = [jolt, '-m', f'pilot.{pack}', mode, str(destination)]
                log = destination / f'{mode}.log'
                started = time.monotonic()
                status = 'timeout'
                try:
                    with log.open('w') as stream:
                        process = subprocess.run(command, cwd=ROOT / 'external/packs' / pack,
                                                 env=env, stdout=stream, stderr=subprocess.STDOUT,
                                                 timeout=args.timeout, check=False)
                    status = process.returncode
                finally:
                    records.append({'pack': pack, 'mode': mode, 'exit': status,
                                    'seconds_including_startup': round(time.monotonic() - started, 3),
                                    'log': str(log)})
                print(log.read_text()[-12000:], end='', flush=True)
                if status != 0:
                    raise SystemExit(f'{pack}/{mode} failed; see {log}')
    finally:
        (output / 'execution.json').write_text(json.dumps(
            {'hegel_sha': sha, 'source_revision': revision, 'processes': records}, indent=2) + '\n')
    print(f'PASS external pilots; evidence: {output}')


if __name__ == '__main__':
    main()
