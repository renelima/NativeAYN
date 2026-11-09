"""agl's NDSR network replaced by FSR 1 EASU on the Vulkan replay (dm/gfx/vk_android/vka_ndsr.c, fsr/ndsr_easu.comp) and the scene resolution palette edit
(dm/tools/gsys_dynres.py). docs/reference/rendering.md "agl's NDSR upscaler network replaced by FSR 1 EASU on the Vulkan replay".

- The committed SPIR-V header matches the shader (needs glslc and spirv-val: PATH, ANDROID_NDK_HOME or ~/android-ndk-*).
- The match rule and the shader on a real Vulkan driver (dm/gfx/vk_android/selftest/ndsr_lvp.c, made for Mesa's lavapipe; Linux or WSL with gcc and
  libvulkan.so.1): every texel written, the bilinear kernel against a CPU reference, EASU's mean (a missing or doubled square is far off), 1:1, linear input,
  a 1280x720 input, the push bank mapping and the binding patch.
- gsys_dynres.py on a synthetic SARC + BYML pack compressed with a dictionary: the scales under DynamicResolutionParam found by key (shared nodes once, others
  untouched), the expected originals checked, the size kept, the frame's dictionary ID and content size kept.
Each part skips when its tools are missing."""
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / 'dm' / 'tools'))
sys.path.insert(0, str(REPO / 'dm' / 'gfx' / 'vk_android' / 'fsr'))
import ndsr_spv  # noqa: E402

try:
    import zstandard   # pyright: ignore[reportMissingImports]  # optional: GsysDynres skips without it
except ImportError:
    zstandard = None


class Header(unittest.TestCase):
    def test_header_is_current(self):
        if not ndsr_spv.tool('glslc') or not ndsr_spv.tool('spirv-val'):
            self.skipTest('no glslc / spirv-val')
        r = subprocess.run([sys.executable, str(REPO / 'dm/gfx/vk_android/fsr/ndsr_spv.py'), '--check'], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)


class Lavapipe(unittest.TestCase):
    def test_shader_on_vulkan(self):
        cc = shutil.which('gcc') or shutil.which('cc')
        if not sys.platform.startswith('linux') or not cc:
            self.skipTest('needs Linux (or WSL) with gcc')
        with tempfile.TemporaryDirectory() as d:
            exe = os.path.join(d, 'ndsr_lvp')
            vk = REPO / 'dm/gfx/vk_android'
            subprocess.run([cc, '-O1', '-DVKA_NDSR_PURE', '-I', str(REPO / 'dm/third_party/vulkan-headers/include'), '-I', str(vk), str(vk / 'selftest/ndsr_lvp.c'),
                            str(vk / 'vka_ndsr.c'), '-ldl', '-lm', '-o', exe], check=True)
            env = dict(os.environ)
            lvp = [p for p in ('/usr/share/vulkan/icd.d/lvp_icd.json', '/usr/share/vulkan/icd.d/lvp_icd.x86_64.json') if os.path.exists(p)]
            if lvp and 'VK_ICD_FILENAMES' not in env:
                env['VK_ICD_FILENAMES'] = lvp[0]
            r = subprocess.run([exe], capture_output=True, text=True, env=env, timeout=600)
            if r.returncode == 77:
                self.skipTest(r.stdout.strip().splitlines()[-1])
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
            self.assertIn('all checks passed', r.stdout)


