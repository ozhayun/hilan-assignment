import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Employee, LeaveRequest, LeaveStatus, LeaveType } from '../models/leave-request.model';
import { LeaveRequestsService } from './leave-requests.service';

@Component({
  selector: 'app-leave-requests',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './leave-requests.component.html',
  styleUrls: ['./leave-requests.component.css']
})
export class LeaveRequestsComponent implements OnInit {
  // Exposed so the template can compare against them directly.
  readonly LeaveStatus = LeaveStatus;

  requests: LeaveRequest[] = [];
  employees: Employee[] = [];
  loading = false;

  submitting = false;
  submitError: string | null = null;

  approvingIds = new Set<number>();
  approveErrors: Record<number, string> = {};

  private fb = inject(FormBuilder);
  private leaveRequestsService = inject(LeaveRequestsService);

  form = this.fb.group(
    {
      employeeId: [null as number | null, Validators.required],
      leaveType: [null as LeaveType | null, Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required]
    },
    { validators: dateRangeValidator }
  );

  ngOnInit(): void {
    this.load();
    this.loadEmployees();
  }

  load(): void {
    this.loading = true;
    this.leaveRequestsService.getAll().subscribe((data) => {
      this.requests = data;
      this.loading = false;
    });
  }

  loadEmployees(): void {
    this.leaveRequestsService.getEmployees().subscribe((data) => {
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

    this.leaveRequestsService
      .create({
        employeeId: employeeId!,
        type: leaveType!,
        startDate: startDate!,
        endDate: endDate!
      })
      .subscribe({
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

  isApproving(id: number): boolean {
    return this.approvingIds.has(id);
  }

  approve(request: LeaveRequest): void {
    this.approvingIds.add(request.id);
    delete this.approveErrors[request.id];

    this.leaveRequestsService.approve(request.id).subscribe({
      next: (updated) => {
        this.approvingIds.delete(request.id);
        // Patch just this row instead of reloading/refetching the whole list.
        request.status = updated.status;
      },
      error: (err) => {
        this.approvingIds.delete(request.id);
        this.approveErrors[request.id] =
          typeof err.error === 'string' ? err.error : 'Failed to approve request.';
      }
    });
  }

  typeLabel(type: LeaveType): string {
    if (type === LeaveType.Vacation) return 'Vacation';
    if (type === LeaveType.Sick) return 'Sick';
    return 'Unpaid';
  }

  statusLabel(status: LeaveStatus): string {
    if (status === LeaveStatus.Pending) return 'Pending';
    if (status === LeaveStatus.Approved) return 'Approved';
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
