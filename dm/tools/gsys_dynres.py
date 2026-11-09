#!/usr/bin/env python3
"""Sets the scene resolution of a gsys game (Nintendo EPD's engine) through its own dynamic resolution palette: writes one ResolutionReduceScale into every level
of DynamicResolutionParam's palettes in the packed ModelSceneExtensionParam file, and writes the pack as a RomFS overlay file (DM_ROMFS_OVERLAY, dm/host/hle_fs.c).

The game draws its scene at its upscaler's output size times the palette level's scale (Zelda: Ocarina of Time, 0400486024DEC000: 2048x1152 x 0.75 = 1536x864 at
level 0), so one scale on every level fixes the scene size: 0.625 gives 1280x720. The values are found by key name, not by offset (the SARC file whose name holds
--file, then the BYML's DynamicResolutionParam -> ... -> ResolutionReduceScale floats; nodes several palettes share are written once), written in place (the file
and the pack keep their sizes, so the game's ResourceSizeTable stays right), and the pack is compressed again with the RomFS's dictionary (Pack/ZsDic.pack.zs,
pack.zsdic), with the original frame's dictionary ID, content size and checksum flags. The new frame is decompressed and compared: only the edited words differ.

  gsys_dynres.py --romfs '<game>.nup:update/131072/romfs' --scale 0.625 --out '<NSP folder>/<id>-work/romfs_overlay_720p'
  gsys_dynres.py --romfs ... --floor 0.5 [--threshold 190] --out '<NSP folder>/<id>-work/romfs_overlay_dynfloor'   (the game keeps choosing; no level below 0.5)
      [--expect 0.75,0.71112,...] [--threshold <LoadThreshold>] [--pack Pack/Bootup.Nin_Ounce_NVN2.pack.zs] [--zsdic Pack/ZsDic.pack.zs] [--file ModelSceneExtensionParam]

--romfs is a RomFS folder, a RomFS image or a NUP package (romfs.py). --expect: the original scales (any order, 1e-4); the tool stops when the file holds others.
--threshold also writes every palette's LoadThreshold (the GPU load at which the game lowers the level: in Zelda: Ocarina of Time's main e0f3ccfa the load is the GPU
time between the frame's two timestamp reports in percent of 16.667 ms (main+0x5a8f0: ns x 0.001 x 100 / 16666.67), so 95 is 15.8 ms and 190 is 31.7 ms). Exit 1 on any error. Needs zstandard (or Python 3.14's
compression.zstd). The output goes to the game's work folder, never into this repository."""
import argparse
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import romfs  # noqa: E402

ZSTD_MAGIC = b'\x28\xb5\x2f\xfd'


class Error(Exception):
    pass


# ---------- zstd (zstandard, or Python 3.14's compression.zstd) ----------
def _zstandard():
    try:
        import zstandard   # pyright: ignore[reportMissingImports]  # optional: Python 3.14's compression.zstd serves without it
        return zstandard
    except ImportError:
        return None


def frame_info(data):
    """(content size or None, dictionary ID, checksum flag) of a zstd frame's header"""
    if data[:4] != ZSTD_MAGIC:
        raise Error('not a zstd frame')
    fhd = data[4]
    fcs_flag, single, checksum, did_flag = fhd >> 6, fhd >> 5 & 1, fhd >> 2 & 1, fhd & 3
    o = 5 + (0 if single else 1)
    did_size = (0, 1, 2, 4)[did_flag]
    did = int.from_bytes(data[o:o + did_size], 'little') if did_size else 0
    o += did_size
    fcs_size = (1 if single else 0, 2, 4, 8)[fcs_flag]
    fcs = int.from_bytes(data[o:o + fcs_size], 'little') + (256 if fcs_size == 2 else 0) if fcs_size else None   # (the two-byte field holds the size less 256)
    return fcs, did, bool(checksum)