# ---------- A synthetic pack ----------
def byml(root):
    """A little-endian BYML (version 7) of nested dicts, lists, floats and ints; a dict key '#<hex>' makes a hash dictionary; the same object twice is one node"""
    seen = set()

    def collect(n):
        if isinstance(n, dict):
            for k, v in n.items():
                if not k.startswith('#'):
                    seen.add(k)
                collect(v)
        elif isinstance(n, list):
            for v in n:
                collect(v)
    collect(root)
    keys = sorted(seen)
    kt = struct.pack('<I', 0xC2 | len(keys) << 8)
    offs, strs = [], b''
    base = 4 + 4 * (len(keys) + 1)
    for k in keys:
        offs.append(base + len(strs))
        strs += k.encode() + b'\0'
    offs.append(base + len(strs))
    kt += struct.pack('<%dI' % len(offs), *offs) + strs
    kt += b'\0' * (-len(kt) % 4)
    out = bytearray(16) + kt
    placed = {}

    def val(v):
        if isinstance(v, float):
            return 0xD2, struct.unpack('<I', struct.pack('<f', v))[0]
        if isinstance(v, int):
            return 0xD1, v & 0xffffffff
        return (0x20 if isinstance(v, dict) and all(k.startswith('#') for k in v) else 0xC1 if isinstance(v, dict) else 0xC0), place(v)

    def place(n):
        if id(n) in placed:
            return placed[id(n)]
        if isinstance(n, dict) and n and all(k.startswith('#') for k in n):
            items = sorted(n.items())
            vals = [val(v) for _, v in items]
            body = struct.pack('<I', 0x20 | len(items) << 8)
            for (k, _), (_, w) in zip(items, vals):
                body += struct.pack('<II', int(k[1:], 16), w)
            body += bytes(t for t, _ in vals)
        elif isinstance(n, dict):
            items = sorted(n.items())
            vals = [val(v) for _, v in items]
            body = struct.pack('<I', 0xC1 | len(items) << 8)
            for (k, _), (t, w) in zip(items, vals):
                body += struct.pack('<I', keys.index(k) | t << 24) + struct.pack('<I', w)
        else:
            vals = [val(v) for v in n]
            body = struct.pack('<I', 0xC0 | len(n) << 8) + bytes(t for t, _ in vals)
            body += b'\0' * (-len(body) % 4) + b''.join(struct.pack('<I', w) for _, w in vals)
        body += b'\0' * (-len(body) % 4)
        o = len(out)
        out.extend(body)
        placed[id(n)] = o
        return o
    root_off = place(root)
    struct.pack_into('<2sHIII', out, 0, b'YB', 7, 16, 0, root_off)
    return bytes(out)


