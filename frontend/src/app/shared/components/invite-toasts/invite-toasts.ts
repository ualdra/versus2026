import { Component, inject } from '@angular/core';
import { InviteToastService } from '../../../core/services/invite-toast.service';

@Component({
  selector: 'app-invite-toasts',
  standalone: true,
  templateUrl: './invite-toasts.html',
  styleUrl: './invite-toasts.scss',
})
export class InviteToastsComponent {
  readonly toasts = inject(InviteToastService);
}
