package org.fxt.freexmltoolkit.service.ai.provider;

/** How an OpenAI-compatible server expects the key: {@code Authorization: Bearer} or Azure's {@code api-key}. */
public enum AuthHeader {
    BEARER, API_KEY;

    public static AuthHeader of(String name) {
        return name != null && name.equalsIgnoreCase(API_KEY.name()) ? API_KEY : BEARER;
    }
}
