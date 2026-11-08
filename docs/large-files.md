# Large File Viewer

The **Large File Viewer** shows text and XML files of any size, read-only: a 1 GB file opens in a
few seconds and needs only a few megabytes of memory. It reads the file from disk as you scroll,
instead of loading it into the editor. Use it for files that are too large for the editor, or when
you only want to look at, search or copy from a large file.

## Opening a file in the viewer

- **Opening a large file:** a file of 50 MB or more asks how to open it: **Open in Viewer**,
  **Open in Editor** or **Cancel**. A file too large for the editor's memory limit (see
  [Memory and large files](settings.md#memory)) offers only the viewer.
- **File → Open in Large File Viewer…** opens any file in the viewer, whatever its size.

The tab is named `<file> (viewer)`. While the file is read for the first time, a progress bar
shows how far it is (a 1 GB file takes a few seconds on an SSD); **Cancel** stops it. The bar above
the text shows the size, the number of lines and the encoding.

## Working in the viewer

| Action | How |
|---|---|
| Scroll | Mouse wheel, scroll bar, Page Up / Page Down, Home / End |
| Go to a line | Type the number into **Line** (or press **Ctrl+G**) and press Enter |
| Select | Click a row; Shift+click or Shift+arrow keys extend the selection |
| Copy | **Ctrl+C** copies the selected rows (up to 100,000) |
| Find | Type into **Find** (or press **Ctrl+F**) and press Enter; **Regex** and **Match case** refine the search |
| Next / previous match | **F3** / **Shift+F3**, or click a match in the list below the text |

The search runs in the background over the whole file and lists up to 10,000 matches with their
line numbers; clicking one shows its line. The status bar shows the line and the byte offset of the
selection.

XML is coloured line by line (tags, attributes, values, comments). A line longer than 4 KB, such as a
minified document on one line, is shown as several rows; the continuation rows are marked with `↪`
instead of a line number. A match that spans two such rows is not found.

## Structure (XML)

For an XML file, the **Structure** tab shows the elements without reading the whole file:

- **Tree** (left): the root element; expanding an element lists its children, 1,000 at a time.
  **Load more…** at the end of a list reads the next 1,000. Each element shows its name, its first
  attribute and, for an element with only text, the text.
- **Table** (right): the children of the selected element as records, one row per child. The columns
  are the children's attributes (`@id`, …) and the texts of their simple child elements (elements
  that contain only text, such as `<amount>`), so a file of records reads like a spreadsheet.
  **Load More** adds the next 1,000 children.

Double-click a row, or choose **Show in Text** in the context menu, to see the element in the
**Text** tab. **Open as New Document** opens the element (up to 50 MB) as a new, unsaved document
in the editor, where it can be edited, validated or transformed like any other file. **Copy XPath**
copies the element's path, such as `/orders[1]/order[42]`.

The structure is read from the bytes of the file on demand, so the first 1,000 children of the root
of a 1 GB file appear in well under a second. Reading assumes a well-formed document; DTD
declarations are skipped, entities other than the predefined ones are not expanded.

## Limits

- The viewer is read-only. To change a large file, open it in the editor (if it fits the memory
  limit), or open the element you need as a new document from the **Structure** tab.
- UTF-8, ASCII and single-byte encodings such as ISO-8859-1 are supported; UTF-16 and UTF-32 files
  are not.
- The file is read where it is: if it changes on disk while it is open, close and reopen the viewer.
