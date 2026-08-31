import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CreateLeaveRequestPayload, Employee, LeaveRequest } from '../models/leave-request.model';

@Injectable({ providedIn: 'root' })
export class LeaveRequestsService {
  private apiUrl = `${environment.apiUrl}/api/leave-requests`;
  private employeesApiUrl = `${environment.apiUrl}/api/employees`;

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
