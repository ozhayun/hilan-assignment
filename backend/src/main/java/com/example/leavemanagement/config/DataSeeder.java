package com.example.leavemanagement.config;

import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataSeeder implements CommandLineRunner {

    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;

    public DataSeeder(EmployeeRepository employees, LeaveRequestRepository leaveRequests) {
        this.employees = employees;
        this.leaveRequests = leaveRequests;
    }

    @Override
    public void run(String... args) {
        if (employees.count() > 0) return;

        Employee dana = new Employee();
        dana.setName("Dana Levi");
        dana.setAnnualQuota(20);

        Employee yossi = new Employee();
        yossi.setName("Yossi Cohen");
        yossi.setAnnualQuota(14);

        employees.save(dana);
        employees.save(yossi);

        // Dana has already used 18 of her 20 days (approved).
        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(dana.getId());
        r.setType(LeaveType.VACATION);
        r.setStartDate(LocalDate.of(2026, 1, 6));
        r.setEndDate(LocalDate.of(2026, 1, 23));
        r.setDays(18);
        r.setStatus(LeaveStatus.APPROVED);
        leaveRequests.save(r);
    }
}
