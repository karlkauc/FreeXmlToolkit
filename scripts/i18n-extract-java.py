#!/usr/bin/env python3
"""Moves user-facing string literals of Java files into the English bundle.

Only a literal that is a whole argument of a known UI call is replaced (setText("…"),
new Tooltip("…"), new MenuItem("…"), DialogHelper.showError("…", "…", …), …) — never a literal
that is concatenated, compared or formatted. It becomes I18n.t("<Class>.<slug>") and the text is
appended to src/main/resources/i18n/messages.properties. New keys are also written to
build/i18n-new-keys.properties for translation.

Usage: scripts/i18n-extract-java.py [--dry-run] FILE.java...
"""
import pathlib
import re
import sys

BUNDLE = pathlib.Path("src/main/resources/i18n/messages.properties")
NEW_KEYS = pathlib.Path("build/i18n-new-keys.properties")
SINKS = {
    "setText", "setTitle", "setHeaderText", "setContentText", "setPromptText", "setAccessibleText",
    "Tooltip", "MenuItem", "CheckMenuItem", "RadioMenuItem", "Menu", "Label", "Button", "CheckBox",
    "ToggleButton", "RadioButton", "Hyperlink", "ShellMenuItem", "Tab", "TableColumn", "TreeTableColumn",
    "TitledPane", "showInformation", "showWarning", "showError", "showActionError", "showConfirmation",
    "showInfo", "inlineRow", "openToolTab", "showExceptionDialog", "setPlaceholderText", "setEmptyText",
    "showWarningDialog", "showInformationDialog", "showErrorDialog", "createMenuItem", "menuItem", "item",
    "setDescription", "showNotification", "showToast", "toast",
    "precondition", "info", "success", "failure", "warning", "error", "showStatus", "setStatus", "status",
    "showMessage", "showPending", "showFailure", "setSubtitle", "setCaption", "setHint", "addSection",
    "PageNavigator", "setPlaceholder", "showEmpty", "setHeader", "emptyLabel", "placeholder",
}
LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"(?:\s*\+\s*"(?:[^"\\\n]|\\.)*")*')
PART = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
IDENT_BEFORE_PAREN = re.compile(r"([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^<>()]*>)?\s*$")


JAVA_ESCAPE = re.compile(r"\\(u+[0-9a-fA-F]{4}|[ntrbf\"'\\]|[0-7]{1,3})")
SIMPLE = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", '"': '"', "'": "'", "\\": "\\"}


def unescape(s):
    def one(m):
        e = m.group(1)
        if e[0] == "u":
            return chr(int(e.lstrip("u"), 16))
        if e in SIMPLE:
            return SIMPLE[e]
        return chr(int(e, 8))
    return JAVA_ESCAPE.sub(one, s)


def prop_escape(v):
    out = v.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "")
    return "\\" + out if out.startswith(" ") else out


def is_text(t):
    if not re.search(r"[A-Za-z]{2}", t):
        return False
    if re.fullmatch(r"[a-z0-9_.\-/:#]+", t):      # ids, style classes, keys, paths
        return False
    if t.startswith(("bi-", "fxt-", "-fx", "http", "/", "#", "*.")) or re.search(r"[<>{}\[\]@$=]", t):
        return False  # ids, paths, markup, XPath, code and templates are no interface text
    return t[0].isupper() or " " in t or t.endswith(("…", "...", ":"))


def call_name(src, pos):
    """Name of the innermost call whose argument list contains pos, or None."""
    depth = 0
    i = pos - 1
    while i >= 0:
        c = src[i]
        if c == '"':  # skip a string literal backwards
            i -= 1
            while i >= 0 and not (src[i] == '"' and src[i - 1] != "\\"):
                i -= 1
        elif c in ")]}":
            depth += 1
        elif c in "([{":
            if depth == 0:
                if c != "(":
                    return None
                head = src[max(0, i - 120):i]
                m = IDENT_BEFORE_PAREN.search(head)
                if not m:
                    return None
                receiver = head[:m.start()].rstrip()
                # Log and telemetry calls keep their English texts
                if re.search(r"(?i)(\blog(ger)?|LOG(GER)?|Telemetry|UsageEvents|System\.(out|err))\s*\.\s*$", receiver):
                    return None
                return m.group(1)
            depth -= 1
        elif c == ";" and depth == 0:
            return None
        i -= 1
    return None


