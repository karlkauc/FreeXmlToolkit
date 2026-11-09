#!/usr/bin/env python3
"""Extracts the field definitions of the FinDatEx templates (EMT, EET, EPT, TPT) from their official
xlsx specifications (https://findatex.eu) into src/main/resources/plugins/findatex/templates.json,
which the FinDatEx plugin uses to check template files.

Usage: scripts/findatex-fields.py EMT=EMT.xlsx EET=EET.xlsx EPT=EPT.xlsx TPT7=TPT_V7.xlsx TPT8=TPT_V8.xlsx
(needs openpyxl). Templates not named keep their entry of the current templates.json, so a single
template can be renewed when FinDatEx publishes a new version. Two versions of one template (TPT V7
and V8 during the transition) are both kept; the plugin picks one by the version value of a file.
"""
import json
import re
import sys

import openpyxl

# layout id -> (template, sheet name prefix, name column, definition column, codification column,
#               M/O/C column, first of the 16 CIC category columns 0-9, A-F or None)
LAYOUT = {
    "EMT": ("EMT", "EMT", 1, 3, 4, 6, None),
    "EET": ("EET", "EET", 1, 2, 3, 6, None),
    "EPT": ("EPT", "EPT", 1, 2, 3, 6, None),
    "TPT7": ("TPT", "TPT", 0, 2, 3, 10, 11),
    "TPT8": ("TPT", "TPT", 0, 2, 3, 5, 6),
}
# EMT/EET/EPT codes are 00010_Name; TPT codes are 1_Name, 8b_Name, 125_Accrued_income_(Security Denominated Currency)
FIELD = re.compile(r"^(\d{5}_[\w-]+|\d{1,4}[a-z]?_[^\n]+)$")
CIC_CATEGORIES = "0123456789ABCDEF"
# TPT: identification codes and the field that says how they are coded; what type 1 means there
TPT_IDENTIFIERS = {
    "1_": ("2_", "isin"),
    "14_": ("15_", "isin"),
    "68_": ("69_", "isin"),
    "47_": ("48_", "lei"),
    "50_": ("51_", "lei"),
}
TPT_LABEL = "14_"


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
    if lower.startswith("cic code") or ("_CIC_" in code and re.fullmatch(r"alphanumeric\s*\(4\)", lower)):
        return {"type": "cic"}
    if re.search(r"iso 3166-1 alpha.2", lower):
        return {"type": "country"}
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
            or lower.startswith("number of units") or lower.startswith("number with floating decimal"):
        return {"type": "number"}
    m = re.fullmatch(r"(?:alphanum\s*\(\s*max\s*(\d+)\s*\)|string\s*\[\s*(\d+)\s*\]|alpha\s*\(\s*max\s*(\d+)\s*\))", c, re.I)
    if m:
        return {"type": "text", "max": int(next(g for g in m.groups() if g))}
    # TPT closed lists: 'Alpha (1) ("Y" = yes "N"= no)', '"Fixed" or "Floating"', 'Alpha(3)( "Cal" = Call / "Put" = Put)'
    if code[:1] != "0" and '"' in c and "empty" not in lower and "e.g." not in lower and "example" not in lower:
        quoted = re.findall(r'"\s*([A-Za-z0-9_]{1,20})\s*"', c)
        if 2 <= len(quoted) <= 12 and (c.lstrip().startswith('"') or lower.startswith("alpha")):
            return {"type": "enum", "values": list(dict.fromkeys(quoted))}
    # TPT closed integer lists: "Integer return corresponding to the following closed list: 0 = non-listed 1 = EEA"
    if lower.startswith("integer return corresponding to the following closed list"):
        values = re.findall(r"(?:^|\s)(\d{1,2})\s*=", c)
        if len(values) >= 2:
            return {"type": "enum", "values": list(dict.fromkeys(values))}
    # Closed lists of short codes: "Y / N", "Y / N / Neutral", "R / P / B / N", "1 or 2", "S or SF or U"
    if ";" not in c:
        parts = [p.strip() for p in re.split(r"\s*/\s*|\s+or\s+", c) if p.strip()]
        if 2 <= len(parts) <= 12 and all(re.fullmatch(r"[A-Za-z0-9]{1,8}", p) for p in parts):
            return {"type": "enum", "values": parts}
    return {"type": "text"}


