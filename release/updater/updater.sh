#!/bin/bash
# FreeXmlToolkit Updater Script for macOS/Linux
# This script is launched by the application after downloading an update.
# It waits for the application to exit, then copies the new files and restarts.

set -e

APP_DIR="$1"
UPDATE_DIR="$2"
LAUNCHER="$3"

echo "========================================"
echo "FreeXmlToolkit Updater"
echo "========================================"
echo ""
echo "Application directory: $APP_DIR"
echo "Update directory: $UPDATE_DIR"
echo "Launcher: $LAUNCHER"
echo ""

# Validate parameters
if [ -z "$APP_DIR" ]; then
    echo "Error: Application directory not specified"
    exit 1
fi

if [ -z "$UPDATE_DIR" ]; then
    echo "Error: Update directory not specified"
    exit 1
fi

if [ -z "$LAUNCHER" ]; then
    echo "Error: Launcher path not specified"
    exit 1
fi

echo "Waiting for application to exit..."
echo ""

# Wait for the application to exit
WAIT_COUNT=0
MAX_WAIT=60  # Maximum 60 seconds

while pgrep -f "FreeXmlToolkit" > /dev/null 2>&1; do
    WAIT_COUNT=$((WAIT_COUNT + 1))
    if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
        echo "Warning: Application did not exit within $MAX_WAIT seconds"
        echo "Attempting to continue anyway..."
        break
    fi
    echo "Application is still running, waiting... ($WAIT_COUNT/$MAX_WAIT)"
    sleep 1
done

# Additional wait to ensure all file handles are released
sleep 2

echo "Application has exited. Starting update installation..."
echo ""

# Find the extracted application folder
UPDATE_APP_DIR=""

# First, look for FreeXmlToolkit directory
if [ -d "$UPDATE_DIR/FreeXmlToolkit" ]; then
    UPDATE_APP_DIR="$UPDATE_DIR/FreeXmlToolkit"
    echo "Found update in: $UPDATE_APP_DIR"
fi

# If not found, search for it
if [ -z "$UPDATE_APP_DIR" ]; then
    UPDATE_APP_DIR=$(find "$UPDATE_DIR" -maxdepth 2 -type d -name "FreeXmlToolkit" 2>/dev/null | head -1)
    if [ -n "$UPDATE_APP_DIR" ]; then
        echo "Found update in: $UPDATE_APP_DIR"
    fi
fi

# If still not found, look for the launcher executable
if [ -z "$UPDATE_APP_DIR" ]; then
    LAUNCHER_PATH=$(find "$UPDATE_DIR" -maxdepth 3 -type f -name "FreeXmlToolkit" ! -name "*.exe" 2>/dev/null | head -1)
    if [ -n "$LAUNCHER_PATH" ]; then
        UPDATE_APP_DIR=$(dirname "$LAUNCHER_PATH")
        # If it's in a bin directory, go up one level
        if [ "$(basename "$UPDATE_APP_DIR")" = "bin" ]; then
            UPDATE_APP_DIR=$(dirname "$UPDATE_APP_DIR")
        fi
        echo "Found update in: $UPDATE_APP_DIR"
    fi
fi

if [ -z "$UPDATE_APP_DIR" ] || [ ! -d "$UPDATE_APP_DIR" ]; then
    echo "Error: Could not find update files"
    echo "Contents of update directory:"
    ls -la "$UPDATE_DIR"
    exit 1
fi

echo ""
echo "Copying new files..."

# Copy files, preserving permissions
if [ "$(uname)" = "Darwin" ]; then
    # macOS
    cp -Rf "$UPDATE_APP_DIR"/* "$APP_DIR/" 2>/dev/null || {
        # Try with parent directory if direct copy fails
        cp -Rf "$UPDATE_APP_DIR"/../* "$APP_DIR/" 2>/dev/null || {
            echo "Error: Failed to copy files!"
            exit 1
        }
    }
else
    # Linux
    cp -rf "$UPDATE_APP_DIR"/* "$APP_DIR/" 2>/dev/null || {
        cp -rf "$UPDATE_APP_DIR"/../* "$APP_DIR/" 2>/dev/null || {
            echo "Error: Failed to copy files!"
            exit 1
        }
    }
fi

echo "Files copied successfully."
echo ""

# Make the launcher executable
if [ -f "$LAUNCHER" ]; then
    chmod +x "$LAUNCHER"
fi

# Make all scripts and executables in bin directory executable
if [ -d "$APP_DIR/bin" ]; then
    find "$APP_DIR/bin" -type f -exec chmod +x {} \; 2>/dev/null
fi
find "$APP_DIR" -type f -name "*.sh" -exec chmod +x {} \; 2>/dev/null

# Clean up the update directory
echo "Cleaning up temporary files..."
rm -rf "$UPDATE_DIR" 2>/dev/null

echo ""
echo "Update completed successfully!"
echo "Starting the updated application..."
echo ""

# Small delay before starting
sleep 1

# Start the updated application
# Use nohup to ensure the app continues running after this script exits
nohup "$LAUNCHER" > /dev/null 2>&1 &

exit 0
