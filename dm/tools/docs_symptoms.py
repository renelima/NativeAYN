#!/usr/bin/env python3
# Writes docs/agents/symptoms.md, the symptom index, from the subsystem references in docs/reference: one line per entry that has a "- **Symptom:**" or "- **Sintoma:**" bullet (the text of that
# bullet, links reduced to their text) with a link to the entry's heading, grouped by reference file in the order docs/reference/README.md lists them. Run it after adding or
# changing an entry; --check only reports whether the index is current (dm/tools/tests/test_docs.py runs it).
# Usage: docs_symptoms.py [--check]
import re, sys, pathlib, unicodedata

REPO = pathlib.Path(__file__).resolve().parents[2]
REF = REPO / 'docs' / 'reference'
OUT = REPO / 'docs' / 'agents' / 'symptoms.md'

def slug(h):
    # GitHub's heading anchor: lower case, punctuation dropped except - and _, spaces to -;
    # Latin letters with diacritics fold to their base (GitHub strips the accents: "Verificação" -> "verificacao").
    # Only NFD (not NFKD): µ and friends must not decompose.
    h = re.sub(r'<[^>]+>', '', h).strip().lower()
    h = unicodedata.normalize('NFD', h)
    h = ''.join(c for c in h if not unicodedata.combining(c))
    h = ''.join(c for c in h if c in ' -_' or unicodedata.category(c)[0] in 'LN')
    return h.replace(' ', '-')

def ref_files():
    # the reference files in the order of the table in docs/reference/README.md
    return [REF / m for m in re.findall(r'^\| \[[^\]]+\]\(([a-z-]+\.md)\)', (REF / 'README.md').read_text(), re.M)]

def entries(path):
    # (heading, anchor, symptom) of each ### entry with a Symptom bullet; anchors numbered as GitHub numbers repeated headings
    seen, head, fence, out = {}, None, False, []
    for line in path.read_text().split('\n'):
        if line.startswith('```'): fence = not fence
        if fence: continue
        m = re.match(r'(#{1,6}) (.*?)\s*#*$', line)
        if m:
            s = slug(m.group(2)); n = seen.get(s, 0); seen[s] = n + 1
            head = (m.group(2), s if n == 0 else f'{s}-{n}') if len(m.group(1)) == 3 else None
            continue
        m = re.match(r'- \*\*(?:Symptom|Sintoma):\*\* (.*)', line)
        if m and head:
            out.append((head[0], head[1], re.sub(r'\[([^\]]+)\]\([^)]*\)', r'\1', m.group(1).strip())))
    return out

def index():
    lines = ['# Índice de sintomas', '',
             'Uma linha por sintoma nas [referências por subsistema](../reference/): o que se vê (uma assertion, uma linha `[RED]`, um abort ou local de crash, um resultado '
             'visível) e a entrada que o explica. Procure primeiro nesta página pelo texto que está no seu log (`grep -i` o nome da função, o texto `[RED]` ou a forma do '
             'endereço), depois leia a entrada e as notas de jogo que ela nomeia. Gerado por `dm/tools/docs_symptoms.py` a partir dos bullets Symptom/Sintoma das entradas: '
             'mude a entrada, então rode de novo.', '']
    for p in ref_files():
        e = entries(p)
        if not e: continue
        title = re.match(r'# (.*)', p.read_text()).group(1)
        lines += [f'## {title}', ''] + [f'- {sym} → [{h}](../reference/{p.name}#{a})' for h, a, sym in e] + ['']
    return '\n'.join(lines).rstrip() + '\n'

if __name__ == '__main__':
    text = index()
    if '--check' in sys.argv[1:]:
        if OUT.read_text() != text: sys.exit(f'{OUT.relative_to(REPO)} is out of date: run dm/tools/docs_symptoms.py')
        print(f'{OUT.relative_to(REPO)} is current')
    else:
        OUT.write_text(text)
        print(f'{OUT.relative_to(REPO)}: {text.count(" → [")} symptoms')
