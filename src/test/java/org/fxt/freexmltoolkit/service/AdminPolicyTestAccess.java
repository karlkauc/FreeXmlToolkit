package org.fxt.freexmltoolkit.service;

import java.nio.file.Path;
import java.util.Properties;

/** Lets tests in other packages install an {@link AdminPolicy}. */
public final class AdminPolicyTestAccess {

    private AdminPolicyTestAccess() {
    }

    /** Makes a policy with these locked settings and defaults the current one. */
    public static void setCurrent(Path policyFile, Properties locked, Properties defaults) {
        AdminPolicy.setCurrentForTests(AdminPolicy.of(policyFile, locked, defaults));
    }

    /** Reads the real policy again on the next access. */
    public static void reset() {
        AdminPolicy.setCurrentForTests(null);
    }
}
