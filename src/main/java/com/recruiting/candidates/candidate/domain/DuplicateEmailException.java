package com.recruiting.candidates.candidate.domain;

import com.recruiting.candidates.common.error.ApplicationException;
import com.recruiting.candidates.common.error.ErrorCode;

/** The message intentionally omits the email address: error messages are logged and must not carry personal data. */
public class DuplicateEmailException extends ApplicationException {

    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, "A candidate with this email already exists.");
    }
}
