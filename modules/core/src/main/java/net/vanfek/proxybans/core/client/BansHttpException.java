package net.vanfek.proxybans.core.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Any failure while talking to the bans service.
 *
 * <p>The response body is kept separate from the message on purpose: the message is safe to show to
 * staff in chat, the body is for the log only and may contain internals of the service.
 */
public class BansHttpException extends RuntimeException {

    private static final int NO_STATUS = -1;

    private final int statusCode;
    private final transient String responseBody;

    public BansHttpException(int statusCode, @NotNull String message, @Nullable String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public BansHttpException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.statusCode = NO_STATUS;
        this.responseBody = null;
    }

    public int statusCode() {
        return this.statusCode;
    }

    public boolean hasStatus() {
        return this.statusCode != NO_STATUS;
    }

    public boolean isNotFound() {
        return this.statusCode == 404;
    }

    public @Nullable String responseBody() {
        return this.responseBody;
    }

}
