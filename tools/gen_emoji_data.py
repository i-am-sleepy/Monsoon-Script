#!/usr/bin/env python3
"""Builds the emoji data asset (D56): app/src/main/assets/emoji/emoji.tsv

Run from the repo root:   python3 -I tools/gen_emoji_data.py [--font path/to/NotoColorEmoji.ttf]

Inputs (all in third_party/emoji-data/, see SOURCES.md there):
  emoji-test.txt                      Unicode emoji list (UTS #51): fully-qualified sequences, names, groups, skin-tone variants
  annotations-{en,bn}.xml             CLDR names ("tts") and search keywords
  annotationsDerived-{en,bn}.xml      CLDR names/keywords derived for flags, skin tones and ZWJ sequences

Output: one line per BASE emoji (skin-tone variants are attached to their base, never lines of their own):
  #emoji<TAB>1<TAB>unicode 17.0                                   (first line: format version)
  <tab><TAB><emoji><TAB><en><TAB><bn><TAB><variants>
    tab       0..7 = Smileys&People, Animals&Nature, Food&Drink, Activities, Travel&Places, Objects, Symbols, Flags (Gboard order)
    emoji     the fully-qualified Unicode string, exactly as emoji-test.txt lists it (ZWJ sequences stay one string)
    en / bn   search text: name words, then '|', then keyword words; all lower-case, normalized like TextNorm.key (precomposed
              Bengali nukta letters), apostrophes removed, words made of letters/marks/digits/joiners separated by single spaces
    variants  skin-tone variants of this emoji (whole strings, file order), separated by a single space; empty if none
If --font is given, every string is shaped with HarfBuzz and dropped when Noto Color Emoji cannot draw it as ONE glyph.
"""
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET

SRC = 'third_party/emoji-data/'
OUT = 'app/src/main/assets/emoji/emoji.tsv'
TONES = set(range(0x1F3FB, 0x1F3FF + 1))
TAB_OF = {
    'Smileys & Emotion': 0, 'People & Body': 0, 'Animals & Nature': 1, 'Food & Drink': 2, 'Activities': 3,
    'Travel & Places': 4, 'Objects': 5, 'Symbols': 6, 'Flags': 7,
}
LINE = re.compile(r'^([0-9A-F ]+?)\s*;\s*(\S+)\s*#\s*(\S+)\s+E(\d+\.\d+)\s+(.*)$')


def parse_emoji_test():
    entries, group, version = [], None, '?'
    for line in open(SRC + 'emoji-test.txt', encoding='utf-8'):
        if line.startswith('# Version:'):
            version = line.split(':', 1)[1].strip()
        if line.startswith('# group:'):
            group = line.split(':', 1)[1].strip()
            continue
        m = LINE.match(line)
        if not m or m.group(2) != 'fully-qualified':
            continue
        cps = [int(c, 16) for c in m.group(1).split()]
        entries.append({'s': ''.join(map(chr, cps)), 'cps': cps, 'name': m.group(5), 'group': group, 'ver': m.group(4)})
    return entries, version


def load_annotations(lang):
    """cp -> (name, keywords list). The main file wins over the derived one."""
    out = {}
    for fname in ('annotations-%s.xml' % lang, 'annotationsDerived-%s.xml' % lang):
        for a in ET.parse(SRC + fname).getroot().iter('annotation'):
            cp = a.get('cp')
            slot = out.setdefault(cp, {})
            if a.get('type') == 'tts':
                slot.setdefault('name', (a.text or '').strip())
            else:
                slot.setdefault('kw', [k.strip() for k in (a.text or '').split('|') if k.strip()])
    return out


def lookup(ann, s):
    for cand in (s, s.replace('️', '')):
        if cand in ann:
            return ann[cand]
    return {}


def is_word_char(c):
    cat = unicodedata.category(c)
    return cat[0] in 'LMN' or c in '‌‍'


def norm_words(text):
    """Same normal form as TextNorm.key + word split used by EmojiSearch (Kotlin): keep both in sync."""
    text = text.replace('’', "'").lower()
    text = text.replace('ড়', 'ড়').replace('ঢ়', 'ঢ়').replace('য়', 'য়')
    text = text.replace("'", '')
    words, cur = [], []
    for c in text:
        if is_word_char(c):
            cur.append(c)
        elif cur:
            words.append(''.join(cur))
            cur = []
    if cur:
        words.append(''.join(cur))
    return words


