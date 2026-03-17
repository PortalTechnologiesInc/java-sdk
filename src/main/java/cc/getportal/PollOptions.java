package cc.getportal;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Builder-style options for polling a stream until completion.
 */
public class PollOptions {
    public long intervalMs = 1000;
    public long timeoutMs = 0; // 0 = no timeout
    @Nullable public Consumer<StreamEvent> onEvent;

    public static PollOptions defaults() {
        return new PollOptions();
    }

    public PollOptions intervalMs(long ms) {
        this.intervalMs = ms;
        return this;
    }

    public PollOptions timeoutMs(long ms) {
        this.timeoutMs = ms;
        return this;
    }

    public PollOptions onEvent(Consumer<StreamEvent> cb) {
        this.onEvent = cb;
        return this;
    }
}
