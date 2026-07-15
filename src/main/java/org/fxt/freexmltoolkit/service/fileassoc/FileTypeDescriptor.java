package org.fxt.freexmltoolkit.service.fileassoc;

import java.util.Set;

/**
 * Platform-independent description of one registrable file type.
 *
 * @param id          stable identifier (the {@code UnifiedEditorFileType} name, e.g. "XML")
 * @param description human-readable document description (e.g. "XML Document")
 * @param extensions  file extensions without leading dot (e.g. {@code [xsl, xslt]})
 * @param mimeType    the MIME type used on Linux (e.g. {@code application/xml})
 */
public record FileTypeDescriptor(String id, String description, Set<String> extensions, String mimeType) {
}
