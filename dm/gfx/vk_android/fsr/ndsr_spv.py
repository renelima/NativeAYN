#!/usr/bin/env python3
"""Compiles ndsr_easu.comp (FSR 1 EASU in place of agl's NDSR upscaler network, vka_ndsr.c) to SPIR-V and writes it as a word array into ../vka_ndsr_spv.h, which
vka_ndsr.c includes, so the build compiles no GLSL: two variants, the float store (PACKED=0) and the 32-bit word store into an R32_UINT view (PACKED=1).
Run it after changing the shader. Needs glslc and spirv-val: on PATH, or the NDK's shader-tools
(ANDROID_NDK_HOME, or ~/android-ndk-*). --check: exit 1 when the header differs from what the shader compiles to (dm/tools/tests/test_ndsr_standin.py)."""
import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, '..', '..', '..', '..'))
SRC = os.path.join(HERE, 'ndsr_easu.comp')
OUT = os.path.join(HERE, '..', 'vka_ndsr_spv.h')
FSR1 = os.path.join(REPO, 'dm', 'third_party', 'fidelityfx-fsr1')
CMD = 'glslc -O --target-env=vulkan1.1 -I dm/third_party/fidelityfx-fsr1 -DPACKED=<0|1> -mfmt=num dm/gfx/vk_android/fsr/ndsr_easu.comp -o -'
VARIANTS = (('vka_ndsr_easu', 0), ('vka_ndsr_easu_packed', 1))


def tool(name):
    """The path of glslc or spirv-val, or None"""
    p = shutil.which(name)
    if p:
        return p
    roots = [os.environ.get('ANDROID_NDK_HOME', '')] + sorted(glob.glob(os.path.expanduser('~/android-ndk-*')), reverse=True)
    for r in roots:
        for c in glob.glob(os.path.join(r, 'shader-tools', '*', name + ('.exe' if os.name == 'nt' else ''))):
            return c
    return None


def header():
    """The header's text, from the shader as it is now"""
    glslc, val = tool('glslc'), tool('spirv-val')
    if not glslc or not val:
        raise FileNotFoundError('glslc and spirv-val are needed (PATH, ANDROID_NDK_HOME or ~/android-ndk-*)')
    arrays = []
    with tempfile.TemporaryDirectory() as tmp:
        for name, packed in VARIANTS:
            flags = ['-O', '--target-env=vulkan1.1', '-I', FSR1, f'-DPACKED={packed}']
            spv = os.path.join(tmp, name + '.spv')
            subprocess.run([glslc] + flags + [SRC, '-o', spv], check=True)
            subprocess.run([val, '--target-env', 'vulkan1.1', spv], check=True)
            num = subprocess.run([glslc] + flags + ['-mfmt=num', SRC, '-o', '-'], check=True, capture_output=True, text=True).stdout
            lines = [ln.strip() for ln in num.strip().splitlines() if ln.strip()]
            arrays += [f'static const uint32_t {name}[] = {{   // PACKED={packed}'] + lines + ['};']
    return '\n'.join([
        '// SPIR-V of FSR 1 EASU in place of agl\'s NDSR upscaler network (vka_ndsr.c, DM_VK_NDSR=fsr). Made from dm/gfx/vk_android/fsr/ndsr_easu.comp with the NDK\'s',
        '// glslc and AMD\'s headers (dm/third_party/fidelityfx-fsr1) by dm/gfx/vk_android/fsr/ndsr_spv.py, and checked with spirv-val --target-env vulkan1.1:',
        '//   ' + CMD,
        '// vka_ndsr_easu stores floats (PACKED=0), vka_ndsr_easu_packed 32-bit words into an R32_UINT view (PACKED=1). Their bindings 100 / 101 / 102 are',
        '// placeholders (vka_ndsr_words). Do not edit',
        '#pragma once',
        '#include <stdint.h>',
    ] + arrays) + '\n'


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--check', action='store_true', help='exit 1 when ../vka_ndsr_spv.h is stale')
    a = ap.parse_args()
    try:
        text = header()
    except (FileNotFoundError, subprocess.CalledProcessError) as e:
        print(f'ndsr_spv: {e}', file=sys.stderr)
        return 2
    old = open(OUT, newline='').read() if os.path.exists(OUT) else ''
    if a.check:
        if old != text:
            print('ndsr_spv: dm/gfx/vk_android/vka_ndsr_spv.h is stale: run dm/gfx/vk_android/fsr/ndsr_spv.py', file=sys.stderr)
            return 1
        print('ndsr_spv: vka_ndsr_spv.h is current')
        return 0
    with open(OUT, 'w', newline='\n') as f:
        f.write(text)
    print(f'ndsr_spv: wrote {os.path.relpath(OUT, REPO)} ({text.count("0x")} words)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
