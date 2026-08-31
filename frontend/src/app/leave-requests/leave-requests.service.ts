import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateLeaveRequestPayload, Employee, LeaveRequest } from '../models/leave-request.model';

// TODO: base URL should come from src/environments/environment.ts (Angular's native
// build-time config mechanism) instead of being hardcoded here - deferred, see DECISIONS.md.
@Injectable({ providedIn: 'root' })
export class LeaveRequestsService {
  private apiUrl = 'http://localhost:5080/api/leave-requests';
  private employeesApiUrl = 'http://localhost:5080/api/employees';

  constructor(private http: HttpClient) {}

  getAll(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(this.apiUrl);
  }

  getEmployees(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.employeesApiUrl);
  }

  create(payload: CreateLeaveRequestPayload): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(this.apiUrl, payload);
  }

  approve(id: number): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(`${this.apiUrl}/${id}/approve`, {});
  }
}