def decompress(data, dictionary):
    z = _zstandard()
    if z:
        d = z.ZstdDecompressor(dict_data=z.ZstdCompressionDict(dictionary)) if dictionary else z.ZstdDecompressor()
        return d.decompressobj().decompress(data)
    from compression import zstd  # Python 3.14
    return zstd.decompress(data, zstd_dict=zstd.ZstdDict(dictionary) if dictionary else None)


def compress(data, dictionary, level, checksum, dict_id=True):
    z = _zstandard()
    if z:
        c = z.ZstdCompressor(level=level, dict_data=z.ZstdCompressionDict(dictionary), write_checksum=checksum, write_content_size=True, write_dict_id=dict_id)
        return c.compress(data)
    from compression import zstd
    P = zstd.CompressionParameter
    return zstd.compress(data, options={P.compression_level: level, P.checksum_flag: int(checksum), P.content_size_flag: 1, P.dict_id_flag: int(dict_id)},
                         zstd_dict=zstd.ZstdDict(dictionary))


# ---------- SARC ----------
def sarc_files(b):
    """{name: (offset, size)} of a SARC archive's files"""
    if b[:4] != b'SARC':
        raise Error('not a SARC archive')
    e = '<' if b[6:8] == b'\xff\xfe' else '>'
    hl, _bom, _size, doff = struct.unpack_from(e + 'HHII', b, 4)
    if b[hl:hl + 4] != b'SFAT':
        raise Error('SARC without SFAT')
    _, n, _mult = struct.unpack_from(e + 'HHI', b, hl + 4)
    fnt = hl + 12 + 16 * n
    if b[fnt:fnt + 4] != b'SFNT':
        raise Error('SARC without SFNT')
    names = fnt + 8
    out = {}
    for i in range(n):
        h, attr, s0, e0 = struct.unpack_from(e + 'IIII', b, hl + 12 + 16 * i)
        if attr >> 24:
            o = names + (attr & 0xffff) * 4
            name = b[o:b.index(b'\0', o)].decode('utf-8', 'replace')
        else:
            name = '%08x' % h
        out[name] = (doff + s0, e0 - s0)
    return out


def zsdic(raw):
    """The pack dictionary from ZsDic.pack.zs (a zstd SARC), a SARC of dictionaries, or a dictionary file"""
    if raw[:4] == ZSTD_MAGIC:
        raw = decompress(raw, None)
    if raw[:4] == b'SARC':
        files = sarc_files(raw)
        hit = [k for k in files if k.endswith('pack.zsdic')]
        if not hit:
            raise Error('the dictionary archive has no pack.zsdic (has: %s)' % ', '.join(sorted(files)))
        o, n = files[hit[0]]
        return raw[o:o + n]
    return raw


# ---------- BYML ----------
D_FLOAT, D_INT = 0xD2, 0xD1
CONTAINERS = (0xC0, 0xC1, 0x20, 0x21)


