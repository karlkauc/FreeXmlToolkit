package org.fxt.freexmltoolkit.service.ai.agent;

import java.util.List;

/**
 * The outcome of verifying one candidate.
 *
 * @param feedbackForModel what a repair round tells the model (error codes, lines, messages — data, not
 *                         instructions); empty when verified
 */
public record Verification(Status status, List<Check> checks, String feedbackForModel) {

    public enum Status { VERIFIED, FAILED, NOT_CHECKABLE }

    public Verification {
        checks = List.copyOf(checks);
        feedbackForModel = feedbackForModel == null ? "" : feedbackForModel;
    }

    public boolean verified() {
        return status == Status.VERIFIED;
    }

    public static Verification failed(String label, String detail) {
        return new Verification(Status.FAILED, List.of(new Check(label, false, detail)), label + ": " + detail);
    }
}
