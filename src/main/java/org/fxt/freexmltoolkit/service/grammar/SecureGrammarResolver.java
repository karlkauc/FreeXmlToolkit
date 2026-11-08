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
 * {@link GrammarResolver}: catalogs and Schema Library, local files, http(s) through the schema cache,
 * all within one {@link GrammarResolver.Session} per parse.
 */
final class SecureGrammarResolver implements Resolver {

    private final GrammarResolver.Session session;

    /** @param rootUri the grammar the parse starts from (see {@link GrammarResolver.Session}) */
    SecureGrammarResolver(String rootUri) {
        this.session = new GrammarResolver.Session(rootUri);
    }

    @Override
    public void resolve(Identifier id, Input input) throws IOException, ResolverException {
        if (input.isResolved()) {
            return;
        }
        String uri = GrammarResolver.absolute(id.getUriReference(), id.getBase()).toString();
        Path file = GrammarResolver.resolveToFile(session, false, null, id.getUriReference(), id.getBase());
        input.setUri(uri);
        input.setByteStream(Files.newInputStream(file));
    }

    @Override
    public void open(Input input) throws IOException, ResolverException {
        if (input.isOpen() || input.getUri() == null) {
            return;
        }
        Path file = GrammarResolver.resolveToFile(session, false, null, input.getUri(), null);
        input.setByteStream(Files.newInputStream(file));
    }
}
