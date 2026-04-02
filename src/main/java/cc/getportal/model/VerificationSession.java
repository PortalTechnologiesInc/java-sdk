package cc.getportal.model;

import cc.getportal.AsyncOperation;

/**
 * Result of {@code createVerificationSession()} — contains the session info
 * and an {@link AsyncOperation} for polling the verification token.
 */
public class VerificationSession {
    public final VerificationSessionResponse session;
    public final AsyncOperation<CashuResponseStatus> operation;

    public VerificationSession(
            VerificationSessionResponse session,
            AsyncOperation<CashuResponseStatus> operation
    ) {
        this.session = session;
        this.operation = operation;
    }
}
