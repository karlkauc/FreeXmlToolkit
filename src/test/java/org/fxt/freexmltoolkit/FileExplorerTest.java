package org.fxt.freexmltoolkit;

import org.junit.jupiter.api.Test;

import javax.swing.filechooser.FileSystemView;
import java.io.File;

public class FileExplorerTest {

    @Test
    public void test() {
        System.out.println("File system roots returned by FileSystemView.getFileSystemView():");
        FileSystemView fsv = FileSystemView.getFileSystemView();
        File[] roots = fsv.getRoots();
        for (File root : roots) {
            System.out.println("Root: " + root);
        }

        System.out.println("Home directory: " + fsv.getHomeDirectory());

        System.out.println("File system roots returned by File.listRoots():");
        File[] f = File.listRoots();
        for (File file : f) {
            System.out.println("Drive: " + file);
            System.out.println("Display name: " + fsv.getSystemDisplayName(file));
            System.out.println("Is drive: " + fsv.isDrive(file));
            System.out.println("Is floppy: " + fsv.isFloppyDrive(file));
            System.out.println("Readable: " + file.canRead());
            System.out.println("Writable: " + file.canWrite());
            System.out.println("Total space: " + file.getTotalSpace());
            System.out.println("Usable space: " + file.getUsableSpace());
        }
    }
}