def extract(layout, path):
    template, prefix, name_col, def_col, cod_col, flag_col, cic_col = LAYOUT[layout]
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    sheet = next(s for s in wb.sheetnames if s.startswith(prefix))
    fields, sections = [], 0
    version = None
    for row in wb[sheet].iter_rows(values_only=True):
        if not row:
            continue
        name = row[name_col] if len(row) > name_col else None
        if isinstance(name, str) and FIELD.match(name.strip()):
            code = name.strip()
            codification = clean(row[cod_col], 300)
            flag = clean(row[flag_col], 3) if len(row) > flag_col and row[flag_col] else ""
            info = {"code": code, "definition": clean(row[def_col], 200), "codification": codification,
                    "flag": "N" if flag.upper() == "N/A" else flag[:1].upper(),
                    "section": sections}
            if cic_col is not None:
                info["cic"] = "".join(cat for i, cat in enumerate(CIC_CATEGORIES)
                                      if len(row) > cic_col + i and str(row[cic_col + i] or "").strip().lower() == "x")
            info.update(field_type(code, codification))
            if code.endswith("_Version"):
                listed = re.findall(r"V\d+(?:\.\d+)*(?:UK)?", codification)
                if template == "TPT":
                    version = listed[-1]  # the list gives the history; the newest is this sheet's
                    info["values"] = [version]
                else:
                    version = version or codification.split(" or ")[0].strip()
                    info["values"] = [v.strip() for v in codification.split(" or ")]
                info["type"] = "version"
            fields.append(info)
        elif template == "TPT":
            first = row[0] if row else None
            if isinstance(first, str) and first.strip() and first.strip() != "NUM_DATA" and fields:
                sections += 1  # a section heading after the first fields
        elif isinstance(row[0], str) and row[0].strip() and row[0].strip() != "NUM" and not name and fields:
            sections += 1  # a section heading after the first fields
    result = {"template": template, "version": version, "sheet": sheet, "fields": fields}
    if template == "TPT":
        link_identifiers(fields)
        result["label"] = next(f["code"] for f in fields if f["code"].startswith(TPT_LABEL))
    return result


def link_identifiers(fields):
    """TPT: identification codes become 'identifier' fields that name their type field and what type 1 is."""
    by_prefix = {f["code"].split("_", 1)[0] + "_": f for f in fields}
    for prefix, (type_prefix, kind) in TPT_IDENTIFIERS.items():
        field, type_field = by_prefix.get(prefix), by_prefix.get(type_prefix)
        if field and type_field:
            field["type"] = "identifier"
            field["typeField"] = type_field["code"]
            field["type1"] = kind
            type_field["type"] = "identifierType"


def main():
    args = [a.split("=", 1) for a in sys.argv[1:]]
    if not args or any(len(a) != 2 or a[0] not in LAYOUT for a in args):
        sys.exit(__doc__)
    target = "src/main/resources/plugins/findatex/templates.json"
    try:
        with open(target, encoding="utf-8") as f:
            current = json.load(f)["templates"]
    except FileNotFoundError:
        current = []
    fresh = {layout: extract(layout, path) for layout, path in args}
    renewed = {(t["template"], t["version"]) for t in fresh.values()}
    renewed_templates = {t["template"] for t in fresh.values() if t["template"] != "TPT"}
    out = [t for t in current
           if t["template"] not in renewed_templates and (t["template"], t["version"]) not in renewed
           and not (t["template"] == "TPT" and any(l.startswith("TPT") for l, _ in args))]
    out += fresh.values()
    order = {"EMT": 0, "EET": 1, "EPT": 2, "TPT": 3}
    out.sort(key=lambda t: (order.get(t["template"], 9), [int(n) for n in re.findall(r"\d+", t["version"] or "0")]))
    with open(target, "w", encoding="utf-8") as f:
        json.dump({"source": "FinDatEx template specifications, https://findatex.eu", "templates": out}, f,
                  ensure_ascii=False, indent=1)
    for t in out:
        print(t["template"], t["version"], len(t["fields"]), "fields")


if __name__ == "__main__":
    main()
