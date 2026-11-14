#!/usr/bin/env python3
"""
Builds the field catalogues of the AIFMD reporting (AIFM file, AIF file) for the KAG-Meldewesen
extension from the regulators' documents:

  * ESMA AIFMD XML schemas v1.2 (2013-1361): every element documents its field number
    ("... - AIF - Item 48 - ...") -> element path per field
  * ESMA IT technical guidance rev 6 (2013-1358, xlsx): field name, M/C/O/F, technical guidance
  * FMA "Prüfregeln AIF/AIFM Reporting File" (PDF, optional): the FMA's national rule per field

Usage:
  python3 scripts/aifmd-fields.py XSD_DIR GUIDANCE_XLSX OUT_DIR [FMA_AIF_PDF FMA_AIFM_PDF]

Writes OUT_DIR/aif-fields.json and OUT_DIR/aifm-fields.json (format of
org.fxt.freexmltoolkit.plugins.regreport.FieldCatalog).
"""
import json
import os
import re
import subprocess
import sys

import openpyxl
from lxml import etree

XS = '{http://www.w3.org/2001/XMLSchema}'
ITEM = re.compile(r'\bItems? (\d+)')
# shared types document both files: "AIFM - Item 37 and AIF - Item 50"
FILE_ITEM = {'AIF': re.compile(r'\bAIF - Items? (\d+)'), 'AIFM': re.compile(r'\bAIFM - Items? (\d+)')}


def load_schemas(xsd_dir):
    """All global elements and named types of the schemas in the folder."""
    elements, types = {}, {}
    for name in os.listdir(xsd_dir):
        if name.endswith('.xsd'):
            root = etree.parse(os.path.join(xsd_dir, name)).getroot()
            for child in root:
                if child.tag == XS + 'element':
                    elements[child.get('name')] = child
                elif child.tag in (XS + 'complexType', XS + 'simpleType') and child.get('name'):
                    types[child.get('name')] = child
    return elements, types


def documentation(el):
    doc = el.find(f'{XS}annotation/{XS}documentation')
    return doc.text if doc is not None and doc.text else ''


def item(doc, kind):
    m = FILE_ITEM[kind].search(doc) or (None if FILE_ITEM['AIF' if kind == 'AIFM' else 'AIFM'].search(doc) else ITEM.search(doc))
    return m.group(1) if m else None


def walk(el, path, elements, types, out, kind, depth=0):
    """Depth-first over the element's content; collects (path, field id) from the documentation."""
    if depth > 40:
        return
    if el.get('ref'):
        el = elements[el.get('ref')]
    name = el.get('name')
    here = path + [name]
    fid = item(documentation(el), kind)
    if fid:
        out.append(('/'.join(here), fid))
    content = el.find(XS + 'complexType')
    if content is None and el.get('type') in types:
        content = types[el.get('type')]
    if content is None or content.tag != XS + 'complexType':
        return
    for attr in content.iter(XS + 'attribute'):
        fid = item(documentation(attr), kind)
        if fid:
            out.append(('/'.join(here) + '/@' + attr.get('name'), fid))
    for child in content.iter(XS + 'element'):
        # only direct particles of this type (not those of nested anonymous types)
        parent = child.getparent()
        while parent is not None and parent.tag not in (XS + 'complexType',):
            parent = parent.getparent()
        if parent is content:
            walk(child, here, elements, types, out, kind, depth + 1)


def guidance(xlsx, sheet):
    """Field id -> (name, obligation, guidance), and the rows in table order as (id, element tag)."""
    wb = openpyxl.load_workbook(xlsx, read_only=True)
    rows = list(wb[sheet].iter_rows(values_only=True))
    head = next(i for i, r in enumerate(rows) if r and r[0] == 'Id')
    fields, order = {}, []
    for r in rows[head + 1:]:
        if r and r[0] is not None and str(r[0]).strip().isdigit():
            fid = str(r[0]).strip()
            fields.setdefault(fid, (str(r[1] or '').strip(), str(r[6] or '').strip(), str(r[8] or '').strip()))
            tag = re.sub(r'[<>\s]', '', str(r[2] or ''))
            order.append((fid, tag))
    return fields, order


