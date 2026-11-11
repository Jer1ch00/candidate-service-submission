package com.recruiting.candidates.common.error;

/** Missing or unknown API key. The message deliberately does not say which of the two it was. */
public class UnauthorizedException extends ApplicationException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED, "A valid API key is required in the X-API-Key header.");
    }
}
