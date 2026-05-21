import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/services/auth.service';
import { VsButtonComponent } from '../../../../shared/components/vs-button/vs-button.component';

@Component({
  selector: 'app-forgot-password-form',
  imports: [ReactiveFormsModule, VsButtonComponent],
  templateUrl: './forgot-password-form.html',
  styleUrl: './forgot-password-form.scss',
})
export class ForgotPasswordForm {
  readonly loading = signal(false);
  readonly sent = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = inject(FormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
  });

  private readonly auth = inject(AuthService);

  isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  getError(field: string): string | null {
    const ctrl = this.form.get(field);
    if (!ctrl?.errors || !ctrl.touched) return null;
    const { required, email } = ctrl.errors;
    if (required) return 'Este campo es obligatorio';
    if (email)    return 'Introduce un correo válido';
    return null;
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;

    this.errorMessage.set(null);
    this.loading.set(true);

    this.auth.requestPasswordReset({ email: this.form.value.email! }).subscribe({
      next: () => {
        this.loading.set(false);
        this.sent.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 0) {
          this.errorMessage.set('No se puede conectar con el servidor');
        } else {
          this.errorMessage.set(err?.error?.message ?? 'Error inesperado');
        }
      },
    });
  }
}
