# XSLT Transformation

> **Last Updated:** December 2025 | **Version:** 1.1.0

This tool lets you transform XML documents into other formats like HTML, text, or different XML structures using XSLT stylesheets. You can also use XQuery to query and transform multiple XML files at once.

---

## What is XSLT?

XSLT (Extensible Stylesheet Language Transformations) is a language for transforming XML documents. Think of it like a recipe that tells the computer how to convert your XML data into a different format.

![XSLT Transformation Overview](img/xslt-factsheet.png)
*XSLT transformation showing XML input and HTML output*

---

## How It Works

You need two files:
1. **XML File** - Your source document with the data
2. **XSLT File** - The stylesheet with transformation rules

The tool applies the rules from the XSLT file to your XML and generates the output.

![XSLT File Selection](img/xslt-file-selection.png)
*Screenshot placeholder: File selection panel with XML and XSLT inputs*

---

## Using the XSLT Tool

### Step 1: Select Your Files

1. In the left panel, select your XML source file
2. In the right panel, select your XSLT stylesheet
3. The transformation runs automatically

### Step 2: View the Results

![XSLT Results](img/xslt-results.png)
*Screenshot placeholder: Transformation results panel*

The output appears in the appropriate viewer:
- **HTML output** → Displayed as a rendered web page
- **XML output** → Displayed in a code editor with highlighting
- **Text output** → Displayed as plain text

---

## Features

| Feature | Description |
|---------|-------------|
| **Automatic Transformation** | Results update when you select files |
| **Multiple Output Formats** | HTML, XML, Text, and more |
| **Live Preview** | See results immediately |
| **Open in Browser** | View HTML output in your web browser |
| **Multi-File Batch Processing** | Process multiple XML files with one XQuery |
| **Error Messages** | Clear feedback when something goes wrong |

---

## Multi-File Batch Processing

Process multiple XML files at once using XQuery. This is useful when you need to:

- Extract data from many XML files and combine the results
- Generate reports across multiple documents
- Search for patterns across a collection of files
- Aggregate statistics from multiple sources

### Switching to Batch Mode

1. In the **XML Source** tab, look for the mode toggle at the top
2. Select **Multiple Files (Batch)** to enable batch processing
3. The interface changes to show the file selection area

### Adding Files for Processing

You have two ways to add files:

**Add Individual Files:**
1. Click the **Add Files** button
2. Select one or more XML files from your computer
3. The files appear in the list with checkboxes

**Add All Files from a Folder:**
1. Click the **Add Directory** button
2. Choose a folder containing XML files
3. All XML files from that folder are added to the list

### Managing Your File List

| Action | How To |
|--------|--------|
| **Select/Deselect Files** | Use the checkbox next to each file name |
| **Select All** | Check the "Select All" checkbox at the top |
| **Remove Files** | Select files and click "Remove Selected" |
| **Clear All** | Click "Clear All" to start fresh |

The file list shows:
- File name
- File size
- Processing status (Ready, Processing, Success, or Error)

### Writing XQuery for Multiple Files

When processing multiple files, use the `collection()` function to access all your selected files. Here are some examples:

**Example 1: List All Root Elements**
```xquery
for $doc in collection()
return $doc/*/local-name()
```

**Example 2: Extract Specific Elements**
```xquery
for $doc in collection()
for $order in $doc//order
return <result>
  <file>{document-uri($doc)}</file>
  <orderId>{$order/@id/string()}</orderId>
  <total>{$order/total/string()}</total>
</result>
```

**Example 3: Count Elements Across All Files**
```xquery
<summary>
  <totalFiles>{count(collection())}</totalFiles>
  <totalOrders>{count(collection()//order)}</totalOrders>
</summary>
```

**Example 4: Find Files Containing Specific Data**
```xquery
for $doc in collection()
where $doc//status = "pending"
return document-uri($doc)
```

### Running the Batch Transformation

1. Add your XML files to the list
2. Write your XQuery in the XQuery editor
3. Click the **Transform** button
4. Wait for processing to complete

During processing:
- Each file shows "Processing..." status
- When done, files show "Success" or "Error"
- Statistics appear showing how many files succeeded

### Viewing Batch Results

After processing, you can view results in two ways:

**Combined View (Default):**
- Shows all results merged together
- Best for aggregated queries or summaries
- Select "Combined" in the result mode toggle

**Per-File View:**
- Shows results for each file individually
- Use the dropdown to select which file to view
- Helpful for debugging or reviewing individual results

### Saving Batch Results

**Save Combined Output:**
- Click "Save" to save the merged results as a single file

**Save All Individual Results:**
1. Click the **Save All** button
2. Choose an output folder
3. Each file's result is saved with a "_result" suffix

For example, if you processed `order1.xml` and `order2.xml`, you would get:
- `order1_result.xml`
- `order2_result.xml`

### Understanding the Statistics

After batch processing, you will see statistics showing:

| Statistic | Meaning |
|-----------|---------|
| **Successful** | Number of files processed without errors |
| **Errors** | Number of files that had problems |
| **Total Time** | How long the entire batch took |

If there are errors, check:
- The error message shown for each failed file
- Whether the XML file is well-formed
- Whether your XQuery syntax is correct

---

## Interface Options

### Collapsible File Panel

Click the arrow to collapse the file selection panel and maximize your view of the results.

### Open in Browser

For HTML output, click "Open in Browser" to view the result in your default web browser.

---

## Tips

- Make sure your XML and XSLT files are valid before transformation
- Check the error messages if the transformation fails
- Use the preview to verify your output before saving
- For batch processing, start with a small number of files to test your XQuery
- Use `document-uri($doc)` in your XQuery to identify which file each result came from

---

## Common XQuery Patterns for Batch Processing

### Wrap Results by File

```xquery
for $doc in collection()
return <fileResult>
  <source>{document-uri($doc)}</source>
  <data>{$doc/*/*}</data>
</fileResult>
```

### Filter Files by Content

```xquery
for $doc in collection()
where exists($doc//errorCode)
return document-uri($doc)
```

### Aggregate Data Across Files

```xquery
<report>
  <totalAmount>{sum(collection()//amount)}</totalAmount>
  <averageValue>{avg(collection()//value)}</averageValue>
  <fileCount>{count(collection())}</fileCount>
</report>
```

### Extract and Transform

```xquery
<customers>
{
  for $doc in collection()
  for $customer in $doc//customer
  return <entry>
    <name>{$customer/name/string()}</name>
    <source>{tokenize(document-uri($doc), '/')[last()]}</source>
  </entry>
}
</customers>
```

---

## Troubleshooting Batch Processing

| Problem | Solution |
|---------|----------|
| No output from `collection()` | Make sure files are selected (checkboxes enabled) |
| "Parse error" on a file | Check that the XML file is well-formed |
| Results missing some files | Check the error count - some files may have failed |
| Slow processing | Consider processing fewer files at once for large batches |

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [XSD Validation](xsd-validation-controller.md) | [Home](index.md) | [PDF Generator (FOP)](fop-controller.md) |

**All Pages:** [XML Editor](xml-controller.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-controller.md) | [XSD Validation](xsd-validation-controller.md) | [XSLT](xslt-controller.md) | [FOP/PDF](fop-controller.md) | [Signatures](signature-controller.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
