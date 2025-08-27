#!/bin/bash

echo "🚀 Starting Enhanced IntelliSense Demo..."
echo ""
echo "This demo showcases the following features:"
echo "• Enhanced Completion Popup with 3-panel layout"
echo "• Fuzzy Search with CamelCase support"
echo "• Type-aware Attribute Value Helpers"  
echo "• XSD Documentation Integration"
echo "• Performance Optimizations"
echo ""

# Build and run the demo
./gradlew run -PmainClass=org.fxt.freexmltoolkit.demo.IntelliSenseDemo --quiet