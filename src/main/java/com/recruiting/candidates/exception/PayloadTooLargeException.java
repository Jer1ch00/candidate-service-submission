package com.recruiting.candidates.exception;

import java.util.Map;

public class PayloadTooLargeException extends ApplicationException {

    public PayloadTooLargeException(long maxBytes) {
        super(
                ErrorCode.PAYLOAD_TOO_LARGE,
                "Request body exceeds the maximum allowed size of " + maxBytes + " bytes.",
                Map.of("maxBytes", maxBytes));
    }
}
