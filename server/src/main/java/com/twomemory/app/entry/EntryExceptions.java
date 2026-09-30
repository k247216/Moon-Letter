package com.twomemory.app.entry;

class EntryValidationException extends RuntimeException {
    EntryValidationException(String message) {
        super(message);
    }
}

class EntryAccessDeniedException extends RuntimeException {
    EntryAccessDeniedException(String message) {
        super(message);
    }
}
