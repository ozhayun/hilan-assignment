package com.example.leavemanagement.model;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.NUMBER)
public enum LeaveStatus {
    PENDING,    // 0
    APPROVED,   // 1
    REJECTED    // 2
}