def parse_argument(src, start):
    """End of the argument expression starting at start, and its top-level '+' operands as (start, end)."""
    depth, i, n = 0, start, len(src)
    operands, op_start = [], start
    while i < n:
        c = src[i]
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
        elif c == "'":
            i += 1
            while i < n and src[i] != "'":
                i += 2 if src[i] == "\\" else 1
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            if depth == 0:
                operands.append((op_start, i))
                return i, operands
            depth -= 1
        elif depth == 0 and c == ",":
            operands.append((op_start, i))
            return i, operands
        elif depth == 0 and c in "?;" or (depth == 0 and src.startswith(("->", "==", "!=", "&&", "||"), i)):
            return None, None
        elif depth == 0 and c == "+":
            operands.append((op_start, i))
            op_start = i + 1
        i += 1
    return None, None


def mf_escape(text):
    """A literal part of a MessageFormat pattern."""
    return text.replace("'", "''").replace("{", "'{'").replace("}", "'}'")


def slug(t):
    words = re.findall(r"[A-Za-z0-9]+", re.sub(r"\{\d+\}", " ", t))[:5]
    return "-".join(w.lower() for w in words) or "text"


def comment_spans(src):
    spans = []
    for m in re.finditer(r"/\*.*?\*/|//[^\n]*", src, re.S):
        spans.append((m.start(), m.end()))
    return spans


def main():
    args = sys.argv[1:]
    dry = "--dry-run" in args
    files = [pathlib.Path(a) for a in args if a != "--dry-run"]
    existing = BUNDLE.read_text(encoding="utf-8")
    keys = dict(re.findall(r"^([^#\s][^=]*)=(.*)$", existing, re.M))
    added = []
    for path in files:
        src = path.read_text(encoding="utf-8")
        cls = path.stem
        spans = comment_spans(src)
        by_text = {}
        out, last, count = [], 0, 0
        for m in LITERAL.finditer(src):
            start, end = m.span()
            if any(a <= start < b for a, b in spans):
                continue
            if start < last:
                continue  # inside a concatenation replaced already
            text = "".join(unescape(p.group(1)) for p in PART.finditer(m.group(0)))
            before = src[:start].rstrip()
            after = src[end:].lstrip()
            args = []
            if before.endswith(("(", ",")) and after.startswith("+"):
                # "text " + expression + " more": a pattern with {0}, {1} …
                arg_end, operands = parse_argument(src, start)
                if arg_end is None or len(operands) < 2:
                    continue
                pattern, literal_text = [], []
                for a, b in operands:
                    part = src[a:b].strip()
                    if PART.fullmatch(part) or re.fullmatch(r'(?:"(?:[^"\\\n]|\\.)*"\s*)+', part):
                        lit = "".join(unescape(p.group(1)) for p in PART.finditer(part))
                        pattern.append(mf_escape(lit))
                        literal_text.append(lit)
                    else:
                        pattern.append("{" + str(len(args)) + "}")
                        args.append(part)
                if not args or not is_text("".join(literal_text).strip() or "x"):
                    continue
                text = "".join(pattern)
                end = arg_end
            elif not before.endswith(("(", ",")) or not after.startswith((")", ",")):
                continue
            if before.endswith("I18n.t(") or not (args or is_text(text)):
                continue
            name = call_name(src, start)
            if name not in SINKS:
                continue
            key = by_text.get(text)
            if key is None:
                base = f"{cls}.{slug(text)}"
                key, n = base, 2
                while key in keys and keys[key] != prop_escape(text):
                    key = f"{base}-{n}"
                    n += 1
                by_text[text] = key
                if key not in keys:
                    keys[key] = prop_escape(text)
                    added.append((path, key, text))
            out.append(src[last:start])
            out.append(f'I18n.t("{key}"' + "".join(", " + a for a in args) + ")")
            last = end
            count += 1
        out.append(src[last:])
        if count and not dry:
            new = "".join(out)
            if "import org.fxt.freexmltoolkit.i18n.I18n;" not in new:
                new = re.sub(r"^(package [^;]+;\n)", r"\1\nimport org.fxt.freexmltoolkit.i18n.I18n;\n", new, count=1, flags=re.M)
            path.write_text(new, encoding="utf-8")
        print(f"{count:4}  {path}", file=sys.stderr)
    if dry:
        for path, key, text in added:
            print(f"{key}={prop_escape(text)}")
        return
    if added:
        with BUNDLE.open("a", encoding="utf-8") as f:
            current = None
            for path, key, text in added:
                if path != current:
                    f.write(f"\n# {path.relative_to('src/main/java')}\n")
                    current = path
                f.write(f"{key}={prop_escape(text)}\n")
        NEW_KEYS.parent.mkdir(exist_ok=True)
        with NEW_KEYS.open("a", encoding="utf-8") as f:
            for path, key, text in added:
                f.write(f"{key}={prop_escape(text)}\n")
    print(f"{len(added)} new keys", file=sys.stderr)


if __name__ == "__main__":
    main()
