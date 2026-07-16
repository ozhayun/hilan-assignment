import { Component } from '@angular/core';
import { LeaveRequestsComponent } from './leave-requests/leave-requests.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [LeaveRequestsComponent],
  template: `
    <h1>Leave Management</h1>
    <app-leave-requests></app-leave-requests>
  `
})
export class AppComponent {}
