#!/usr/bin/env python3
"""
Builds the field catalogue of the MiFIR transaction report (auth.016.001.01, ESMA65-8-2598) for the
ESMA Reporting extension. ESMA's tables do not number the schema's elements, so the element paths
(walked from the XSD) are mapped to the fields 1-65 of RTS 22 Annex I Table 2 here; names, content
and the validation rules (with their CON-xxx error codes) come from ESMA's validation rules workbook
(esma65-8-2594 annex 1, sheet TransactionDataValidations).

Usage: python3 scripts/esma-mifir-fields.py AUTH016_XSD VALIDATION_RULES_XLSX OUT_JSON
"""
import json
import re
import sys

import openpyxl
from lxml import etree

XS = '{http://www.w3.org/2001/XMLSchema}'

# (regex on the path below Tx/New or Tx/Cxl, RTS 22 field number); the first match wins
FIELDS = [
    (r'^TxId$', 2), (r'^Tx/TradPlcMtchgId$', 3), (r'^ExctgPty$', 4), (r'^InvstmtPtyInd$', 5), (r'^SubmitgPty$', 6),
    (r'^Buyr/AcctOwnr/Id/Prsn/FrstNm$', 9), (r'^Buyr/AcctOwnr/Id/Prsn/Nm$', 10), (r'^Buyr/AcctOwnr/Id/Prsn/BirthDt$', 11),
    (r'^Buyr/AcctOwnr/CtryOfBrnch$', 8), (r'^Buyr/AcctOwnr/', 7),
    (r'^Buyr/DcsnMakr/Prsn/FrstNm$', 13), (r'^Buyr/DcsnMakr/Prsn/Nm$', 14), (r'^Buyr/DcsnMakr/Prsn/BirthDt$', 15),
    (r'^Buyr/DcsnMakr/', 12),
    (r'^Sellr/AcctOwnr/Id/Prsn/FrstNm$', 18), (r'^Sellr/AcctOwnr/Id/Prsn/Nm$', 19), (r'^Sellr/AcctOwnr/Id/Prsn/BirthDt$', 20),
    (r'^Sellr/AcctOwnr/CtryOfBrnch$', 17), (r'^Sellr/AcctOwnr/', 16),
    (r'^Sellr/DcsnMakr/Prsn/FrstNm$', 22), (r'^Sellr/DcsnMakr/Prsn/Nm$', 23), (r'^Sellr/DcsnMakr/Prsn/BirthDt$', 24),
    (r'^Sellr/DcsnMakr/', 21),
    (r'^OrdrTrnsmssn/TrnsmssnInd$', 25), (r'^OrdrTrnsmssn/TrnsmttgBuyr$', 26), (r'^OrdrTrnsmssn/TrnsmttgSellr$', 27),
    (r'^Tx/TradDt$', 28), (r'^Tx/TradgCpcty$', 29), (r'^Tx/Qty', 30), (r'^Tx/DerivNtnlChng$', 32), (r'^Tx/Pric', 33),
    (r'^Tx/NetAmt$', 35), (r'^Tx/TradVn$', 36), (r'^Tx/CtryOfBrnch$', 37), (r'^Tx/UpFrntPmt', 38),
    (r'^Tx/CmplxTradCmpntId$', 40),
    (r'^FinInstrm/Id$', 41), (r'^FinInstrm/Othr/FinInstrmGnlAttrbts/Id$', 41), (r'/FullNm$', 42), (r'/ClssfctnTp$', 43),
    (r'/FinInstrmGnlAttrbts/NtnlCcy$', 44), (r'/OthrNtnlCcy$', 45), (r'/PricMltplr$', 46),
    (r'/UndrlygInstrm/.*/Nm/Term', 49), (r'/UndrlygInstrm/.*/Nm/', 48), (r'/UndrlygInstrm/', 47),
    (r'/OptnTp$', 50), (r'/StrkPric', 51), (r'/OptnExrcStyle$', 53), (r'/MtrtyDt$', 54), (r'/XpryDt$', 55),
    (r'/DlvryTp$', 56), (r'^InvstmtDcsnPrsn/Prsn/CtryOfBrnch$', 58), (r'^InvstmtDcsnPrsn/', 57),
    (r'^ExctgPrsn/Prsn/CtryOfBrnch$', 60), (r'^ExctgPrsn/', 59), (r'/WvrInd$', 61), (r'/ShrtSellgInd$', 62),
    (r'/OTCPstTradInd$', 63), (r'/RskRdcgTx$', 64), (r'/SctiesFincgTxInd$', 65),
]


