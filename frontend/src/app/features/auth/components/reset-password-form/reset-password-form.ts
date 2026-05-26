import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/services/auth.service';
import { VsButtonComponent } from '../../../../shared/components/vs-button/vs-button.component';

function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const pw  = group.get('newPassword')?.value;
  const cpw = group.get('confirmPassword')?.value;
  return pw && cpw && pw !== cpw ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-reset-password-form',
  imports: [ReactiveFormsModule, VsButtonComponent],
  templateUrl: './reset-password-form.html',
  styleUrl: './reset-password-form.scss',
})
export class ResetPasswordForm implements OnInit {
  @Input({ required: true }) token!: string;

  readonly loading  = signal(false);
  readonly success  = signal(false);
  readonly showPw   = signal(false);
  readonly showCpw  = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = inject(FormBuilder).group(
    {
      newPassword:     ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordMatchValidator },
  );

  private readonly auth   = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (!this.token) {
      this.errorMessage.set('No se encontró el token de recuperación en el enlace.');
    }
  }

  isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  getError(field: string): string | null {
    const ctrl = this.form.get(field);
    if (!ctrl?.errors || !ctrl.touched) return null;
    const { required, minlength, maxlength } = ctrl.errors;
    if (required)   return 'Este campo es obligatorio';
    if (minlength)  return `Mínimo ${minlength.requiredLength} caracteres`;
    if (maxlength)  return `Máximo ${maxlength.requiredLength} caracteres`;
    return null;
  }

  get mismatch(): boolean {
    return !!(this.form.hasError('passwordMismatch') && this.form.get('confirmPassword')?.touched);
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading() || !this.token) return;

    this.errorMessage.set(null);
    this.loading.set(true);

    const { newPassword } = this.form.value;
    this.auth.confirmPasswordReset({ token: this.token, newPassword: newPassword! }).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigateByUrl('/login'), 2500);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        const code = err?.error?.error;
        if (code === 'TOKEN_EXPIRED')  this.errorMessage.set('El enlace ha expirado. Solicita uno nuevo.');
        else if (code === 'TOKEN_INVALID') this.errorMessage.set('El enlace no es válido.');
        else if (err.status === 0) this.errorMessage.set('No se puede conectar con el servidor');
        else this.errorMessage.set(err?.error?.message ?? 'Error inesperado');
      },
    });
  }
}
