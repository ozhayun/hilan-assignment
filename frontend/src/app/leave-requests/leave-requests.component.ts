import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';

// NOTE: This component was written quickly for a POC.
// It talks to the API directly, manages state by hand and uses `any` everywhere.
@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  requests: any[] = [];
  employees: any[] = [];
  loading = false;

  submitting = false;
  submitError: string | null = null;

  private fb = inject(FormBuilder);

  form = this.fb.group(
    {
      employeeId: [null, Validators.required],
      leaveType: [null, Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required]
    },
    { validators: dateRangeValidator }
  );

  private apiUrl = 'http://localhost:5080/api/leave-requests';
  private employeesApiUrl = 'http://localhost:5080/api/employees';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.load();
    this.loadEmployees();
  }

  load(): void {
    this.loading = true;
    this.http.get<any>(this.apiUrl).subscribe((data) => {
      this.requests = data;
      this.loading = false;
    });
  }

  loadEmployees(): void {
    this.http.get<any[]>(this.employeesApiUrl).subscribe((data) => {
      this.employees = data;
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.submitError = null;

    const { employeeId, leaveType, startDate, endDate } = this.form.value;
    const payload = {
      employeeId,
      type: leaveType,
      startDate,
      endDate
    };

    this.http.post<any>(this.apiUrl, payload).subscribe({
      next: () => {
        this.submitting = false;
        this.form.reset();
        this.load();
      },
      error: (err) => {
        this.submitting = false;
        this.submitError = typeof err.error === 'string' ? err.error : 'Failed to submit leave request.';
      }
    });
  }

  // Wired up by the candidate as part of the assignment.
  approve(id: number): void {
    // TODO (candidate): call POST /api/leave-requests/{id}/approve
    // and handle loading / error / success without a generic alert.
    this.http.post<any>(this.apiUrl + '/' + id + '/approve', {}).subscribe(() => {
      this.load();
    });
  }

  typeLabel(type: number): string {
    if (type == 0) return 'Vacation';
    if (type == 1) return 'Sick';
    return 'Unpaid';
  }

  statusLabel(status: number): string {
    if (status == 0) return 'Pending';
    if (status == 1) return 'Approved';
    return 'Rejected';
  }
}

// Cross-field validator: startDate must not be after endDate. Compared as ISO
// (yyyy-MM-dd) strings from the native date input, which sort chronologically.
function dateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDate')?.value;
  const end = group.get('endDate')?.value;
  if (!start || !end) {
    return null;
  }
  return start > end ? { dateRangeInvalid: true } : null;
}
