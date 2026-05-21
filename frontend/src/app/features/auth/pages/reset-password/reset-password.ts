import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ResetPasswordForm } from '../../components/reset-password-form/reset-password-form';

@Component({
  selector: 'app-reset-password',
  imports: [RouterLink, ResetPasswordForm],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
})
export class ResetPassword implements OnInit {
  readonly token = signal<string>('');

  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.token.set(this.route.snapshot.queryParamMap.get('token') ?? '');
  }
}
