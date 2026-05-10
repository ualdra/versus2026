import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AchievementToastsComponent } from './shared/components/achievement-toasts/achievement-toasts';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AchievementToastsComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
