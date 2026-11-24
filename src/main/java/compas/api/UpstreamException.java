package compas.api;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

public class UpstreamException extends RuntimeException {
    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean isTimeout() {
        for (Throwable cause = getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
