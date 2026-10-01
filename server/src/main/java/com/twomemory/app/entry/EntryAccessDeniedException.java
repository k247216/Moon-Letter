package com.twomemory.app.entry;

public class EntryAccessDeniedException extends RuntimeException {
    public EntryAccessDeniedException(String message) {
        super(message);
    }
}
