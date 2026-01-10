#!/bin/bash
#
# FreeXMLToolkit Release Script
#
# This script ensures the correct order of operations for creating a release:
# 1. Update version in build.gradle.kts
# 2. Update version in UpdateCheckServiceImpl.java
# 3. Commit and push changes
# 4. Create and push tag
# 5. Create GitHub release (triggers build workflow)
#
# Usage: ./release.sh <version>
# Example: ./release.sh 1.2.4
#

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Files to update
BUILD_GRADLE="build.gradle.kts"
UPDATE_SERVICE="src/main/java/org/fxt/freexmltoolkit/service/UpdateCheckServiceImpl.java"

print_step() {
    echo -e "${BLUE}==>${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}!${NC} $1"
}

# Check arguments
VERSION=$1
if [ -z "$VERSION" ]; then
    echo "FreeXMLToolkit Release Script"
    echo ""
    echo "Usage: ./release.sh <version>"
    echo "Example: ./release.sh 1.2.4"
    echo ""
    echo "This script will:"
    echo "  1. Update version in build.gradle.kts"
    echo "  2. Update DEFAULT_VERSION in UpdateCheckServiceImpl.java"
    echo "  3. Commit and push the version changes"
    echo "  4. Create and push git tag v<version>"
    echo "  5. Create GitHub release (triggers build workflow)"
    exit 1
fi

# Validate version format (X.Y.Z)
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    print_error "Invalid version format: $VERSION"
    echo "Version must be in format X.Y.Z (e.g., 1.2.3)"
    exit 1
fi

TAG="v$VERSION"

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║         FreeXMLToolkit Release Script                      ║"
echo "╠════════════════════════════════════════════════════════════╣"
echo "║  Version: $VERSION                                            ║"
echo "║  Tag:     $TAG                                              ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Check if we're in the right directory
if [ ! -f "$BUILD_GRADLE" ]; then
    print_error "build.gradle.kts not found. Are you in the project root?"
    exit 1
fi

if [ ! -f "$UPDATE_SERVICE" ]; then
    print_error "UpdateCheckServiceImpl.java not found."
    exit 1
fi

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    print_error "GitHub CLI (gh) is not installed."
    echo "Install it from: https://cli.github.com/"
    exit 1
fi

# Check if authenticated with GitHub
if ! gh auth status &> /dev/null; then
    print_error "Not authenticated with GitHub CLI."
    echo "Run: gh auth login"
    exit 1
fi

# Check for uncommitted changes
if [ -n "$(git status --porcelain)" ]; then
    print_warning "You have uncommitted changes:"
    git status --short
    echo ""
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 1
    fi
fi

# Check if tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
    print_error "Tag $TAG already exists!"
    echo "To delete it: git tag -d $TAG && git push origin --delete $TAG"
    exit 1
fi

# Get current version
CURRENT_VERSION=$(grep "^version = " "$BUILD_GRADLE" | sed 's/version = "\(.*\)"/\1/')
print_step "Current version: $CURRENT_VERSION"
print_step "New version: $VERSION"
echo ""

# Confirm
read -p "Proceed with release? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 1
fi

echo ""

# Step 1: Update build.gradle.kts
print_step "Updating version in $BUILD_GRADLE..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/^version = \".*\"/version = \"$VERSION\"/" "$BUILD_GRADLE"
else
    # Linux
    sed -i "s/^version = \".*\"/version = \"$VERSION\"/" "$BUILD_GRADLE"
fi
print_success "Updated $BUILD_GRADLE"

# Step 2: Update UpdateCheckServiceImpl.java
print_step "Updating DEFAULT_VERSION in $UPDATE_SERVICE..."
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    sed -i '' "s/private static final String DEFAULT_VERSION = \".*\"/private static final String DEFAULT_VERSION = \"$VERSION\"/" "$UPDATE_SERVICE"
else
    # Linux
    sed -i "s/private static final String DEFAULT_VERSION = \".*\"/private static final String DEFAULT_VERSION = \"$VERSION\"/" "$UPDATE_SERVICE"
fi
print_success "Updated $UPDATE_SERVICE"

# Step 3: Verify changes
print_step "Verifying changes..."
echo ""
echo "  build.gradle.kts:"
grep "^version = " "$BUILD_GRADLE" | sed 's/^/    /'
echo ""
echo "  UpdateCheckServiceImpl.java:"
grep "DEFAULT_VERSION = " "$UPDATE_SERVICE" | sed 's/^/    /'
echo ""

# Step 4: Commit changes
print_step "Committing version changes..."
git add "$BUILD_GRADLE" "$UPDATE_SERVICE"
git commit -m "chore: Bump version to $VERSION

Co-Authored-By: Release Script <noreply@freexmltoolkit.org>"
print_success "Changes committed"

# Step 5: Push changes
print_step "Pushing changes to remote..."
git push
print_success "Changes pushed"

# Step 6: Create tag
print_step "Creating tag $TAG..."
git tag -a "$TAG" -m "Version $VERSION"
print_success "Tag created"

# Step 7: Push tag
print_step "Pushing tag to remote..."
git push origin "$TAG"
print_success "Tag pushed"

# Step 8: Create GitHub release
print_step "Creating GitHub release..."
gh release create "$TAG" \
    --title "Version $VERSION" \
    --generate-notes

print_success "GitHub release created"

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                    Release Complete!                       ║"
echo "╠════════════════════════════════════════════════════════════╣"
echo "║  The GitHub Actions workflow is now building packages.     ║"
echo "║                                                            ║"
echo "║  Monitor progress:                                         ║"
echo "║    gh run list --limit 5                                   ║"
echo "║    gh run watch                                            ║"
echo "║                                                            ║"
echo "║  View release:                                             ║"
echo "║    gh release view $TAG                                    ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
