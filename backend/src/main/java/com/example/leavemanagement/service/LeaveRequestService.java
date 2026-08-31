package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

// Owns the leave-request business logic: quota calculation, create validation,
// and the approve state machine (+ its concurrency handling). The controller
// only translates HTTP requests into calls here; exceptions thrown here carry
// their own @ResponseStatus, so the controller doesn't map them by hand.
@Service
public class LeaveRequestService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository,
                                LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public List<LeaveRequest> getAll() {
        return leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
    }

    @Transactional
    public LeaveRequest create(CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        // Reject double-booking: this employee can't have two leave requests
        // (of any type) covering the same day.
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                dto.getEmployeeId(), dto.getStartDate(), dto.getEndDate(), LeaveStatus.REJECTED);
        if (!overlapping.isEmpty()) {
            throw new ConflictException("Overlaps with an existing leave request for this employee");
        }

        // How many vacation days has the employee already used this year?
        int used = vacationDaysUsed(dto.getEmployeeId());

        // Make sure the request does not exceed the quota.
        if (dto.getType() == LeaveType.VACATION && used + days > employee.getAnnualQuota()) {
            throw new BadRequestException("Not enough vacation balance");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        return leaveRequestRepository.save(request);
    }

    @Transactional
    public LeaveRequest approve(Long id) {
        // Lock the request row first, so two concurrent approve() calls for the *same*
        // request id are serialized instead of both seeing it as still PENDING.
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Leave request not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("Leave request is already " + request.getStatus());
        }

        if (request.getType() == LeaveType.VACATION) {
            // Also lock the employee row, so two concurrent approvals for *different*
            // requests belonging to the same employee can't both read the same "days
            // used so far" and jointly push the employee over quota.
            Employee employee = employeeRepository.findByIdForUpdate(request.getEmployeeId())
                    .orElseThrow(() -> new NotFoundException("Employee not found"));

            int used = vacationDaysUsed(request.getEmployeeId());

            if (used + request.getDays() > employee.getAnnualQuota()) {
                throw new ConflictException("Approving this request would exceed the annual quota");
            }
        }

        request.setStatus(LeaveStatus.APPROVED);
        return leaveRequestRepository.save(request);
    }

    private int vacationDaysUsed(Long employeeId) {
        return leaveRequestRepository
                .findByEmployeeIdAndTypeAndStatus(employeeId, LeaveType.VACATION, LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();
    }
}
