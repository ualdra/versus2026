import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { VsButtonComponent } from '../../../../shared/components/vs-button/vs-button.component';
import { AuthService } from '../../../../core/services/auth.service';

function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-register-form',
  imports: [ReactiveFormsModule, VsButtonComponent],
  templateUrl: './register-form.html',
  styleUrl: './register-form.scss',
})
export class RegisterForm {
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly showConfirmPassword = signal(false);

  readonly form: FormGroup;

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor(private readonly fb: FormBuilder) {
    this.form = this.fb.group(
      {
        username: [
          '',
          [
            Validators.required,
            Validators.minLength(3),
            Validators.maxLength(20),
            Validators.pattern(/^\w+$/),
          ],
        ],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(8)]],
        confirmPassword: ['', Validators.required],
      },
      { validators: passwordMatchValidator },
    );
  }

  isInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl?.invalid && ctrl.touched);
  }

  getError(field: string): string | null {
    const ctrl = this.form.get(field);
    if (!ctrl?.errors || !ctrl.touched) return null;
    const { required, minlength, maxlength, email, pattern } = ctrl.errors;
    if (required)  return 'Este campo es obligatorio';
    if (minlength) return `Mínimo ${minlength.requiredLength} caracteres`;
    if (maxlength) return `Máximo ${maxlength.requiredLength} caracteres`;
    if (email)     return 'Introduce un correo válido';
    if (pattern)   return 'Solo letras, números y guión bajo';
    return null;
  }

  get mismatch(): boolean {
    return !!(
      this.form.hasError('passwordMismatch') &&
      this.form.get('confirmPassword')?.touched
    );
  }

  togglePassword(): void {
    this.showPassword.update(v => !v);
  }

  toggleConfirmPassword(): void {
    this.showConfirmPassword.update(v => !v);
  }

  onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;

    this.errorMessage.set(null);
    this.loading.set(true);

    const { username, email, password } = this.form.value;
    this.auth.register({ username, email, password }).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/check-email');
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.errorMessage.set(this.translate(err));
      },
    });
  }

  private translate(err: HttpErrorResponse): string {
    const code = err?.error?.error;
    if (code === 'CONFLICT') return 'El email o usuario ya están registrados';
    if (code === 'VALIDATION_ERROR') return 'Datos no válidos';
    if (err.status === 0) return 'No se puede conectar con el servidor';
    return err?.error?.message ?? 'Error inesperado';
  }
}
