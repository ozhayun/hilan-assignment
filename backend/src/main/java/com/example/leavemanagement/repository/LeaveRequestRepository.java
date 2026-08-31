package com.example.leavemanagement.repository;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByEmployeeIdAndTypeAndStatus(Long employeeId, LeaveType type, LeaveStatus status);

    // Locks the request row, so two concurrent approve() calls for the *same* request id
    // are serialized instead of both reading it as still PENDING.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LeaveRequest r where r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    // Any of the employee's non-rejected requests whose date range overlaps
    // [startDate, endDate] - used to reject double-booked leave.
    @Query("select r from LeaveRequest r where r.employeeId = :employeeId and r.status <> :excludedStatus "
            + "and r.startDate <= :endDate and r.endDate >= :startDate")
    List<LeaveRequest> findOverlapping(@Param("employeeId") Long employeeId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate,
                                        @Param("excludedStatus") LeaveStatus excludedStatus);

    // Case-insensitive substring match on the employee's name, via a proper bind
    // parameter - no string concatenation into SQL.
    List<LeaveRequest> findByEmployee_NameContainingIgnoreCase(String name);
}
