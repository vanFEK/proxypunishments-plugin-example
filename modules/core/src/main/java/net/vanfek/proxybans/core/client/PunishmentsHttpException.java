package net.vanfek.proxybans.core.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PunishmentsHttpException extends RuntimeException {

    private static final int NO_STATUS = -1;

    private final int statusCode;
    private final transient String responseBody;

    public PunishmentsHttpException(int statusCode, @NotNull String message, @Nullable String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public PunishmentsHttpException(@NotNull String message, @Nullable Throwable cause) {
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