class Byml:
    def __init__(self, b, base):
        """b: the whole buffer; base: the BYML file's offset in it (offsets in the file are relative to it)"""
        self.b, self.base = b, base
        magic = bytes(b[base:base + 2])
        if magic not in (b'YB', b'BY'):
            raise Error('not a BYML file')
        self.e = '<' if magic == b'YB' else '>'
        _ver, kt, _st, self.root = struct.unpack_from(self.e + 'HIII', b, base + 2)
        self.keys = self.strtab(kt) if kt else []

    def u24(self, o):
        x = self.b[o:o + 3]
        return x[0] | x[1] << 8 | x[2] << 16 if self.e == '<' else x[0] << 16 | x[1] << 8 | x[2]

    def u32(self, o):
        return struct.unpack_from(self.e + 'I', self.b, o)[0]

    def strtab(self, o):
        a = self.base + o
        if self.b[a] != 0xC2:
            raise Error('BYML key table is not a string table')
        n = self.u24(a + 1)
        offs = struct.unpack_from(self.e + '%dI' % (n + 1), self.b, a + 4)
        return [bytes(self.b[a + offs[i]:self.b.index(b'\0', a + offs[i])]).decode('utf-8', 'replace') for i in range(n)]

    def items(self, o):
        """[(name, type, value word offset in b)] of the container node at file offset o"""
        a = self.base + o
        t, n = self.b[a], self.u24(a + 1)
        if t == 0xC1:
            return [(self.keys[self.u24(a + 4 + 8 * i)], self.b[a + 4 + 8 * i + 3], a + 4 + 8 * i + 4) for i in range(n)]
        if t == 0xC0:
            vo = a + 4 + ((n + 3) & ~3)
            return [('[%d]' % i, self.b[a + 4 + i], vo + 4 * i) for i in range(n)]
        if t in (0x20, 0x21):   # hash dictionaries: entries of a 32-bit (0x20) or 64-bit (0x21) key hash and the value word, then the types
            sz = 8 if t == 0x20 else 12
            ta = a + 4 + sz * n
            out = []
            for i in range(n):
                e = a + 4 + sz * i
                h = self.u32(e) if t == 0x20 else struct.unpack_from(self.e + 'Q', self.b, e)[0]
                out.append(('#%08x' % h, self.b[ta + i], e + sz - 4))
            return out
        return []

    def find(self, under, key):
        """[(path, type, value word offset)] of every value named key below a node named under; each value word once (shared nodes)"""
        out, seen_nodes, seen_words = [], set(), set()

        def walk(o, path, inside):
            if o in seen_nodes:
                return
            seen_nodes.add(o)
            for name, t, w in self.items(o):
                p = path + ('/' if not name.startswith('[') else '') + name
                if t in CONTAINERS:
                    walk(self.u32(w), p, inside or name == under)
                elif inside and name == key and w not in seen_words:
                    seen_words.add(w)
                    out.append((p, t, w))
        if self.root:
            walk(self.root, '', False)
        return out

    def get(self, t, w):
        return struct.unpack_from(self.e + ('f' if t == D_FLOAT else 'i'), self.b, w)[0]

    def put(self, t, w, v):
        struct.pack_into(self.e + ('f' if t == D_FLOAT else 'i'), self.b, w, v if t == D_FLOAT else int(v))


