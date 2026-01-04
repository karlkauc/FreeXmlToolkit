#!/bin/bash
#
# Assembly Script - Injects CSV data into HTML template
# Generates the final interactive report
#
# Usage: ./scripts/assemble-loc-report.sh
#

set -e

# Configuration
OUTPUT_DIR="build/reports/loc"
CSV_FILE="$OUTPUT_DIR/data.csv"
TEMPLATE_FILE="scripts/loc-report-template.html"
OUTPUT_FILE="$OUTPUT_DIR/index.html"
TEMP_REPORT="$OUTPUT_DIR/index.tmp"

# Verify files exist
if [ ! -f "$CSV_FILE" ]; then
    echo "❌ Error: CSV file not found: $CSV_FILE"
    exit 1
fi

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "❌ Error: Template file not found: $TEMPLATE_FILE"
    exit 1
fi

echo "📄 Assembling LOC report..."

# Export variables for Python script
export TEMPLATE_FILE CSV_FILE TEMP_REPORT

# Use Python to properly handle the CSV data injection with escaping
python3 << 'PYTHON_EOF'
import os

template_file = os.environ['TEMPLATE_FILE']
csv_file = os.environ['CSV_FILE']
output_file = os.environ['TEMP_REPORT']

# Read template
with open(template_file, 'r') as f:
    template = f.read()

# Read CSV data
with open(csv_file, 'r') as f:
    csv_data = f.read()

# Escape backticks in CSV data (backticks are used as template delimiters in JavaScript)
csv_data_escaped = csv_data.replace('`', '\\`')

# Replace placeholder with escaped CSV data
result = template.replace('{{CSV_DATA}}', csv_data_escaped)

# Write output
with open(output_file, 'w') as f:
    f.write(result)
PYTHON_EOF

# Verify temp file was created
if [ ! -f "$TEMP_REPORT" ]; then
    echo "❌ Error: Failed to create temporary report file"
    exit 1
fi

# Move temp file to final location
mv "$TEMP_REPORT" "$OUTPUT_FILE"

# Verify output file was created
if [ ! -f "$OUTPUT_FILE" ]; then
    echo "❌ Error: Failed to create report file"
    exit 1
fi

# Get file size for confirmation
FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)

echo "✅ Report assembled successfully!"
echo "📊 Output file: $OUTPUT_FILE"
echo "📦 File size: $FILE_SIZE"
echo ""
echo "🌐 Open in browser: file://$(pwd)/$OUTPUT_FILE"
