# fxt-cli: compare, merge, convert

`compare`, `merge` and `convert`. Installation, exit codes and logging are on the [overview page](command-line.md).

## compare

Compares two XML files by structure, two JSON or YAML files by their data, or two folders file by
file, like **Compare with File…** in XML or Data mode and **Compare Folders…** in the application (see
[Compare & Merge](../guides/compare-merge.md#comparing-as-xml)). Formatting, attribute order and namespace
prefixes do not count.

```bash
fxt-cli compare [options] LEFT RIGHT
```

| Option | Meaning |
|---|---|
| `--exact-whitespace` | Whitespace in text counts (default: trimmed, runs collapsed, whitespace between elements ignored) |
| `--ignore-comments` | Do not compare comments |
| `--ignore-order` | Pair elements regardless of their position |
| `--prefixes` | Also report changed namespace prefixes |
| `-k`, `--key ATTR,...` | Attributes that identify an element among same-named siblings, e.g. `-k id` |
| `--ignore XPATH` | Leave out the nodes or attributes this XPath 1.0 selects, on both sides, e.g. `--ignore '//@created'`; repeatable |
| `--tolerance N` | Numbers in text or attribute values that differ by at most `N` are equal (default: exact) |
| `--[no-]placeholders` | Evaluate XMLUnit placeholders in LEFT: `${xmlunit.ignore}`, `${xmlunit.isNumber}`, `${xmlunit.isDateTime}`, `${xmlunit.matchesRegex(REGEX)}` (default: on) |
| `--bytes` | Folders: compare XML, JSON and YAML files by their bytes only |
| `-f`, `--format FORMAT` | `text` (default) or `json` |

LEFT is the expected document. With placeholders it can describe a result whose values change from
run to run (an ID, a date): see [Comparing as XML](../guides/compare-merge.md#comparing-as-xml).

**JSON and YAML.** Two files with the extensions `.json`, `.jsonc`, `.json5`, `.yaml` or `.yml` are
compared by their data, a JSON file against a YAML file too: member order, formatting, comments,
quoting and YAML anchors do not count, `1.0` equals `1`. Arrays are compared in order (an inserted
item is one difference); `--ignore-order` pairs items regardless of position, `-k id` pairs objects
by a key member, `--tolerance` applies to numbers. Locations are JSONPath:
`deploy.yaml:3: Member 'replicas' changed: "2" → "3" ($.replicas)`. `--ignore` is for XML only.

For two files, the text report has one line per difference in the form
`left.xml:LINE: Attribute 'qty' changed: "1" → "2" (/order[1]/item[1]/@qty)` and a summary;
differences XMLUnit rates *similar* (a moved element, CDATA instead of text) are marked `[similar]`
(JSON: `"similar": true`) and still count. For
two folders it lists every file that differs or exists on one side only - XML files compared by
structure, JSON and YAML files by their data (`differs    deploy.yaml (2 data differences)`) - then
the counts; in the JSON report the count is `xmlDifferences` for both kinds. The exit
code is 0 when the files are equivalent or the folders match, 1 when they differ, 3 when one of two
compared files is not well-formed XML or not valid JSON / YAML (inside folders such a file counts as
different, with a note).

## merge

Three-way merge of a base and two changed versions, like **Tools → Merge Files…** (see
[Merging three files](../guides/compare-merge.md#merging-three-files)). Conflicts are written with Git's
markers.

```bash
fxt-cli merge [options] BASE LEFT RIGHT
```

| Option | Meaning |
|---|---|
| `-o`, `--output FILE` | Result file (may be LEFT); without it the result goes to standard output |
| `-L`, `--label NAME` | Names in the conflict markers, up to three in the order LEFT, BASE, RIGHT |
| `--no-base` | Leave the base part (`|||||||`) out of conflict blocks |
| `--exact-whitespace` | For XML: changes that differ only in whitespace are a conflict too |

The exit code is 0 without conflicts and 1 with conflicts (the result then contains the markers).
A merge without conflicts whose XML is not well-formed gives a warning on standard error.

As a Git merge driver for XML files, in `.git/config` or `~/.gitconfig`:

```ini
[merge "fxt"]
    name = FreeXmlToolkit XML merge
    driver = fxt-cli merge -q -L ours -L base -L theirs %O %A %B -o %A
```

and in `.gitattributes`: `*.xml merge=fxt`.

## convert

Converts between XML, JSON and YAML, like **Tools → Convert to JSON… / XML… / YAML…** in the
application. The mappings are described in the
[JSON Editor](../guides/json-yaml.md#converting-between-xml-json-and-yaml) chapter.

```bash
fxt-cli convert [options] INPUT...
```

| Option | Meaning |
|---|---|
| `-t`, `--to FORMAT` | `json`, `xml` or `yaml`; default: `json` for XML input, `xml` for JSON and YAML input |
| `--from FORMAT` | `xml`, `json`, `yaml`; default: by extension (`.json`/`.jsonc`/`.json5`, `.yaml`/`.yml`, everything else XML) |
| `-m`, `--mapping MAPPING` | `structured` (default) or `w3c` (XPath 3.1 `fn:json-to-xml` vocabulary, lossless) |
| `--typed` | XML to JSON or YAML: numbers and `true`/`false` become numbers and booleans |
| `--root NAME` | JSON to XML: root element when the JSON does not name one; default `json` |
| `--indent N` | Spaces per indentation level, `0` for compact output; default 2 |
| `-o`, `--output FILE` | Output file (one input) |
| `-d`, `--output-dir DIR` | Output folder for several inputs; each result is named after its input with the new extension |

Without `-o` and `-d` the result goes to standard output. A file that cannot be converted
(not well-formed, not valid JSON or YAML, not in the W3C vocabulary with `-m w3c`) is reported on
standard error and gives exit code 1; the other files are still converted.
