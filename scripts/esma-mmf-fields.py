#!/usr/bin/env python3
"""
Builds the field catalogue of the ESMA money market fund report (MMFR Art. 37, auth.093.001.01) for
the KAG-Meldewesen extension from ESMA's validation rules workbook (esma65-8-6480 annex 1):
field code, name, ITS field code, M/C/O, definition, XML and application validation rules and the
element path. A "check" is derived from the application rule where it is a plain code check
(ISO 4217 currency, ISO 3166 country, EU country, ISIN / LEI check digits).

Paths are written for both layouts: inside the business data envelope (BizData/Hdr/AppHdr,
BizData/Pyld/Document/...) and with Document as root element.

Usage: python3 scripts/esma-mmf-fields.py VALIDATION_RULES_XLSX OUT_JSON
"""
import json
import re
import sys

import openpyxl


def clean(value):
    return re.sub(r'\s+', ' ', str(value or '')).strip()


def check_of(rule):
    text = rule.lower()
    if 'iso 4217' in text:
        return 'currency'
    if 'iso 3166' in text and 'eu country' in text:
        return 'eu-country'
    if 'iso 3166' in text:
        return 'country'
    if 'check sum digits of the isin' in text or 'check digit' in text and 'isin' in text:
        return 'isin'
    if 'lei' in text and ('check' in text and 'digit' in text):
        return 'lei'
    return None


def main():
    xlsx, out = sys.argv[1:3]
    wb = openpyxl.load_workbook(xlsx, read_only=True)
    rows = list(wb['MoneyMarketFundReport'].iter_rows(values_only=True))
    fields = []
    for r in rows[1:]:
        if r[1] != 'E' or not r[17]:
            continue
        fid = clean(r[2]).split(' ')[0]
        path = str(r[17]).strip()
        if '/@' in path or not path.startswith('/Document/'):
            continue
        its = clean(r[10])
        name = clean(r[4]) + (f' ({its})' if its else '')
        guidance = '\n'.join(x for x in (clean(r[14]), clean(r[12])) if x)
        rule = '\n'.join(x for x in (str(r[15] or '').strip(), str(r[16] or '').strip()) if x)
        rest = path[len('/Document/'):]
        variants = []
        if rest.startswith('AppHdr/'):
            variants = ['BizData/Hdr/' + rest]
        else:
            variants = ['BizData/Pyld/Document/' + rest, 'Document/' + rest]
        for p in variants:
            field = {'id': fid, 'name': name, 'path': p, 'obligation': clean(r[6]), 'guidance': guidance}
            if rule:
                field['rule'] = re.sub(r'[ \t]+', ' ', rule)
            check = check_of(str(r[16] or ''))
            if check:
                field['check'] = check
            fields.append(field)
    catalog = {'format': 'ESMA money market fund report (auth.093.001.01)',
               'source': 'ESMA MMF reporting validation rules v07 (esma65-8-6480 annex 1)', 'fields': fields}
    with open(out, 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, indent=1)
    checks = {}
    for f in fields:
        if 'check' in f:
            checks[f['check']] = checks.get(f['check'], 0) + 1
    print(f'{out}: {len(fields)} field paths, checks {checks}')


if __name__ == '__main__':
    main()