def search_text(name, keywords):
    seen, name_words, kw_words = set(), [], []
    for w in norm_words(name):
        if w not in seen:
            seen.add(w)
            name_words.append(w)
    for k in keywords:
        for w in norm_words(k):
            if w not in seen:
                seen.add(w)
                kw_words.append(w)
    return ' '.join(name_words) + '|' + ' '.join(kw_words)


TONE_NAME = re.compile(r'^(?:light|medium-light|medium|medium-dark|dark) skin tone$')


def canon(name):
    """Name without its skin-tone phrases: 'person: light skin tone, blond hair' -> 'person,blond hair'."""
    toks = [t.strip() for t in re.split(r'[:,]', name)]
    toks = [t for t in toks if t and not TONE_NAME.match(t)]
    if toks[1:3] == ['person', 'person']:  # mixed-tone "kiss: person, person, light..." belongs to the generic "kiss"
        del toks[1:3]
    return ','.join(toks)


def has_tone(cps):
    return any(c in TONES for c in cps)


def font_checker(path):
    import uharfbuzz as hb
    font = hb.Font(hb.Face(hb.Blob.from_file_path(path)))

    def ok(s):
        buf = hb.Buffer()
        buf.add_str(s)
        buf.guess_segment_properties()
        hb.shape(font, buf, {})
        ids = [i.codepoint for i in buf.glyph_infos]
        return len(ids) == 1 and ids[0] != 0

    return ok


def main():
    font_ok = None
    if '--font' in sys.argv:
        font_ok = font_checker(sys.argv[sys.argv.index('--font') + 1])

    entries, version = parse_emoji_test()
    bases, by_name, orphans = [], {}, []
    for e in entries:
        if not has_tone(e['cps']):
            b = {'s': e['s'], 'name': e['name'], 'group': e['group'], 'variants': []}
            bases.append(b)
            by_name[canon(e['name'])] = b
    # Variants are matched to their base by NAME with the tone phrases removed, not by position: Unicode 17 lists the mixed-tone
    # "people with bunny ears" after the men/women blocks, and "person: light skin tone, blond hair" belongs to "person: blond hair".
    for e in entries:
        if has_tone(e['cps']):
            b = by_name.get(canon(e['name']))
            if b is None or b['group'] != e['group']:
                orphans.append(e)
            else:
                b['variants'].append(e['s'])
    if orphans:
        sys.exit('skin-tone entries without a base: %r' % [(o['name'], o['group']) for o in orphans[:5]])

    en_ann, bn_ann = load_annotations('en'), load_annotations('bn')
    dropped_base, dropped_var, no_en, no_bn = [], 0, 0, 0
    rows = []
    for b in bases:
        if font_ok:
            if not font_ok(b['s']):
                dropped_base.append(b['name'])
                continue
            before = len(b['variants'])
            b['variants'] = [v for v in b['variants'] if font_ok(v)]
            dropped_var += before - len(b['variants'])
        en = lookup(en_ann, b['s'])
        bn = lookup(bn_ann, b['s'])
        if not en:
            no_en += 1
        if not bn:
            no_bn += 1
        en_text = search_text(en.get('name') or b['name'], en.get('kw', []))
        bn_text = search_text(bn.get('name', ''), bn.get('kw', []))
        rows.append((TAB_OF[b['group']], b['s'], en_text, bn_text, ' '.join(b['variants'])))
    rows.sort(key=lambda r: r[0])  # stable: Unicode order inside a tab

    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('#emoji\t1\tunicode %s\n' % version)
        for r in rows:
            f.write('%d\t%s\t%s\t%s\t%s\n' % r)

    nvar = sum(len(r[4].split()) for r in rows if r[4])
    print('unicode %s: %d base emoji, %d variants, %d fully-qualified in the source' % (version, len(rows), nvar, len(entries)))
    print('without CLDR name: en %d, bn %d (they stay searchable by their Unicode name / English words)' % (no_en, no_bn))
    if font_ok:
        print('dropped because the font cannot draw them as one glyph: %d base, %d variants %s' % (len(dropped_base), dropped_var, dropped_base[:10]))
    per_tab = {}
    for r in rows:
        per_tab[r[0]] = per_tab.get(r[0], 0) + 1
    print('per tab:', dict(sorted(per_tab.items())))


if __name__ == '__main__':
    main()
