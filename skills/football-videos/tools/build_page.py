"""Build the stories control page (stories/catalog.json inlined into control_page.template.html).
usage: python3 tools/build_page.py <out.html>   (run tools/build_catalog.py first)"""
import json, os, sys
T = os.path.dirname(os.path.abspath(__file__))
cat = json.load(open(os.path.join(T, '..', 'stories', 'catalog.json'), encoding='utf-8'))
data = json.dumps(cat, ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
html = open(os.path.join(T, 'control_page.template.html'), encoding='utf-8').read().replace('/*CATALOG*/', data, 1)
os.makedirs(os.path.dirname(os.path.abspath(sys.argv[1])), exist_ok=True)
open(sys.argv[1], 'w', encoding='utf-8').write(html)
print('wrote', sys.argv[1], len(html.encode()) // 1024, 'KB')
