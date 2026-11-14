#!/usr/bin/env python3
"""
Builds the field catalogue and the action-type matrix of the SFTR securities financing transaction
report (auth.052.001.02, ESMA usage guideline 1.2.0) for the ESMA Reporting extension:

  * the usage guideline workbook (Full_View): element path -> SFTR table and field number, name
  * ESMA's SFTR validation rules (ESMA74-362-1008, sheet Cpty_Transaction): per field, action type
    (NEWT, MODI, EROR, ETRM, POSC, COLU, CORR, VALU) and variant - level (TCTN trade, PSTN position)
    and SFT type (Repo, BSB, SL, ML) - whether it is Mandatory, Conditional, Optional or "-", plus the
    conditional rules text and ESMA's error codes

Usage: python3 scripts/esma-sftr-fields.py AUTH052_XLSX VALIDATION_RULES_XLSX OUT_DIR
Writes OUT_DIR/sftr-fields.json (FieldCatalog) and OUT_DIR/sftr-matrix.json (ActionMatrix).
"""
import json
import os
import re
import sys

import openpyxl

ELEMENT_ACTION = {'New': 'NEWT', 'Mod': 'MODI', 'Err': 'EROR', 'EarlyTermntn': 'ETRM', 'PosCmpnt': 'POSC',
                  'CollUpd': 'COLU', 'Crrctn': 'CORR', 'ValtnUpd': 'VALU'}
PREFIX = '/Document/SctiesFincgRptgTxRpt/TradData/Rpt/'


def clean(value):
    return re.sub(r'\s+', ' ', str(value or '')).strip()


def matrix(xlsx):
    rows = list(openpyxl.load_workbook(xlsx, read_only=True)['Cpty_Transaction'].iter_rows(values_only=True))
    level_row, action_row, type_row = rows[2], rows[3], rows[4]
    columns = {}
    level, action = None, None
    for j in range(5, len(type_row)):
        if level_row[j]:
            level = 'PSTN' if 'Position' in str(level_row[j]) else 'TCTN'
        if action_row[j]:
            m = re.match(r'([A-Z]{4})', str(action_row[j]).strip())
            action = m.group(1) if m else None
        sft = clean(type_row[j])
        if action and sft in ('Repo', 'BSB', 'SL', 'ML'):
            columns[j] = (f'{level}.{sft}', action)
    rules_col = next(j for j, c in enumerate(action_row) if c and 'Conditional' in str(c))
    codes_col = next(j for j, c in enumerate(action_row) if c and 'Error code' in str(c))
    result = {}
    for r in rows[5:]:
        if not r or r[0] is None or r[1] is None:
            continue
        fid = f'{clean(r[0])}.{clean(r[1])}'
        if not re.fullmatch(r'\d+\.\d+', fid):
            continue
        obligations = {}
        for j, (variant, action) in columns.items():
            value = clean(r[j]) if j < len(r) else ''
            if value:
                obligations.setdefault(variant, {})[action] = value
        result[fid] = {'name': clean(r[2]), 'format': clean(r[4]), 'rules': str(r[rules_col] or '').strip(),
                       'codes': clean(r[codes_col]), 'obligations': obligations}
    return result


def check_of(fmt, element):
    if element == 'LEI':
        return 'lei'
    if element == 'ISIN':
        return 'isin'
    if element == 'Ccy' or 'iso 4217' in fmt.lower() and element.endswith('Ccy'):
        return 'currency'
    if element == 'Ctry':
        return 'country'
    return None


def main():
    guideline, rules_xlsx, out_dir = sys.argv[1:4]
    m = matrix(rules_xlsx)
    rows = list(openpyxl.load_workbook(guideline, read_only=True)['Full_View'].iter_rows(values_only=True))
    fields, paths = [], {}
    for r in rows[1:]:
        if not r[16] or not r[17] or not r[23] or str(r[14] or '') == 'Yes':
            continue
        fid = f'{clean(r[17])}.{clean(r[16])}'
        path = str(r[23]).strip()
        if not path.startswith(PREFIX) or not re.fullmatch(r'\d+\.\d+', fid):
            continue
        rest = path[len(PREFIX):]
        action_element = rest.split('/')[0]
        action = ELEMENT_ACTION.get(action_element)
        relative = rest[len(action_element) + 1:] if '/' in rest else ''
        if action and relative:
            lst = paths.setdefault(fid, {}).setdefault(action, [])
            if relative not in lst:
                lst.append(relative)
        info = m.get(fid, {})
        tctn = info.get('obligations', {}).get('TCTN.SL', {})
        field = {'id': fid, 'name': clean(info.get('name')) or clean(r[2]), 'path': 'Document/SctiesFincgRptgTxRpt/TradData/Rpt/' + rest,
                 'obligation': ', '.join(f'{a} {o}' for a, o in tctn.items()) + (' (securities lending)' if tctn else ''),
                 'guidance': clean(r[24])}
        if info.get('rules'):
            field['rule'] = info['rules']
        check = check_of(info.get('format', ''), rest.rsplit('/', 1)[-1])
        if check:
            field['check'] = check
        fields.append(field)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, 'sftr-fields.json'), 'w', encoding='utf-8') as f:
        json.dump({'format': 'SFTR securities financing transaction report (auth.052.001.02)',
                   'source': 'ESMA SFTR counterparty and TR data exchange 2023 (auth.052 1.2.0) and validation rules '
                             '(ESMA74-362-1008)', 'fields': fields}, f, ensure_ascii=False, indent=1)
    out = {fid: {'name': info['name'], 'codes': info['codes'], 'obligations': info['obligations'],
                 'paths': paths.get(fid, {})} for fid, info in m.items()}
    with open(os.path.join(out_dir, 'sftr-matrix.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f'{len(fields)} field paths, {len(m)} fields in the matrix, without a path: '
          f'{sorted(fid for fid in m if fid not in paths)[:30]}')


if __name__ == '__main__':
    main()
