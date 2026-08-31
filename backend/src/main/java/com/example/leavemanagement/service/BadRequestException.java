package com.example.leavemanagement.service;

// The request itself is invalid (e.g. exceeds the annual quota). Mapped to 400
// explicitly by the controller - see NotFoundException for why not @ResponseStatus.
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
