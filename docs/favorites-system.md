# Favorites System

The FreeXmlToolkit features a comprehensive favorites system that allows you to save, organize, and quickly access
frequently used files across all editors in the application.

## Overview

The favorites system is designed to improve your workflow by providing quick access to the files you use most often.
Whether you're working with XML documents, XSD schemas, or Schematron rules, you can save any file as a favorite and
access it from any editor in the application.

## Key Features

### Universal Access

- **Cross-Editor Compatibility**: Save files from any editor and access them from any other editor
- **File Type Intelligence**: Automatically detects file types (XML, XSD, XSLT, Schematron) and handles them
  appropriately
- **One-Click Loading**: Quick access to your most frequently used files with a single click

### Smart Organization

- **Custom Categories**: Organize favorites into custom folders (e.g., "Project Files", "Templates", "Schemas", "Test
  Data")
- **Folder Management**: Create, rename, and delete categories to match your workflow
- **Mixed File Types**: Store different file types in the same category for project-based organization

### Rich Metadata

- **Custom Names**: Give favorites descriptive names that are easy to remember
- **Descriptions**: Add detailed descriptions to help identify file purposes
- **Visual Icons**: File type icons help you quickly identify different kinds of files
- **Timestamps**: Track when favorites were added and last accessed

## How to Use Favorites

### Adding Files to Favorites

1. **Open a File**: Load any XML, XSD, or Schematron file in any editor
2. **Click Add to Favorites**: Look for the "★ Add" button in the editor toolbar
3. **Fill Out the Form**:
    - **Name**: Enter a descriptive name (auto-filled with the filename)
    - **Category**: Choose an existing category or create a new one
    - **Description**: Add optional details about the file's purpose

### Accessing Favorites

1. **Click the Favorites Dropdown**: Look for the "Favorites" button in any editor
2. **Browse Categories**: If you have multiple categories, they'll appear as submenus
3. **Select a File**: Click on any favorite to load it immediately
4. **File Validation**: If a file no longer exists, you'll be offered the option to remove it from favorites

### Managing Favorites

The favorites dropdown menu includes management options:

- **Manage Favorites**: Access the full favorites management interface (coming soon)
- **Remove Non-Existent Files**: Clean up favorites that point to files that have been moved or deleted

## Editor Integration

### XML Ultimate Editor

- **Toolbar Integration**: Favorites buttons are prominently displayed in the main toolbar
- **File Type Support**: Handles XML, XSD, XSLT, and other XML-related file types
- **Smart Categorization**: Defaults to "General" category for mixed file types

### XSD Controller

- **Info Panel Integration**: Favorites buttons appear in the text tab info panel
- **Schema-Focused**: Defaults to "XSD Schemas" category for schema files
- **Type Filtering**: Shows only XSD files in the favorites dropdown for focused workflow

### Schematron Controller

- **Toolbar Integration**: Favorites buttons integrated into the main toolbar
- **Rule-Focused**: Defaults to "Schematron Rules" category for business rules
- **Type Filtering**: Shows only Schematron files for focused validation workflow

## Data Storage

### Local Persistence

- **Storage Location**: Favorites are saved in `~/.freexmltoolkit/favorites.json`
- **Cross-Platform**: Works consistently across Windows, macOS, and Linux
- **JSON Format**: Human-readable format that's easy to backup and restore

### Data Structure

Each favorite contains:

- Unique identifier
- Display name
- File path
- Category/folder assignment
- File type detection
- Creation and last-accessed timestamps
- Optional description

## Tips and Best Practices

### Organization Strategies

- **Project-Based**: Create categories for different projects or clients
- **Type-Based**: Organize by file type ("Schemas", "Templates", "Test Data")
- **Frequency-Based**: Use categories like "Daily Use", "Templates", "Archive"

### Naming Conventions

- Use descriptive names that make sense to you
- Include project or purpose information in the name
- Keep names concise but informative

### Maintenance

- Regularly use the "Remove Non-Existent Files" feature to keep favorites clean
- Review and reorganize categories as your projects evolve
- Add descriptions to complex files to remember their purpose later

## Technical Details

### Supported File Types

- **XML**: Generic XML documents (`.xml`)
- **XSD**: XML Schema Definition files (`.xsd`)
- **Schematron**: Business rule validation files (`.sch`)
- **XSLT**: Transformation stylesheets (`.xsl`, `.xslt`)
- **Other**: Any other file type can be saved but may have limited editor support

### Error Handling

- **Missing Files**: Graceful handling when favorite files are moved or deleted
- **Permission Issues**: Clear error messages for files that cannot be accessed
- **Corruption Recovery**: Automatic fallback if favorites data becomes corrupted

### Performance

- **Lazy Loading**: Favorites menu is populated on-demand for fast startup
- **Efficient Storage**: Minimal disk space usage with JSON serialization
- **Memory Efficient**: Only loads favorite metadata, not file contents

---

[← Back to Index](index.md) | [Next: XML Editor Features](xml-editor-features.md)