def edit(raw, file_part, scale, threshold=None, expect=None, log=print, floor=None):
    """The edited pack bytes and the edited value word offsets, from the decompressed pack raw: every level's scale set to scale, or (scale None) each raised to
    floor when it is below it, the levels above it kept"""
    if scale is not None: new = lambda v: scale
    elif floor is not None: new = lambda v: max(v, floor)
    else: raise Error('edit takes a scale or a floor')
    files = sarc_files(raw)
    hits = sorted(k for k in files if file_part in k and k.endswith('.bgyml'))
    if not hits:
        raise Error('no .bgyml file whose name holds %r in the pack' % file_part)
    buf = bytearray(raw)
    words, found = [], []
    for name in hits:
        o, n = files[name]
        by = Byml(buf, o)
        scales = [x for x in by.find('DynamicResolutionParam', 'ResolutionReduceScale') if x[1] == D_FLOAT]
        if not scales:
            log('%s: no DynamicResolutionParam ResolutionReduceScale floats' % name)
            continue
        log('%s (pack+%#x, %d bytes): %d scales' % (name, o, n, len(scales)))
        for path, t, w in scales:
            v = by.get(t, w)
            found.append(v)
            log('  %-78s %.5f -> %.5f  (file+%#x, pack+%#x)' % (path, v, new(v), w - o, w))
        if expect is not None:
            have, want = sorted(found), sorted(expect)
            if len(have) != len(want) or any(abs(a - b) > 1e-4 for a, b in zip(have, want)):
                raise Error('the scales found (%s) are not the expected originals (%s): another build or an edited file' %
                            (', '.join('%.5f' % x for x in have), ', '.join('%.5f' % x for x in want)))
        for path, t, w in scales:
            v = by.get(t, w)
            if new(v) != v:
                by.put(t, w, new(v))
                words.append(w)
        if threshold is not None:
            for path, t, w in by.find('DynamicResolutionParam', 'LoadThreshold'):
                if t in (D_FLOAT, D_INT):
                    log('  %-78s %s -> %s  (pack+%#x)' % (path, by.get(t, w), threshold, w))
                    by.put(t, w, threshold)
                    words.append(w)
    if not words:
        raise Error('nothing to edit: no ResolutionReduceScale under DynamicResolutionParam in %s' % ', '.join(hits))
    return bytes(buf), words


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--romfs', required=True, help='RomFS folder, image, or <file>.nup:update/<version>/romfs')
    ap.add_argument('--pack', default='Pack/Bootup.Nin_Ounce_NVN2.pack.zs', help='the pack, in the RomFS (default %(default)s)')
    ap.add_argument('--zsdic', default='Pack/ZsDic.pack.zs', help='the dictionaries, in the RomFS (default %(default)s)')
    ap.add_argument('--file', default='ModelSceneExtensionParam', help='part of the edited file\'s name in the pack (default %(default)s)')
    ap.add_argument('--scale', type=float, help='ResolutionReduceScale for every level (0.625: a 2048x1152 output draws its scene at 1280x720)')
    ap.add_argument('--floor', type=float, help='instead of --scale: raise the levels below this scale to it and keep the others (the game then never goes below it)')
    ap.add_argument('--threshold', type=float, help='also every palette\'s LoadThreshold')
    ap.add_argument('--expect', help='the original scales, comma-separated (stops when the file holds others)')
    ap.add_argument('--level', type=int, default=19, help='zstd level (default %(default)s)')
    ap.add_argument('--out', required=True, help='overlay root: the pack is written at <out>/<pack>')
    a = ap.parse_args(argv)
    try:
        if (a.scale is None) == (a.floor is None):
            raise Error('give one of --scale and --floor')
        if not 0.05 <= (a.scale if a.scale is not None else a.floor) <= 2.0:
            raise Error('--%s %g is outside 0.05 .. 2' % (('scale', a.scale) if a.scale is not None else ('floor', a.floor)))
        expect = [float(x) for x in a.expect.split(',')] if a.expect else None
        src = a.romfs.rstrip('/\\') + '/' + a.pack
        with romfs.open(src, 'rb') as f:
            packed = f.read()
        with romfs.open(a.romfs.rstrip('/\\') + '/' + a.zsdic, 'rb') as f:
            dictionary = zsdic(f.read())
        size, did, checksum = frame_info(packed)
        raw = decompress(packed, dictionary)
        if size is not None and len(raw) != size:
            raise Error('the pack decompressed to %d bytes, its frame says %d' % (len(raw), size))
        print('%s: %d bytes, %d decompressed (dictionary ID %#x%s)' % (src, len(packed), len(raw), did, ', checksum' if checksum else ''))
        new, words = edit(raw, a.file, a.scale, a.threshold, expect, floor=a.floor)
        out = compress(new, dictionary, a.level, checksum, did != 0)
        size2, did2, _ = frame_info(out)
        back = decompress(out, dictionary)
        if back != new or size2 != len(new) or did2 != did:
            raise Error('the new frame does not decompress to the edited pack (size %s, dictionary ID %#x)' % (size2, did2))
        diff = [i for i in range(len(raw)) if raw[i] != back[i]]
        allowed = {w + k for w in words for k in range(4)}
        if len(back) != len(raw) or any(i not in allowed for i in diff):
            raise Error('bytes outside the edited words changed')
        dst = os.path.join(a.out, *a.pack.split('/'))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'wb') as f:
            f.write(out)
        print('wrote %s: %d bytes (%d words edited, %d bytes differ, size kept at %d decompressed)' % (dst, len(out), len(words), len(diff), len(back)))
        return 0
    except (Error, OSError, ValueError, struct.error) as e:
        print('gsys_dynres: %s' % e, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
