package com.recruiting.candidates.exception;

/** The message intentionally omits the email address: error messages are logged and must not carry personal data. */
public class DuplicateEmailException extends ApplicationException {

    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, "A candidate with this email already exists.");
    }
}
