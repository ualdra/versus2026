import { Component, inject, signal } from '@angular/core';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-login-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login-form.html',
  styleUrl: './login-form.scss',
})
export class LoginForm {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form: FormGroup;

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor(private readonly fb: FormBuilder) {
    this.form = this.fb.group({
      identifier: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
  }

  isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  getError(field: string): string | null {
    const ctrl = this.form.get(field);
    if (!ctrl?.errors || !ctrl.touched) return null;
    const { required, minlength, email } = ctrl.errors;
    if (required)  return 'Este campo es obligatorio';
    if (email)     return 'Introduce un correo válido';
    if (minlength) return `Mínimo ${minlength.requiredLength} caracteres`;
    return null;
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;

    this.errorMessage.set(null);
    this.loading.set(true);

    const { identifier, password } = this.form.value;
    this.auth.login({ email: identifier, password }).subscribe({
      next: (res) => {
        this.loading.set(false);
        const target = res.user.role === 'ADMIN' ? '/admin' : '/dashboard';
        this.router.navigateByUrl(target);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.translate(err));
      },
    });
  }

  private translate(err: HttpErrorResponse): string {
    const code = err?.error?.error;
    if (code === 'UNAUTHORIZED') return 'Credenciales incorrectas';
    if (code === 'VALIDATION_ERROR') return 'Datos no válidos';
    if (err.status === 0) return 'No se puede conectar con el servidor';
    return err?.error?.message ?? 'Error inesperado';
  }
}
