package org.glavo.javafx.webp;

import java.io.IOException;

/// Signals that WebP data could not be parsed or decoded.
///
/// The exception extends [IOException] because failures typically happen while consuming
/// an external byte stream. The library wraps lower-level parser and Image I/O failures in this
/// type so callers can handle all WebP-specific read failures consistently.
public class WebPException extends IOException {

    /// Creates a new exception with the supplied message.
    ///
    /// @param message a human-readable description of the failure
    public WebPException(String message) {
        super(message);
    }

    /// Creates a new exception with the supplied message and cause.
    ///
    /// @param message a human-readable description of the failure
    /// @param cause the underlying cause
    public WebPException(String message, Throwable cause) {
        super(message, cause);
    }
}
