import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { GameService } from '../../../../core/services/game.service';
import { AchievementToastService } from '../../../../core/services/achievement-toast.service';
import {
  PrecisionAnswerResponse,
  QuestionNumeric,
} from '../../../../core/models/game.models';

type Phase = 'idle' | 'feedback' | 'loading';

@Component({
  selector: 'app-precision',
  standalone: true,
  imports: [RouterLink, FormsModule, DecimalPipe],
  templateUrl: './precision.html',
  styleUrl: './precision.scss',
})
export class Precision implements OnInit {
  private readonly game = inject(GameService);
  private readonly router = inject(Router);
  private readonly achievementToasts = inject(AchievementToastService);

  lives = signal(100);
  rounds = signal(0);
  phase = signal<Phase>('loading');
  errorMessage = signal<string | null>(null);

  question = signal<QuestionNumeric | null>(null);
  sessionId = signal<string | null>(null);
  inputValue = signal<string>('');

  feedback = signal<{ correctValue: number; deviationPercent: number; lifeDelta: number } | null>(null);
  private pendingNext: QuestionNumeric | null = null;

  readonly lifeBarStyle = computed(() => `width: ${Math.max(0, Math.min(100, this.lives()))}%`);

  ngOnInit(): void {
    this.start();
  }

  submit(): void {
    if (this.phase() !== 'idle') return;
    const q = this.question();
    const sid = this.sessionId();
    const raw = this.inputValue().replace(',', '.').trim();
    const value = Number(raw);
    if (!q || !sid || !Number.isFinite(value)) return;

    this.phase.set('loading');
    this.errorMessage.set(null);
    this.game
      .answerPrecision({ sessionId: sid, questionId: q.id, value })
      .subscribe({
        next: (res) => this.applyAnswer(res),
        error: () => {
          this.phase.set('idle');
          this.errorMessage.set('No se pudo enviar la respuesta.');
        },
      });
  }

  next(): void {
    if (this.pendingNext) {
      this.question.set(this.pendingNext);
      this.pendingNext = null;
    }
    this.feedback.set(null);
    this.inputValue.set('');
    this.phase.set('idle');
  }

  private start(): void {
    this.phase.set('loading');
    this.errorMessage.set(null);
    this.game.startPrecision().subscribe({
      next: (res) => {
        if (res.question.type !== 'NUMERIC') {
          this.errorMessage.set('Pregunta inválida recibida del servidor.');
          this.phase.set('idle');
          return;
        }
        this.sessionId.set(res.sessionId);
        this.question.set(res.question);
        this.lives.set(100);
        this.rounds.set(0);
        this.inputValue.set('');
        this.phase.set('idle');
      },
      error: () => {
        this.phase.set('idle');
        this.errorMessage.set('No se pudo iniciar la partida.');
      },
    });
  }

  private applyAnswer(res: PrecisionAnswerResponse): void {
    this.lives.set(res.livesRemaining);
    this.rounds.update((r) => r + 1);
    this.feedback.set({
      correctValue: res.correctValue,
      deviationPercent: res.deviationPercent,
      lifeDelta: res.lifeDelta,
    });
    this.phase.set('feedback');

    if (res.nextQuestion && res.nextQuestion.type === 'NUMERIC') {
      this.pendingNext = res.nextQuestion;
    }

    if (res.gameOver) {
      this.achievementToasts.showMany(res.achievementsUnlocked);
      const finalRounds = this.rounds();
      setTimeout(
        () =>
          this.router.navigate(['/play/result'], {
            state: {
              mode: 'PRECISION',
              score: Math.max(0, this.lives()),
              bestStreak: 0,
              rounds: finalRounds,
              won: this.lives() > 0,
            },
          }),
        1600
      );
    }
  }
}
