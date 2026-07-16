package com.example.leavemanagement.model;

import com.fasterxml.jackson.annotation.JsonFormat;

// Serialized as a number (0/1/2) to match the Angular client, which works with
// numeric codes. Mirrors how the original .NET POC exposed the enum.
@JsonFormat(shape = JsonFormat.Shape.NUMBER)
public enum LeaveType {
    VACATION,   // 0
    SICK,       // 1
    UNPAID      // 2
}
