/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileExplorerTreeItem refresh functionality.
 *
 * <p>Note: These tests require a display and do not work in headless environments.
 * They are disabled for CI builds but can be run locally with a display.</p>
 */
@Disabled("Requires JavaFX display - does not work in headless CI environments")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileExplorerTreeItemTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    void initJavaFX() throws InterruptedException {
        // Initialize JavaFX toolkit for TreeItem usage
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // JavaFX already initialized
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX initialization timed out");
    }

    @Test
    void testRefreshDetectsNewFiles() throws IOException {
        // Create initial directory structure
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);
        Files.createFile(subDir.resolve("file1.xml"));

        // Create tree item and load children
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(subDir, List.of("xml"));

        // Access children to trigger lazy loading
        int initialChildCount = treeItem.getChildren().size();
        assertEquals(1, initialChildCount, "Should have 1 initial child");

        // Add a new file
        Files.createFile(subDir.resolve("file2.xml"));

        // Refresh the tree item
        treeItem.refresh();

        // Verify new file is detected
        int newChildCount = treeItem.getChildren().size();
        assertEquals(2, newChildCount, "Should have 2 children after refresh");
    }

    @Test
    void testRefreshDetectsDeletedFiles() throws IOException {
        // Create initial directory structure
        Path subDir = tempDir.resolve("subdir2");
        Files.createDirectory(subDir);
        Path file1 = Files.createFile(subDir.resolve("file1.xml"));
        Files.createFile(subDir.resolve("file2.xml"));

        // Create tree item and load children
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(subDir, List.of("xml"));

        // Access children to trigger lazy loading
        int initialChildCount = treeItem.getChildren().size();
        assertEquals(2, initialChildCount, "Should have 2 initial children");

        // Delete a file
        Files.delete(file1);

        // Refresh the tree item
        treeItem.refresh();

        // Verify deleted file is removed
        int newChildCount = treeItem.getChildren().size();
        assertEquals(1, newChildCount, "Should have 1 child after refresh");
    }

    @Test
    void testRefreshRespectsFileFilter() throws IOException {
        // Create initial directory structure
        Path subDir = tempDir.resolve("subdir3");
        Files.createDirectory(subDir);
        Files.createFile(subDir.resolve("file1.xml"));
        Files.createFile(subDir.resolve("file2.txt")); // Should be filtered out

        // Create tree item with XML filter
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(subDir, List.of("xml"));

        // Access children to trigger lazy loading
        int childCount = treeItem.getChildren().size();
        assertEquals(1, childCount, "Should only show XML files");

        // Add an XSLT file (should be filtered out)
        Files.createFile(subDir.resolve("style.xslt"));

        // Add another XML file
        Files.createFile(subDir.resolve("file3.xml"));

        // Refresh
        treeItem.refresh();

        // Should now have 2 children (only XML files)
        int newChildCount = treeItem.getChildren().size();
        assertEquals(2, newChildCount, "Should have 2 XML files after refresh");
    }

    @Test
    void testRefreshHandlesEmptyDirectory() throws IOException {
        // Create empty directory
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectory(emptyDir);

        // Create tree item
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(emptyDir);

        // Access children
        assertTrue(treeItem.getChildren().isEmpty(), "Empty directory should have no children");

        // Refresh should not throw
        assertDoesNotThrow(() -> treeItem.refresh());
    }

    @Test
    void testForceRefresh() throws IOException {
        // Create directory with subdirectories
        Path subDir = tempDir.resolve("subdir4");
        Files.createDirectory(subDir);
        Path nestedDir = subDir.resolve("nested");
        Files.createDirectory(nestedDir);
        Files.createFile(nestedDir.resolve("file.xml"));

        // Create tree item
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(subDir, List.of("xml"));

        // Load children
        assertEquals(1, treeItem.getChildren().size(), "Should have nested directory");

        // Add file to nested directory
        Files.createFile(nestedDir.resolve("file2.xml"));

        // Force refresh should work without throwing
        assertDoesNotThrow(() -> treeItem.forceRefresh());
    }

    @Test
    void testSubdirectoryCount() throws IOException {
        // Create directory with mixed content
        Path subDir = tempDir.resolve("subdir5");
        Files.createDirectory(subDir);
        Files.createDirectory(subDir.resolve("dir1"));
        Files.createDirectory(subDir.resolve("dir2"));
        Files.createFile(subDir.resolve("file.xml"));

        // Create tree item
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(subDir);

        // Should count 2 subdirectories
        assertEquals(2, treeItem.getSubdirectoryCount(), "Should have 2 subdirectories");
    }

    @Test
    void testRefreshOnlyUpdatesWhenChangesExist() throws IOException {
        // Create directory
        Path subDir = tempDir.resolve("subdir6");
        Files.createDirectory(subDir);
        Files.createFile(subDir.resolve("file.xml"));

        // Create tree item
        FileExplorerTreeItem treeItem = new FileExplorerTreeItem(subDir, List.of("xml"));

        // Load children
        treeItem.getChildren();

        // Refresh without changes should not throw
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                treeItem.refresh();
            }
        });
    }
}
