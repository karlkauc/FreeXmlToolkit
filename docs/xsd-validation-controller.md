# XSD Validation

This tool is dedicated to validating your XML files against an XSD (XML Schema Definition). It helps you ensure that your XML documents are not only well-formed but also conform to a specific, predefined structure and set of rules.

## How it Works

You provide an XML file and its corresponding XSD schema. The tool then compares the XML against the rules defined in the XSD and reports whether it is valid or not.

## Key Features

![Screenshot of XSD Validation Controller](img/xsd-validation-controller.png)

### File Input
-   **Load XML and XSD Files:** You can load your files by clicking buttons to open a file dialog or by simply dragging and dropping them from your computer onto the designated areas.
-   **Automatic Schema Detection:** If your XML file contains a reference to its schema, you can use the "Autodetect" feature. The tool will automatically find, load, and use the correct schema for validation, saving you the step of selecting it manually.

### Validation and Results
-   **Instant Validation:** The validation process runs as soon as both an XML and an XSD file are provided.
-   **Clear Status Updates:** A prominent status bar at the top provides immediate feedback:
    -   **Success:** A green bar with a checkmark appears if the XML is valid.
    -   **Error:** A red bar with an error icon appears if any validation errors are found.
    -   **Ready:** A neutral message is shown when the tool is ready for a new validation.
-   **Detailed Error Reporting:** If the validation fails, a detailed list of all errors is displayed. For each error, you get:
    -   The error message describing the problem.
    -   The exact line and column number where the error occurred.
    -   A snippet of the XML code around the error, making it easy to locate and fix.

### Reporting
-   **Export to Excel:** You can export the complete list of validation errors to an Excel spreadsheet. This is useful for sharing reports, tracking issues, or performing further analysis.

### User Interface
-   **Clear Results:** A dedicated button allows you to clear all current results, file selections, and error messages, giving you a clean slate to start a new validation.

---

[Previous: XSD Tools](xsd-controller.md) | [Home](index.md) | [Next: XSLT Transformation](xslt-controller.md)
