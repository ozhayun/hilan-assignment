package com.example.leavemanagement.service;

// The leave request can't be approved in its current state (already
// approved/rejected, or approving it would exceed the quota). Mapped to 409
// explicitly by the controller - see NotFoundException for why not @ResponseStatus.
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