def sarc(files):
    names = b''
    noff = []
    for n in files:
        noff.append(len(names))
        names += n.encode() + b'\0'
        names += b'\0' * (-len(names) % 4)
    hl = 0x14
    sfat = struct.pack('<4sHHI', b'SFAT', 0xC, len(files), 0x65)
    data, ents = b'', b''
    for (n, blob), no in zip(files.items(), noff):
        data += b'\0' * (-len(data) % 8)
        ents += struct.pack('<IIII', 0, 0x01000000 | no // 4, len(data), len(data) + len(blob))
        data += blob
    sfnt = struct.pack('<4sHH', b'SFNT', 8, 0) + names
    doff = hl + len(sfat) + len(ents) + len(sfnt)
    doff += -doff % 8
    head = struct.pack('<4sHHIIHH', b'SARC', hl, 0xFEFF, doff + len(data), doff, 0x100, 0)
    b = head + sfat + ents + sfnt
    return b + b'\0' * (doff - len(b)) + data


SCALES = [0.75, 0.5, 0.33334]
NAME = 'System/GameGraphics/ModelSceneExtensionParam/Default.game__gfx__ModelSceneExtensionParam.bgyml'


def pack_bytes():
    levels = [{'Priority': i, 'ResolutionReduceScale': s} for i, s in enumerate(SCALES)]
    pal = {'DynamicResolutionLevelParamList': levels, 'LoadThreshold': 95.0, 'ReduceCounterMax': 2}
    pal2 = {'DynamicResolutionLevelParamList': levels, 'LoadThreshold': 108.0, 'ReduceCounterMax': 3}   # (the level nodes shared, as in the game)
    root = {'DynamicResolutionParam': {'LevelPaletteDict': {'#2ebeca77': pal, '#90e0a495': pal2}},
            'Other': {'ResolutionReduceScale': 0.9}, 'ResolutionParam': {'#eeb1db9f': {'BaseResolution': {'X': 2560, 'Y': 1440}}}}
    return sarc({'Other/File.bgyml': b'x' * 40, NAME: byml(root)})


@unittest.skipUnless(zstandard, 'no zstandard module')
class GsysDynres(unittest.TestCase):
    def setUp(self):
        import gsys_dynres
        self.g = gsys_dynres
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name) / 'romfs'
        (self.root / 'Pack').mkdir(parents=True)
        assert zstandard   # (the class is skipped without it)
        self.raw = pack_bytes()
        samples = [pack_bytes()[i:i + 512] for i in range(0, len(self.raw) - 512, 16)] * 8
        self.dic = zstandard.train_dictionary(2048, samples, dict_id=4)
        frame = zstandard.ZstdCompressor(dict_data=self.dic, write_dict_id=True, write_content_size=True).compress(self.raw)
        (self.root / 'Pack/Bootup.Nin_Ounce_NVN2.pack.zs').write_bytes(frame)
        dsarc = sarc({'pack.zsdic': self.dic.as_bytes(), 'zs.zsdic': b'\0' * 16})
        (self.root / 'Pack/ZsDic.pack.zs').write_bytes(zstandard.ZstdCompressor().compress(dsarc))
        self.out = Path(self.tmp.name) / 'ov'

    def tearDown(self):
        self.tmp.cleanup()

    def run_tool(self, *extra):
        return self.g.main(['--romfs', str(self.root), '--scale', '0.625', '--out', str(self.out)] + list(extra))

    def test_scales_set_and_rest_kept(self):
        self.assertEqual(self.run_tool('--expect', ','.join(map(str, SCALES))), 0)
        frame = (self.out / 'Pack/Bootup.Nin_Ounce_NVN2.pack.zs').read_bytes()
        size, did, _ = self.g.frame_info(frame)
        self.assertEqual((size, did), (len(self.raw), 4))
        assert zstandard
        new = zstandard.ZstdDecompressor(dict_data=self.dic).decompressobj().decompress(frame)
        self.assertEqual(len(new), len(self.raw))
        files = self.g.sarc_files(new)
        o, _ = files[NAME]
        by = self.g.Byml(new, o)
        got = by.find('DynamicResolutionParam', 'ResolutionReduceScale')
        self.assertEqual(len(got), len(SCALES))   # (shared by both palettes: each once)
        self.assertTrue(all(abs(by.get(t, w) - 0.625) < 1e-7 for _, t, w in got))
        other = by.find('Other', 'ResolutionReduceScale')
        self.assertEqual([round(by.get(t, w), 5) for _, t, w in other], [0.9])
        diff = sum(1 for a, b in zip(self.raw, new) if a != b)
        self.assertLessEqual(diff, 4 * len(SCALES))
        thr = by.find('DynamicResolutionParam', 'LoadThreshold')
        self.assertEqual(sorted(by.get(t, w) for _, t, w in thr), [95.0, 108.0])

    def test_threshold(self):
        self.assertEqual(self.run_tool('--threshold', '1000'), 0)
        assert zstandard
        new = zstandard.ZstdDecompressor(dict_data=self.dic).decompressobj().decompress((self.out / 'Pack/Bootup.Nin_Ounce_NVN2.pack.zs').read_bytes())
        o, _ = self.g.sarc_files(new)[NAME]
        by = self.g.Byml(new, o)
        self.assertEqual([by.get(t, w) for _, t, w in by.find('DynamicResolutionParam', 'LoadThreshold')], [1000.0, 1000.0])

    def test_wrong_originals_stop(self):
        self.assertEqual(self.run_tool('--expect', '0.8,0.5,0.33334'), 1)
        self.assertFalse((self.out / 'Pack/Bootup.Nin_Ounce_NVN2.pack.zs').exists())

    def test_no_such_file_stops(self):
        self.assertEqual(self.run_tool('--file', 'NoSuchParam'), 1)


if __name__ == '__main__':
    unittest.main()
