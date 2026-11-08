#!/usr/bin/env python3
"""Extracts the field definitions of the FinDatEx templates (EMT, EET, EPT) from their official
xlsx specifications (https://findatex.eu) into src/main/resources/plugins/findatex/templates.json,
which the FinDatEx plugin uses to check template files.

Usage: scripts/findatex-fields.py EMT.xlsx EET.xlsx EPT.xlsx
(the files in that order; needs openpyxl). Re-run when FinDatEx publishes a new version.
"""
import json
import re
import sys

import openpyxl

# template id -> (sheet name prefix, name column, definition column, codification column, M/O/C column)
LAYOUT = {
    "EMT": ("EMT", 1, 3, 4, 6),
    "EET": ("EET", 1, 2, 3, 6),
    "EPT": ("EPT", 1, 2, 3, 6),
}
FIELD = re.compile(r"^\d{5}_[\w-]+$")


def clean(value, limit):
    text = re.sub(r"\s+", " ", str(value or "")).strip()
    return text if len(text) <= limit else text[: limit - 1] + "…"


def field_type(code, codification):
    c = codification
    lower = c.lower()
    if "hh:mm:ss" in c:
        return {"type": "datetime"}
    if re.fullmatch(r"YYYY-MM-DD\s+ISO 8601", c):
        return {"type": "date"}
    if lower.startswith("use the following priority"):
        return {"type": "identifier"}
    if lower.startswith("one of the options in the following closed list"):
        return {"type": "identifierType"}
    if "iso 4217" in lower:
        return {"type": "currency"}
    if "iso 17442" in lower or code.endswith("_LEI"):
        return {"type": "lei"}
    m = re.fullmatch(r"(?:number\s*\[\s*(\d+)\s*-\s*(\d+)\s*\]|(\d+)\s*-\s*(\d+) or Empty|(\d+) to (\d+))", c, re.I)
    if m:
        lo, hi = [g for g in m.groups() if g is not None]
        return {"type": "integer", "min": int(lo), "max": int(hi)}
    if lower == "integer":
        return {"type": "integer"}
    if lower.startswith("floating decimal") or lower.startswith("number with decimal") \
            or lower.startswith("number of units"):
        return {"type": "number"}
    m = re.fullmatch(r"(?:alphanum\s*\(\s*max\s*(\d+)\s*\)|string\s*\[\s*(\d+)\s*\])", c, re.I)
    if m:
        return {"type": "text", "max": int(next(g for g in m.groups() if g))}
    # Closed lists of short codes: "Y / N", "Y / N / Neutral", "R / P / B / N", "1 or 2", "S or SF or U"
    if ";" not in c:
        parts = [p.strip() for p in re.split(r"\s*/\s*|\s+or\s+", c) if p.strip()]
        if 2 <= len(parts) <= 12 and all(re.fullmatch(r"[A-Za-z0-9]{1,8}", p) for p in parts):
            return {"type": "enum", "values": parts}
    return {"type": "text"}


def extract(template, path):
    prefix, name_col, def_col, cod_col, flag_col = LAYOUT[template]
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    sheet = next(s for s in wb.sheetnames if s.startswith(prefix))
    fields, section, sections = [], None, 0
    version = None
    for row in wb[sheet].iter_rows(values_only=True):
        name = row[name_col] if len(row) > name_col else None
        if isinstance(name, str) and FIELD.match(name.strip()):
            code = name.strip()
            codification = clean(row[cod_col], 300)
            info = {"code": code, "definition": clean(row[def_col], 200), "codification": codification,
                    "flag": clean(row[flag_col], 3)[:1] if row[flag_col] else "",
                    "section": sections}
            info.update(field_type(code, codification))
            if version is None and code.endswith("_Version"):
                version = codification.split(" or ")[0].strip()
                info["type"] = "version"
                info["values"] = [v.strip() for v in codification.split(" or ")]
            fields.append(info)
        elif isinstance(row[0], str) and row[0].strip() and row[0].strip() != "NUM" and not name and fields:
            sections += 1  # a section heading after the first fields
    return {"template": template, "version": version, "sheet": sheet, "fields": fields}


def main():
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    out = [extract(t, p) for t, p in zip(("EMT", "EET", "EPT"), sys.argv[1:])]
    target = "src/main/resources/plugins/findatex/templates.json"
    with open(target, "w", encoding="utf-8") as f:
        json.dump({"source": "FinDatEx template specifications, https://findatex.eu", "templates": out}, f,
                  ensure_ascii=False, indent=1)
    for t in out:
        print(t["template"], t["version"], len(t["fields"]), "fields")


if __name__ == "__main__":
    main()
