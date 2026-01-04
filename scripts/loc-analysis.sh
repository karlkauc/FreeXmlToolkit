#!/bin/bash
#
# LOC Analysis Script - Analyzes Java LOC over last 100 commits
# Generates CSV data suitable for visualization
#
# Usage: ./scripts/loc-analysis.sh
#

set -e

# Configuration
OUTPUT_DIR="build/reports/loc"
CSV_FILE="$OUTPUT_DIR/data.csv"
COMMIT_LIMIT=100

# Get project root directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

# Verify we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo "❌ Error: Not in a git repository"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Get total commit count for validation
TOTAL_COMMITS=$(git rev-list --count HEAD)
if [ "$TOTAL_COMMITS" -lt "$COMMIT_LIMIT" ]; then
    echo "⚠️  Warning: Repository has only $TOTAL_COMMITS commits (requested $COMMIT_LIMIT)"
    COMMIT_LIMIT=$TOTAL_COMMITS
fi

echo "📊 Analyzing Java LOC for last $COMMIT_LIMIT commits..."
echo ""

# Initialize CSV file with header
echo "commit_index,commit_hash,date,author,total_loc,added,deleted,net_change,message" > "$CSV_FILE"

# Process each commit
COMMIT_INDEX=0
while IFS= read -r COMMIT_HASH; do
    COMMIT_INDEX=$((COMMIT_INDEX + 1))

    # Calculate progress percentage
    PROGRESS=$((COMMIT_INDEX * 100 / COMMIT_LIMIT))

    # Show progress indicator
    printf "\r[%-50s] %d%% (%d/%d commits)" "$(printf '#%.0s' {1..$(($PROGRESS / 2))})" "$PROGRESS" "$COMMIT_INDEX" "$COMMIT_LIMIT"

    # Get commit metadata
    COMMIT_DATE=$(git log -1 --format='%ai' "$COMMIT_HASH")
    COMMIT_AUTHOR=$(git log -1 --format='%an' "$COMMIT_HASH" | tr ',' ' ')  # Remove commas from author name
    COMMIT_MESSAGE=$(git log -1 --format='%s' "$COMMIT_HASH" | sed 's/"/""/g')  # Escape quotes

    # Count total Java LOC at this commit
    TOTAL_LOC=0
    while IFS= read -r JAVA_FILE; do
        # Get line count for this file at this commit
        FILE_LOC=$(git show "$COMMIT_HASH:$JAVA_FILE" 2>/dev/null | wc -l)
        TOTAL_LOC=$((TOTAL_LOC + FILE_LOC))
    done < <(git ls-tree -r "$COMMIT_HASH" --name-only | grep '\.java$')

    # Calculate additions and deletions for this commit
    ADDED=0
    DELETED=0
    while IFS=$'\t' read -r ADD DEL FILE; do
        if [[ "$FILE" == *.java ]]; then
            ADDED=$((ADDED + ADD))
            DELETED=$((DELETED + DEL))
        fi
    done < <(git show --numstat --pretty='' "$COMMIT_HASH" 2>/dev/null)

    # Calculate net change
    NET_CHANGE=$((ADDED - DELETED))

    # Format net change with sign prefix
    if [ "$NET_CHANGE" -ge 0 ]; then
        NET_CHANGE_STR="+$NET_CHANGE"
    else
        NET_CHANGE_STR="$NET_CHANGE"
    fi

    # Append CSV row (CSV format: index,hash,date,author,total_loc,added,deleted,net_change,message)
    echo "$COMMIT_INDEX,$COMMIT_HASH,$COMMIT_DATE,$COMMIT_AUTHOR,$TOTAL_LOC,$ADDED,$DELETED,$NET_CHANGE_STR,\"$COMMIT_MESSAGE\"" >> "$CSV_FILE"

done < <(git log -"$COMMIT_LIMIT" --pretty=format:'%H')

# Final progress update
printf "\r[%-50s] 100%% (%d/%d commits)\n" "$(printf '#%.0s' {1..50})" "$COMMIT_INDEX" "$COMMIT_LIMIT"
echo ""

# Verify CSV was created
if [ ! -f "$CSV_FILE" ]; then
    echo "❌ Error: Failed to create CSV file"
    exit 1
fi

# Get statistics from CSV
LINE_COUNT=$(wc -l < "$CSV_FILE")
DATA_ROWS=$((LINE_COUNT - 1))

if [ "$DATA_ROWS" -eq 0 ]; then
    echo "❌ Error: No data was generated"
    exit 1
fi

echo "✅ LOC analysis complete!"
echo "📊 Data file: $CSV_FILE"
echo "📈 Commits analyzed: $DATA_ROWS"
echo ""

# Show summary
FIRST_COMMIT=$(sed -n '2p' "$CSV_FILE" | cut -d',' -f2)
LAST_COMMIT=$(tail -1 "$CSV_FILE" | cut -d',' -f2)
FIRST_LOC=$(sed -n '2p' "$CSV_FILE" | cut -d',' -f5)
LAST_LOC=$(tail -1 "$CSV_FILE" | cut -d',' -f5)
LOC_CHANGE=$((FIRST_LOC - LAST_LOC))

echo "Date range: $(sed -n '2p' "$CSV_FILE" | cut -d',' -f4) to $(tail -1 "$CSV_FILE" | cut -d',' -f4)"
echo "LOC at newest commit: $FIRST_LOC"
echo "LOC at oldest commit: $LAST_LOC"
echo "Net change: $([ "$LOC_CHANGE" -ge 0 ] && echo "+$LOC_CHANGE" || echo "$LOC_CHANGE")"
echo ""
