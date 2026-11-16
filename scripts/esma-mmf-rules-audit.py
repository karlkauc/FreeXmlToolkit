#!/usr/bin/env python3
"""Lists ESMA's MMF application validation rules (esma65-8-6480 annex 1, v07) with what checks them.

Usage: python3 scripts/esma-mmf-rules-audit.py regdata/esma/esma65-8-6480_annex_1_mmf_validation_rules_v07.xlsx OUT.md

Every application rule item (column "Application Validation rule", split at "1.", "2." ...) becomes a row:
  - external    needs data outside the file (GLEIF, ESMA's register of authorised MMFs, earlier reports, receipt);
  - implemented the field number appears as ESMA-MMF-<n> in MmfRules.java / MmfContentRules.java (or in its COVERED_FIELDS);
  - open        a content rule not checked yet.
The XML validation rules (formats, code lists) are left out: the schema checks them.
"""
import json
import re
import sys

import openpyxl

EXTERNAL = re.compile(r'(?i)GLEIF|register|database|previous|already|ESMA list|existing LEI|FIRDS|received|'
                      r'submission date|authorised during|authorised under|must be authorised')


def catalog_checks(catalog):
    """Field numbers whose format/code check runs from the field catalogue (CatalogChecks)."""
    try:
        return {f['id'] for f in json.load(open(catalog, encoding='utf-8'))['fields'] if f.get('check')}
    except FileNotFoundError:
        return set()


CODE_CHECK = re.compile(r'(?i)ISO 3166|ISO 4217|ISO 6166|ISIN|20 characters|2-character country|EU country|currency code')


def implemented_ids(sources, conditions):
    """Field numbers a rule reports (literal ids, COVERED_FIELDS, mmf-conditions.json if present)."""
    ids = set()
    for java in sources:
        text = open(java, encoding='utf-8').read()
        ids |= set(re.findall(r'ESMA-MMF-(\d+)', text))
        covered = re.search(r'COVERED_FIELDS = List\.of\((.*?)\);', text, re.S)
        if covered:
            ids |= set(re.findall(r'"(\d+)"', covered.group(1)))
    try:
        for rule in json.load(open(conditions, encoding='utf-8')).get('rules', []):
            ids.add(str(rule['field']))
    except FileNotFoundError:
        pass
    return ids


def main():
    xlsx, out = sys.argv[1:3]
    pkg = 'src/main/java/org/fxt/freexmltoolkit/plugins/kagmeldewesen/'
    java = [pkg + 'MmfRules.java', pkg + 'MmfContentRules.java']
    conditions = 'src/main/resources/plugins/kagmeldewesen/mmf/mmf-conditions.json'
    done = implemented_ids(java, conditions)
    checked = catalog_checks('src/main/resources/plugins/kagmeldewesen/mmf/mmf-fields.json')
    rows = list(openpyxl.load_workbook(xlsx, read_only=True)['MoneyMarketFundReport'].iter_rows(values_only=True))
    items, seen = [], set()
    for r in rows[1:]:
        if r[1] != 'E' or not r[16]:
            continue
        fid = str(r[2]).split()[0] if r[2] else '?'
        name = ' '.join(str(r[4] or '').split())
        for text in re.split(r'\n(?=\s*\d+\.)', str(r[16]).strip()):
            text = ' '.join(text.split())
            if not text or (fid, text) in seen:
                continue
            seen.add((fid, text))
            status = ('external' if EXTERNAL.search(text) else 'implemented' if fid in done
                      or (fid in checked and CODE_CHECK.search(text)) else 'open')
            items.append((fid, name, status, text))
    counts = {s: sum(1 for i in items if i[2] == s) for s in ('implemented', 'open', 'external')}
    with open(out, 'w', encoding='utf-8') as f:
        f.write('# ESMA MMF application rules - worklist\n\n')
        f.write(' · '.join(f'{k}: {v}' for k, v in counts.items()) + '\n\n')
        f.write('| Field | Name | Status | Rule |\n|---|---|---|---|\n')
        for fid, name, status, text in items:
            f.write(f'| {fid} | {name} | {status} | {text.replace("|", "/")} |\n')
    print(out, counts)


if __name__ == '__main__':
    main()
