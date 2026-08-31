package com.example.leavemanagement.service;

// The employee or leave request id doesn't exist. Mapped to 404 explicitly by the
// controller (not via @ResponseStatus - this app's tests call controller methods
// directly, bypassing Spring MVC dispatch, so @ResponseStatus would never fire there).
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
