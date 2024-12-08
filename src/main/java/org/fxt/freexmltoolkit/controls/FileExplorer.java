package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class FileExplorer extends VBox {

    private final static Logger logger = LogManager.getLogger(FileExplorer.class);
    TreeTableView<Path> fileTreeView;

    public FileExplorer() {
        logger.debug("FileExplorer()");
        init();
    }

    private void init() {
        try {
            this.setPadding(new Insets(10, 10, 10, 10));
            this.setSpacing(10);

            fileTreeView = new TreeTableView<>();
            TreeTableColumn<Path, String> fileName = new TreeTableColumn<>("File Name");
            TreeTableColumn<Path, String> fileExtension = new TreeTableColumn<>("File Extension");
            TreeTableColumn<Path, String> fileSize = new TreeTableColumn<>("File Size");
            TreeTableColumn<Path, String> fileDate = new TreeTableColumn<>("File Date");

            fileName.setCellValueFactory(p -> {
                if (p.getValue() != null && p.getValue().getValue() != null) {
                    return new SimpleStringProperty(p.getValue().getValue().toFile().getName());
                }
                return new SimpleStringProperty("");
            });
            fileExtension.setCellValueFactory(p -> {
                if (p.getValue() != null && p.getValue().getValue() != null && p.getValue().getValue().getFileName() != null) {
                    var file = p.getValue().getValue();
                    logger.debug("Is directory: {}, {}", file.getFileName().toFile().getAbsoluteFile(), Files.isDirectory(file));

                    if (Files.isDirectory(file)) {
                        return new SimpleStringProperty("");
                    }
                    return new SimpleStringProperty(FilenameUtils.getExtension(p.getValue().getValue().getFileName().toString()));
                } else {
                    return new SimpleStringProperty("");
                }
            });
            fileSize.setCellValueFactory(p -> {
                if (p.getValue().getValue().toFile().isFile()) {
                    return new SimpleStringProperty(FileUtils.byteCountToDisplaySize(p.getValue().getValue().toFile().length()));
                }
                return new SimpleStringProperty("");
            });
            fileDate.setCellValueFactory(p -> {
                final Path file = p.getValue().getValue();
                try {
                    final BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                    return new SimpleStringProperty(attr.lastModifiedTime().toString());
                } catch (IOException e) {
                    // logger.error(e.getMessage());
                }

                return new SimpleStringProperty("");
            });

            fileTreeView.getColumns().add(fileName);
            fileTreeView.getColumns().add(fileExtension);
            fileTreeView.getColumns().add(fileSize);
            fileTreeView.getColumns().add(fileDate);

            String hostName = "computer";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {
            }

            FileExplorerTreeItem<Path> root = new FileExplorerTreeItem<>(new File(hostName).toPath());

            for (File file : File.listRoots()) {
                logger.debug("drinnen: {}", file.getName());
                root.getChildren().add(new FileExplorerTreeItem<>(file.toPath()));
                logger.debug("nach treeNode");
            }

            fileTreeView.setShowRoot(true);
            fileTreeView.setRoot(root);

            fileTreeView.getSelectionModel()
                    .selectedItemProperty()
                    .addListener((observable, oldValue, newValue) -> {
                        if (newValue.getValue().toFile().isFile()) {
                            System.out.println("LADEN = " + newValue.getValue().toFile().getAbsolutePath());
                        }
                        if (newValue.getValue().toFile().isDirectory()) {
                            if (newValue.isExpanded()) {
                                newValue.getChildren().clear();
                            } else {
                                try (var walk = Files.walk(newValue.getValue(), 1)) {
                                    List<Path> result = walk
                                            .filter(f -> f.toFile().isFile() || f.toFile().isDirectory())
                                            .toList();
                                    for (Path file : result) {
                                        newValue.getChildren().add(new FileExplorerTreeItem<>(file));
                                    }
                                } catch (IOException e) {
                                    // e.printStackTrace();
                                }
                            }
                        }
                        System.out.println("Selected Text : " + newValue.getValue());
                    });

            this.getChildren().addAll(new Label("File browser"), fileTreeView);

            VBox.setVgrow(fileTreeView, Priority.ALWAYS);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }


}
