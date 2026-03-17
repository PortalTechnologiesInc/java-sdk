package cc.getportal;

import java.util.concurrent.CompletableFuture;

/**
 * Wraps an async operation: the stream ID is available immediately,
 * while the {@code done} future resolves when a terminal event arrives.
 */
public record AsyncOperation<T>(String streamId, CompletableFuture<T> done) {}
