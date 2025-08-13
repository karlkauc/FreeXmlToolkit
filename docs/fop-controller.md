# PDF Generator (FOP)

This section of the application allows you to create PDF documents from your XML files. It uses the Apache FOP (Formatting Objects Processor) engine to transform XML data into a professional-looking PDF.

## How it Works

The process requires two main files:

1.  **XML File:** This is your data file. It contains the information you want to present in the PDF (e.g., a report, an invoice, or a list of items).
2.  **XSL-FO Stylesheet:** This is a special stylesheet that acts as a template. It defines how the data from your XML file should be formatted in the PDF—specifying layouts, fonts, colors, and more.

The tool takes your XML data, applies the rules from the XSL-FO stylesheet, and generates a PDF document as the output.

## Key Features

![Screenshot of FOP Controller](img/fop-pdf.png)

### File Input
- **Select XML and XSL Files:** You can click buttons to open a file dialog and choose your XML data file and your XSL-FO stylesheet.
- **Drag and Drop:** For convenience, you can also drag and drop your XML and XSL files directly from your computer onto the designated fields.

### PDF Output
- **Choose Output Location:** You can specify exactly where you want to save the generated PDF file and what to name it.
- **Set PDF Properties:** Before creating the PDF, you can add important metadata, including:
    - **Author:** The name of the person creating the document.
    - **Title:** The title of the document.
    - **Subject:** A short description of the document's content.
    - **Keywords:** Words that can help in searching for the document.

### Conversion and Preview
- **Start Conversion:** A dedicated button kicks off the process to generate the PDF. A progress indicator will show you that the tool is working.
- **Built-in PDF Viewer:** Once the PDF is created, it is automatically displayed within the application. You can scroll through the pages to review the final document without needing to open an external PDF reader.
- **Error Handling:** The tool checks to make sure you have selected all the necessary files before it starts. If anything is missing, it will notify you so you can fix it.

---

[Previous: XSLT Transformation](xslt-controller.md) | [Home](index.md) | [Next: XML Signature](signature-controller.md)
