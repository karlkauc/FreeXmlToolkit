#!/usr/bin/env python3
"""
Builds the field catalogue and the action-type matrix of the EMIR Refit trade report
(auth.030.001.03, ESMA usage guideline DATTAR 1.1.0) for the ESMA-Meldungen extension:

  * the usage guideline workbook (Full_View): element path -> EMIR field number, name, details
  * ESMA's EMIR Refit validation rules workbook (Validations_Cpty_derivative): per field and
    action type (NEWT, MODI, VALU, CORR, TERM, EROR, REVI, POSC) at trade and position level
    whether it is Mandatory, Conditional, Optional or not applicable ("-"), plus the rules text

Usage: python3 scripts/esma-emir-fields.py DATTAR_XLSX VALIDATION_RULES_XLSX OUT_DIR
Writes OUT_DIR/emir-fields.json (FieldCatalog) and OUT_DIR/emir-matrix.json.
"""
import json
import os
import re
import sys

import openpyxl

ACTIONS = ['NEWT', 'MODI', 'VALU', 'CORR', 'TERM', 'EROR', 'REVI', 'POSC']
ELEMENT_ACTION = {'New': 'NEWT', 'Mod': 'MODI', 'Crrctn': 'CORR', 'Termntn': 'TERM', 'PosCmpnt': 'POSC',
                  'ValtnUpd': 'VALU', 'Err': 'EROR', 'Rvv': 'REVI'}
PREFIX = '/Document/DerivsTradRpt/TradData/Rpt/'


def clean(value):
    return re.sub(r'\s+', ' ', str(value or '')).strip()


def check_of(fmt):
    text = fmt.lower()
    if 'iso 17442' in text or 'legal entity identifier' in text:
        return 'lei'
    if 'iso 6166' in text or 'isin' in text and 'iso' in text:
        return 'isin'
    if 'iso 4217' in text:
        return 'currency'
    if 'iso 3166' in text:
        return 'country'
    return None


def matrix(xlsx):
    rows = list(openpyxl.load_workbook(xlsx, read_only=True)['Validations_Cpty_derivative'].iter_rows(values_only=True))
    head = next(i for i, r in enumerate(rows) if r and 'NEWT' in [str(c).strip() for c in r if c])
    result = {}
    for r in rows[head + 1:]:
        if not r or r[1] is None or r[2] is None:
            continue
        fid = f'{clean(r[1])}.{clean(r[2])}'
        if not re.fullmatch(r'\d+\.\d+', fid):
            continue
        trade = {a: clean(r[11 + i]) for i, a in enumerate(ACTIONS)}
        position = {a: clean(r[20 + i]) for i, a in enumerate(ACTIONS)}
        result[fid] = {'name': clean(r[4]), 'format': clean(r[6]), 'rules': str(r[28] or '').strip(),
                       'codes': clean(r[29]), 'TRAD': trade, 'PSTN': position}
    return result


def main():
    dattar, rules_xlsx, out_dir = sys.argv[1:4]
    m = matrix(rules_xlsx)
    rows = list(openpyxl.load_workbook(dattar, read_only=True)['Full_View'].iter_rows(values_only=True))
    fields, paths = [], {}
    for r in rows[1:]:
        if not r[17] or not r[23] or str(r[14] or '') == 'Yes':
            continue
        fid = clean(r[17]).split(' ')[0]
        path = str(r[23]).strip()
        if not path.startswith(PREFIX):
            continue
        rest = path[len(PREFIX):]
        action_element = rest.split('/')[0]
        action = ELEMENT_ACTION.get(action_element)
        relative = rest[len(action_element) + 1:] if '/' in rest else ''
        if action and relative:
            paths.setdefault(fid, {}).setdefault(action, [])
            if relative not in paths[fid][action]:
                paths[fid][action].append(relative)
        info = m.get(fid, {})
        trade = info.get('TRAD', {})
        obligation = ', '.join(f'{a} {trade[a]}' for a in ACTIONS if trade.get(a)) if trade else ''
        guidance = clean(r[18])
        rule = info.get('rules', '')
        # documents in the BizData envelope are looked up without its BizData/Pyld/ prefix (CatalogDescriber)
        for variant in ('Document/DerivsTradRpt/TradData/Rpt/' + rest,):
            field = {'id': fid, 'name': clean(r[16]) or info.get('name', ''), 'path': variant,
                     'obligation': obligation, 'guidance': guidance}
            if rule:
                field['rule'] = rule
            check = check_of(info.get('format', ''))
            if check and path.endswith(('/LEI', '/ISIN', '/Ccy', '/Ctry')) or check in ('currency', 'country') and \
                    path.rsplit('/', 1)[-1] in ('Ccy', 'Ctry'):
                field['check'] = {'LEI': 'lei', 'ISIN': 'isin', 'Ccy': 'currency', 'Ctry': 'country'}.get(
                    path.rsplit('/', 1)[-1], check)
            fields.append(field)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, 'emir-fields.json'), 'w', encoding='utf-8') as f:
        json.dump({'format': 'EMIR Refit trade report (auth.030.001.03)',
                   'source': 'ESMA EMIR Refit incoming messages 1.1.0 (DATTAR) and validation rules (esma74-362-2683)',
                   'fields': fields}, f, ensure_ascii=False, indent=1)
    out = {fid: {'name': info['name'], 'codes': info['codes'],
                 'obligations': {'TRAD': info['TRAD'], 'PSTN': info['PSTN']},
                 'paths': paths.get(fid, {})} for fid, info in m.items()}
    with open(os.path.join(out_dir, 'emir-matrix.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    unmapped = sorted(fid for fid in m if fid not in paths)
    print(f'{len(fields)} field paths, {len(m)} fields in the matrix, without a path: {unmapped[:30]}')


if __name__ == '__main__':
    main()
