import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ForgotPasswordForm } from '../../components/forgot-password-form/forgot-password-form';

@Component({
  selector: 'app-forgot-password',
  imports: [RouterLink, ForgotPasswordForm],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword {}
