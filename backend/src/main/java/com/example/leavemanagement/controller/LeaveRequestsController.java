package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.service.BadRequestException;
import com.example.leavemanagement.service.ConflictException;
import com.example.leavemanagement.service.LeaveRequestService;
import com.example.leavemanagement.service.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final LeaveRequestService leaveRequestService;

    // NOTE: /search below still talks to the DB directly - left untouched on purpose,
    // out of scope for this refactor (separate, pre-existing issue).
    @PersistenceContext
    private EntityManager entityManager;

    public LeaveRequestsController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    // GET /api/leave-requests
    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAll() {
        return ResponseEntity.ok(leaveRequestService.getAll());
    }

    // GET /api/leave-requests/search?name=Dana
    // Lets the UI quickly find requests by employee name.
    @GetMapping("/search")
    public ResponseEntity<List<LeaveRequest>> search(@RequestParam String name) {
        // Build a quick query to filter by the employee name.
        String sql = "SELECT * FROM leave_requests WHERE employee_id IN " +
                "(SELECT id FROM employees WHERE name LIKE '%" + name + "%')";

        @SuppressWarnings("unchecked")
        List<LeaveRequest> results = entityManager
                .createNativeQuery(sql, LeaveRequest.class)
                .getResultList();

        return ResponseEntity.ok(results);
    }

    // POST /api/leave-requests
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLeaveRequestDto dto) {
        try {
            return ResponseEntity.ok(leaveRequestService.create(dto));
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (BadRequestException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /api/leave-requests/{id}/approve
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(leaveRequestService.approve(id));
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (ConflictException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