def align(pairs, order):
    """Assigns the table's ids to the schema's elements in document order (same element name);
    the id documented in the schema is the fallback (shared types document one id for all uses)."""
    result, j = [], 0
    for path, doc_id in pairs:
        name = path.rsplit('/', 1)[-1]
        found = None
        for k in range(j, min(len(order), j + 25)):
            if order[k][1] == name:
                found = k
                break
        if found is not None:
            result.append((path, order[found][0]))
            j = found + 1
        else:
            result.append((path, doc_id))
    return result


def fma_rules(pdf):
    """Field id -> the FMA's rule text (column "Prüfregel" of the PDF)."""
    if not pdf or not os.path.isfile(pdf):
        return {}
    text = subprocess.run(['pdftotext', '-layout', pdf, '-'], capture_output=True, text=True).stdout
    rules = {}
    for page in text.split('\f'):
        lines = page.splitlines()
        col = next((l.index('Prüfregel') for l in lines if 'Prüfregel' in l and 'Id' in l), None)
        if col is None:
            col = next((l.index('Prüfregel') for l in lines if 'Prüfregel' in l), None)
        if col is None:
            continue
        ids = [(i, m.group(1)) for i, l in enumerate(lines) for m in [re.match(r'\s{0,8}(\d{1,3})\s{2,}', l)] if m]
        for i, line in enumerate(lines):
            # the rule column: the last run of text that starts near the column's header
            part = ''
            for m in re.finditer(r'(?:^|\s{3,})(\S(?:.*?\S)?)(?=\s{3,}|$)', line):
                if m.start(1) >= col - 60:
                    part = line[m.start(1):].strip()
                    break
            if not part or part == 'Prüfregel' or not ids:
                continue
            # the row of a line: the id whose line is nearest (ids sit in the middle of their row)
            fid = min(ids, key=lambda x: abs(x[0] - i))[1]
            rules.setdefault(fid, []).append(part)
    return {k: re.sub(r'\s+', ' ', ' '.join(v)).strip() for k, v in rules.items()}


def build(root_element, kind, sheet, label, xsd_dir, xlsx, pdf, out):
    elements, types = load_schemas(xsd_dir)
    pairs = []
    walk(elements[root_element], [], elements, types, pairs, kind)
    info, order = guidance(xlsx, sheet)
    pairs = align(pairs, order)
    rules = fma_rules(pdf)
    fields, seen = [], set()
    for path, fid in pairs:
        if '/@' in path or (path, fid) in seen:
            continue
        seen.add((path, fid))
        name, obligation, guide = info.get(fid, ('', '', ''))
        field = {'id': fid, 'name': name, 'path': path, 'obligation': obligation, 'guidance': guide}
        if fid in rules:
            field['rule'] = rules[fid]
        fields.append(field)
    catalog = {'format': label,
               'source': 'ESMA AIFMD reporting XML documents v1.2 (2013-1361), IT technical guidance rev 6 '
                         '(2013-1358)' + (', FMA Prüfregeln (Stand 29.01.2024)' if rules else ''),
               'fields': fields}
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    missing = sorted(set(info) - {fid for _, fid in pairs}, key=int)
    print(f'{out}: {len(fields)} fields, {len(rules)} FMA rules; ids without an element: {missing[:20]}')


def main():
    xsd_dir, xlsx, out_dir = sys.argv[1:4]
    aif_pdf = sys.argv[4] if len(sys.argv) > 4 else None
    aifm_pdf = sys.argv[5] if len(sys.argv) > 5 else None
    os.makedirs(out_dir, exist_ok=True)
    build('AIFReportingInfo', 'AIF', 'Technical guidance AIF file', 'AIFMD AIF reporting file', xsd_dir, xlsx, aif_pdf,
          os.path.join(out_dir, 'aif-fields.json'))
    build('AIFMReportingInfo', 'AIFM', 'Technical guidance AIFM file', 'AIFMD AIFM reporting file', xsd_dir, xlsx, aifm_pdf,
          os.path.join(out_dir, 'aifm-fields.json'))


if __name__ == '__main__':
    main()
