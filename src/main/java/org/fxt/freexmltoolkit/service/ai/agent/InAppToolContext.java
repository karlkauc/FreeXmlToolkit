package org.fxt.freexmltoolkit.service.ai.agent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;
import org.fxt.freexmltoolkit.service.ai.tool.WorkspaceAccess;

/**
 * The tool context of the in-app assistant: the folders the tools may read are those of the documents involved (and
 * the open project), falling back to the user's home folder's temporary area when nothing is saved yet — never the
 * whole disk.
 */
public final class InAppToolContext {

    private InAppToolContext() {
    }

    /** A context whose roots are the folders of {@code files} (and {@code extraFolders}); limits from the settings. */
    public static ToolContext forFiles(Collection<File> files, Collection<Path> extraFolders, ToolLimits limits) {
        Set<Path> roots = new LinkedHashSet<>();
        for (File file : files) {
            if (file != null && file.getParentFile() != null && file.getParentFile().isDirectory()) {
                roots.add(file.getParentFile().toPath().toAbsolutePath().normalize());
            }
        }
        for (Path folder : extraFolders) {
            if (folder != null && Files.isDirectory(folder)) {
                roots.add(folder.toAbsolutePath().normalize());
            }
        }
        if (roots.isEmpty()) {
            roots.add(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath());
        }
        return ToolContext.of(new WorkspaceAccess(new ArrayList<>(roots)), limits);
    }

    /** The roots of a context as files, for engine calls that take folders. */
    public static List<Path> roots(ToolContext context) {
        return context.files().roots();
    }
}
