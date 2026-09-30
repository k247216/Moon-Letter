package com.twomemory.app.media;

class MediaValidationException extends RuntimeException {
    MediaValidationException(String message) {
        super(message);
    }
}

class MediaAccessDeniedException extends RuntimeException {
    MediaAccessDeniedException(String message) {
        super(message);
    }
}

class MediaNotReadyException extends RuntimeException {
    MediaNotReadyException(String message) {
        super(message);
    }
}
