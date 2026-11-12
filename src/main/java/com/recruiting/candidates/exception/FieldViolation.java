package com.recruiting.candidates.exception;

/** One invalid input field: where ({@code field}) and why ({@code message}). Never echoes the rejected value. */
public record FieldViolation(String field, String message) {}
