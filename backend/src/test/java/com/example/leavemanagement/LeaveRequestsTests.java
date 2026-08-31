package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestsController controller;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

    @Test
    void create_ExceedingRemainingQuota_IsRejected() {
        // Arrange: employee with a 20-day quota who already has 18 approved vacation days.
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        LeaveRequest alreadyApproved = new LeaveRequest();
        alreadyApproved.setEmployeeId(emp.getId());
        alreadyApproved.setType(LeaveType.VACATION);
        alreadyApproved.setStatus(LeaveStatus.APPROVED);
        alreadyApproved.setStartDate(LocalDate.of(2026, 1, 1));
        alreadyApproved.setEndDate(LocalDate.of(2026, 1, 18));
        alreadyApproved.setDays(18);
        leaveRequests.save(alreadyApproved);

        long before = leaveRequests.count();

        // A further 5-day request would push the employee to 23 days, over the 20-day quota.
        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 5));

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertEquals(400, result.getStatusCode().value());
        assertEquals(before, leaveRequests.count());
    }

    @Test
    void approve_UnknownId_Returns404() {
        ResponseEntity<?> result = controller.approve(-1L);

        assertEquals(404, result.getStatusCode().value());
    }

    @Test
    void approve_AlreadyApproved_Returns409() {
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(emp.getId());
        request.setType(LeaveType.VACATION);
        request.setStatus(LeaveStatus.APPROVED);
        request.setStartDate(LocalDate.of(2026, 3, 1));
        request.setEndDate(LocalDate.of(2026, 3, 3));
        request.setDays(3);
        leaveRequests.save(request);

        ResponseEntity<?> result = controller.approve(request.getId());

        assertEquals(409, result.getStatusCode().value());
    }

    @Test
    void approve_Pending_SetsStatusApproved() {
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(emp.getId());
        request.setType(LeaveType.VACATION);
        request.setStatus(LeaveStatus.PENDING);
        request.setStartDate(LocalDate.of(2026, 3, 1));
        request.setEndDate(LocalDate.of(2026, 3, 3));
        request.setDays(3);
        leaveRequests.save(request);

        ResponseEntity<?> result = controller.approve(request.getId());

        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_ConcurrentApprovalsExceedingQuota_OnlyOneSucceeds() throws Exception {
        // Employee with a 10-day quota and two independent 6-day PENDING requests.
        // Approving both would total 12 days, over quota - only one may be approved.
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(10);
        employees.save(emp);

        LeaveRequest requestA = new LeaveRequest();
        requestA.setEmployeeId(emp.getId());
        requestA.setType(LeaveType.VACATION);
        requestA.setStatus(LeaveStatus.PENDING);
        requestA.setStartDate(LocalDate.of(2026, 3, 1));
        requestA.setEndDate(LocalDate.of(2026, 3, 6));
        requestA.setDays(6);
        leaveRequests.save(requestA);

        LeaveRequest requestB = new LeaveRequest();
        requestB.setEmployeeId(emp.getId());
        requestB.setType(LeaveType.VACATION);
        requestB.setStatus(LeaveStatus.PENDING);
        requestB.setStartDate(LocalDate.of(2026, 4, 1));
        requestB.setEndDate(LocalDate.of(2026, 4, 6));
        requestB.setDays(6);
        leaveRequests.save(requestB);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<?>> approveA = () -> {
                barrier.await();
                return controller.approve(requestA.getId());
            };
            Callable<ResponseEntity<?>> approveB = () -> {
                barrier.await();
                return controller.approve(requestB.getId());
            };

            Future<ResponseEntity<?>> futureA = pool.submit(approveA);
            Future<ResponseEntity<?>> futureB = pool.submit(approveB);

            ResponseEntity<?> resultA = futureA.get(10, TimeUnit.SECONDS);
            ResponseEntity<?> resultB = futureB.get(10, TimeUnit.SECONDS);

            boolean exactlyOneSucceeded =
                    resultA.getStatusCode().is2xxSuccessful() != resultB.getStatusCode().is2xxSuccessful();
            assertTrue(exactlyOneSucceeded, "Exactly one of the two concurrent approvals should succeed");

            List<LeaveRequest> approved = leaveRequests
                    .findByEmployeeIdAndTypeAndStatus(emp.getId(), LeaveType.VACATION, LeaveStatus.APPROVED);
            int totalApprovedDays = approved.stream().mapToInt(LeaveRequest::getDays).sum();
            assertTrue(totalApprovedDays <= emp.getAnnualQuota(),
                    "Total approved days must never exceed the annual quota");
        } finally {
            pool.shutdownNow();
        }
    }
}
