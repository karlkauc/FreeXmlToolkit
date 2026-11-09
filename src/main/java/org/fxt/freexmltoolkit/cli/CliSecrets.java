package org.fxt.freexmltoolkit.cli;

import picocli.CommandLine;

/**
 * Passwords on the command line: from an interactive prompt ({@code --password} without a value),
 * a value ({@code --password=…}, visible in the shell history, so discouraged) or an environment
 * variable ({@code --password-env NAME}), which scripts should use.
 */
final class CliSecrets {

    /** Tests set the environment here instead of the process environment. */
    static java.util.function.Function<String, String> environment = System::getenv;

    private CliSecrets() {
    }

    /**
     * @param value   the option value (may be empty after an empty prompt) or {@code null}
     * @param envName the environment variable to read instead, or {@code null}
     * @param what    the option, for the error message
     * @return the secret, or {@code null} when neither is given
     */
    static char[] resolve(CommandLine commandLine, char[] value, String envName, String what) {
        if (envName != null && !envName.isBlank()) {
            if (value != null) {
                throw new CommandLine.ParameterException(commandLine, "Give " + what + " or " + what + "-env, not both");
            }
            String fromEnv = environment.apply(envName);
            if (fromEnv == null) {
                throw new CommandLine.ParameterException(commandLine,
                        "The environment variable " + envName + " (" + what + "-env) is not set");
            }
            return fromEnv.toCharArray();
        }
        return value;
    }
}
