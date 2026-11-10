package org.fxt.freexmltoolkit.service.fo;

import java.nio.file.Path;

/** Builds {@link FoProcessorConfig}s that start {@link FakeFoProcessor} with the running JVM. */
public final class FakeProcessors {

    private FakeProcessors() {
    }

    /** @return the java program of this JVM */
    public static String java() {
        return ProcessHandle.current().info().command().orElseThrow();
    }

    /** @return the test classes folder (quoted for the argument template) */
    static String classpath() {
        try {
            return Path.of(FakeFoProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param mode  the fake's mode (ok, fail, nooutput, hang)
     * @param extra template text appended after {@code ${fo} ${pdf}}
     */
    public static FoProcessorConfig config(String name, String mode, String extra, int timeoutSeconds) {
        String arguments = "--enable-preview -cp \"" + classpath() + "\" " + FakeFoProcessor.class.getName()
                + " " + mode + " ${fo} ${pdf}" + (extra == null ? "" : " " + extra);
        return new FoProcessorConfig(null, name, FoProcessorPreset.CUSTOM, java(), arguments, "", timeoutSeconds);
    }
}
