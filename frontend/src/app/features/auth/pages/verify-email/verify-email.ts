import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-verify-email',
  imports: [RouterLink],
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.scss',
})
export class VerifyEmail implements OnInit {
  readonly state = signal<'loading' | 'success' | 'expired' | 'invalid'>('loading');
  readonly message = signal<string | null>(null);

  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('invalid');
      this.message.set('No se encontró el token de verificación en el enlace.');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: (res) => {
        this.state.set('success');
        this.message.set(res.message);
      },
      error: (err: HttpErrorResponse) => {
        const code = err?.error?.error;
        if (code === 'TOKEN_EXPIRED') {
          this.state.set('expired');
          this.message.set('El enlace de verificación ha expirado. Vuelve a registrarte para recibir uno nuevo.');
        } else {
          this.state.set('invalid');
          this.message.set('El enlace de verificación no es válido.');
        }
      },
    });
  }
}
