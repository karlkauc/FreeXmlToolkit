package org.fxt.freexmltoolkit.service.grammar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.thaiopensource.resolver.Identifier;
import com.thaiopensource.resolver.Input;
import com.thaiopensource.resolver.Resolver;
import com.thaiopensource.resolver.ResolverException;

/**
 * Jing's and Trang's resolver ({@code include}, {@code externalRef}, DTD modules) over
 * {@link GrammarResolver}: catalogs and Schema Library, local files, http(s) through the schema cache.
 */
final class SecureGrammarResolver implements Resolver {

    @Override
    public void resolve(Identifier id, Input input) throws IOException, ResolverException {
        if (input.isResolved()) {
            return;
        }
        String uri = GrammarResolver.absolute(id.getUriReference(), id.getBase()).toString();
        Path file = GrammarResolver.resolveToFile(null, id.getUriReference(), id.getBase());
        input.setUri(uri);
        input.setByteStream(Files.newInputStream(file));
    }

    @Override
    public void open(Input input) throws IOException, ResolverException {
        if (input.isOpen() || input.getUri() == null) {
            return;
        }
        Path file = GrammarResolver.resolveToFile(null, input.getUri(), null);
        input.setByteStream(Files.newInputStream(file));
    }
}
