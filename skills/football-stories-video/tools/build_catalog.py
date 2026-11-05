"""Build stories/catalog.json (every story, fact, quiz, date and club link) from the stories/*.md files.
usage: python3 tools/build_catalog.py   (run from the skill folder)
The control page and Claude both read this catalog; rerun it after editing any story file."""
import json, re, glob, os
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'stories')
CATS = {'01': 'אגדות', '02': 'קאמבקים', '03': 'סינדרלה', '04': 'שערוריות', '05': 'מרגשים',
        '06': 'מוזרים', '07': 'העברות', '08': 'דרבים', '09': 'ישראל', '10': 'חם 2026',
        '11': 'הידעתם', '13': 'נחשו מי'}
KEYS = {'הוק': 'hook', 'הסיפור': 'story', 'שאלה לתגובות': 'q', '🎬 צילומים': 'footage',
        'רמזים': 'clues', 'תשובה': 'answer'}
CALM = {'05'}                     # whole category gets the calm tone
TWO_PART = {'02-1', '02-10', '03-1', '03-11', '05-6', '08-1', '09-3', '10-7'}
items = []
for f in sorted(glob.glob(os.path.join(D, '[01][0-9]-*.md'))):
    k = os.path.basename(f)[:2]
    if k not in CATS:
        continue
    text = open(f, encoding='utf-8').read()
    if k == '11':
        sec = ''
        for line in text.splitlines():
            if line.startswith('## '):
                sec = line[3:].strip()
            m = re.match(r'^(\d+)\. (.+)$', line)
            if m:
                items.append({'id': f'11-{m[1]}', 'cat': k, 'title': sec, 'hook': m[2].strip()})
        continue
    for block in re.split(r'^### ', text, flags=re.M)[1:]:
        lines = block.strip().splitlines()
        n, title = lines[0].split('. ', 1)
        it = {'id': f'{k}-{n}', 'cat': k, 'title': title.strip()}
        warn = []
        for line in lines[1:]:
            m = re.match(r'^\*\*(.+?):\*\*\s*(.*)$', line)
            if not m:
                continue
            key, val = m[1].strip(), m[2].strip().strip('"')
            if key.startswith('⚠️'):
                warn.append(val)
            elif key in KEYS:
                it[KEYS[key]] = val
        if warn:
            it['warn'] = ' '.join(warn)
        if k in CALM or 'טון' in it.get('warn', ''):
            it['calm'] = True
        if it['id'] in TWO_PART:
            it['two'] = True
        items.append(it)
byid = {i['id']: i for i in items}
# dates from CALENDAR.md
for line in open(os.path.join(D, 'CALENDAR.md'), encoding='utf-8'):
    m = re.match(r'^\| (\d\d-\d+) \| (\d\d)\.(\d\d)\.(\d{4}) \|', line)
    if m and m[1] in byid:
        byid[m[1]]['date'] = f'{m[4]}-{m[3]}-{m[2]}'
# clubs from 12-clubs.md: "## name" + "**סיפורים:** `ids`" or "name `ids` · name `ids`"
clubs = {}
head = None
for line in open(os.path.join(D, '12-clubs.md'), encoding='utf-8'):
    if line.startswith('## '):
        head = line[3:].strip()
    elif line.startswith('**סיפורים:**') and head:
        body = line.split('**סיפורים:**', 1)[1]
        segs = [s for s in body.split('·')]
        named = [re.match(r'\s*([^`]+?)\s*((?:`[^`]+`\s*)+)$', s) for s in segs]
        if len(segs) > 1 or (named[0] and named[0][1].strip()):
            for m in named:
                if m:
                    clubs.setdefault(m[1].strip(), []).extend(re.findall(r'`([^`]+)`', m[2]))
        else:
            for name in head.split(' / '):
                clubs.setdefault(name.strip(), []).extend(re.findall(r'`([^`]+)`', body))
for name, ids in clubs.items():
    for i in ids:
        if i in byid:
            byid[i].setdefault('clubs', []).append(name)
missing = sorted({i for ids in clubs.values() for i in ids if i not in byid})
out = {'cats': CATS, 'items': items, 'clubs': sorted(clubs)}
json.dump(out, open(os.path.join(D, 'catalog.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
print(f"items {len(items)}  dated {sum('date' in i for i in items)}  clubs {len(clubs)}  missing club ids {missing}")
