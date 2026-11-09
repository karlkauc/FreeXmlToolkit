package org.fxt.freexmltoolkit.controls.shared.utilities;

import java.util.function.Consumer;

/**
 * Connects the XML editor's context menu ("Encrypt Element…", "Decrypt…") with the Sign &amp;
 * Encrypt page of the shell, which sets the handlers at start. Without a shell (tests, other
 * hosts) the menu items stay hidden.
 */
public final class XmlEncryptionMenuHook {

    private static volatile Consumer<String> encryptElement;
    private static volatile Runnable decrypt;

    private XmlEncryptionMenuHook() {
    }

    /**
     * @param encrypt receives the positional XPath of the element at the caret
     * @param decryptDocument opens decryption for the active document
     */
    public static void install(Consumer<String> encrypt, Runnable decryptDocument) {
        encryptElement = encrypt;
        decrypt = decryptDocument;
    }

    /** @return whether a shell handles the menu items */
    public static boolean isInstalled() {
        return encryptElement != null;
    }

    static void encryptElement(String xpath) {
        Consumer<String> handler = encryptElement;
        if (handler != null) {
            handler.accept(xpath);
        }
    }

    static void decrypt() {
        Runnable handler = decrypt;
        if (handler != null) {
            handler.run();
        }
    }
}