def paths(xsd):
    root = etree.parse(xsd).getroot()
    elements = {e.get('name'): e for e in root if e.tag == XS + 'element'}
    types = {e.get('name'): e for e in root if e.get('name') and e.tag in (XS + 'complexType', XS + 'simpleType')}
    out = []

    def walk(el, path, depth=0):
        if depth > 30:
            return
        here = path + [el.get('name')]
        out.append('/'.join(here))
        ct = el.find(XS + 'complexType')
        if ct is None and el.get('type') in types and types[el.get('type')].tag == XS + 'complexType':
            ct = types[el.get('type')]
        if ct is None:
            return
        for child in ct.iter(XS + 'element'):
            parent = child.getparent()
            while parent is not None and parent.tag != XS + 'complexType':
                parent = parent.getparent()
            if parent is ct:
                walk(child, here, depth + 1)

    walk(elements['Document'], [])
    return out


def rules(xlsx):
    rows = list(openpyxl.load_workbook(xlsx, read_only=True)['TransactionDataValidations'].iter_rows(values_only=True))
    info = {}
    for r in rows[1:]:
        if not r[0] or not r[1] or not str(r[1]).strip().isdigit():
            continue
        fid = int(str(r[1]).strip())
        entry = info.setdefault(fid, {'name': '', 'content': '', 'format': '', 'rules': []})
        if not entry['name'] and r[2]:
            entry['name'] = ' '.join(str(r[2]).split())
        if not entry['content'] and r[3]:
            entry['content'] = ' '.join(str(r[3]).split())
        if not entry['format'] and r[4]:
            entry['format'] = ' '.join(str(r[4]).split())
        if r[5]:
            code = str(r[7] or '').strip()
            entry['rules'].append((code + ': ' if code and code != 'N/A' else '') + ' '.join(str(r[5]).split()))
    return info


def main():
    xsd, xlsx, out = sys.argv[1:4]
    info = rules(xlsx)
    fields = []
    for path in paths(xsd):
        m = re.match(r'Document/FinInstrmRptgTxRpt/Tx/(New|Cxl)/(.+)$', path)
        if not m:
            continue
        rest = m.group(2)
        fid = next((f for pattern, f in FIELDS if re.search(pattern, rest)), None)
        if fid is None:
            continue
        i = info.get(fid, {})
        field = {'id': str(fid), 'name': i.get('name', ''), 'path': path,
                 'obligation': 'New' if m.group(1) == 'New' else 'Cancellation',
                 'guidance': '\n'.join(x for x in (i.get('content', ''), i.get('format', '')) if x)}
        if i.get('rules'):
            field['rule'] = '\n'.join(i['rules'])
        fields.append(field)
    fields.insert(0, {'id': '1', 'name': info.get(1, {}).get('name', 'Report status'),
                      'path': 'Document/FinInstrmRptgTxRpt/Tx/New', 'obligation': 'New',
                      'guidance': info.get(1, {}).get('content', '')})
    with open(out, 'w', encoding='utf-8') as f:
        json.dump({'format': 'MiFIR transaction report (auth.016.001.01)',
                   'source': 'ESMA MiFIR transaction reporting XML schema 1.1.0 (ESMA65-8-2598) and validation rules '
                             '(esma65-8-2594)', 'fields': fields}, f, ensure_ascii=False, indent=1)
    print(f'{out}: {len(fields)} field paths, fields {sorted({int(f["id"]) for f in fields})}')


if __name__ == '__main__':
    main()
