package com.twomemory.app.couple;

class ConflictException extends RuntimeException {
    ConflictException(String message) {
        super(message);
    }
}

class AccessDeniedException extends RuntimeException {
    AccessDeniedException(String message) {
        super(message);
    }
}

class ValidationException extends RuntimeException {
    ValidationException(String message) {
        super(message);
    }
}
