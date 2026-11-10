package org.fxt.freexmltoolkit.service.ai.agent;

import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;

/** Checks a candidate the model produced with the application's own engines. Never throws for a bad candidate. */
public interface Verifier {

    Verification verify(String candidate, CancelToken cancel);
}
