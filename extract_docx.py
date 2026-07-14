# -*- coding: utf-8 -*-
import sys, zipfile, re
import xml.etree.ElementTree as ET

NS = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}

def cell_text(tc):
    texts = []
    for p in tc.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}p'):
        t = ''.join(n.text or '' for n in p.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'))
        if t.strip():
            texts.append(t.strip())
    return ' / '.join(texts)

def walk(body, out):
    for child in body:
        tag = child.tag.split('}')[1]
        if tag == 'p':
            t = ''.join(n.text or '' for n in child.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'))
            if t.strip():
                out.append(t)
        elif tag == 'tbl':
            out.append('[TABLE]')
            for tr in child.findall('w:tr', NS):
                cells = [cell_text(tc) for tc in tr.findall('w:tc', NS)]
                out.append(' | '.join(cells))
            out.append('[/TABLE]')

src = sys.argv[1]
dst = sys.argv[2]
with zipfile.ZipFile(src) as z:
    xml = z.read('word/document.xml')
root = ET.fromstring(xml)
body = root.find('w:body', NS)
out = []
walk(body, out)
with open(dst, 'w', encoding='utf-8') as f:
    f.write('\n'.join(out))
print('OK', dst)
