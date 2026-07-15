package org.fxt.freexmltoolkit.service.fileassoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Test double: records every command and answers via a configurable responder
 * (default: success with empty output).
 */
class RecordingCommandRunner implements CommandRunner {

    final List<List<String>> commands = new ArrayList<>();
    Function<List<String>, CommandResult> responder = c -> new CommandResult(0, "", "");

    @Override
    public CommandResult run(List<String> command) throws IOException {
        commands.add(command);
        return responder.apply(command);
    }

    List<String> flatCommands() {
        return commands.stream().map(c -> String.join(" ", c)).toList();
    }
}
