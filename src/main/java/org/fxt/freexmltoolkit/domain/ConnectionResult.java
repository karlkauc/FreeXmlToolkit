package org.fxt.freexmltoolkit.domain;

import java.net.URI;

public record ConnectionResult(
        URI url,
        Integer httpStatus,
        Long duration,
        String[] resultHeader,
        String resultBody
) {
}
