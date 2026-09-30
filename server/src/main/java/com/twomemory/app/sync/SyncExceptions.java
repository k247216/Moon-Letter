package com.twomemory.app.sync;

class SyncConflictException extends RuntimeException {
    SyncConflictException(String message) {
        super(message);
    }
}

class SyncValidationException extends RuntimeException {
    SyncValidationException(String message) {
        super(message);
    }
}
