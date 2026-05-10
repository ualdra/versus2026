import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { GameService } from '../../../../core/services/game.service';
import { AchievementToastService } from '../../../../core/services/achievement-toast.service';
import {
  QuestionBinary,
  SurvivalAnswerResponse,
} from '../../../../core/models/game.models';

type Phase = 'idle' | 'correct' | 'wrong' | 'loading';

@Component({
  selector: 'app-survival',
  standalone: true,
  imports: [RouterLink, UpperCasePipe],
  templateUrl: './survival.html',
  styleUrl: './survival.scss',
})
export class Survival implements OnInit {
  private readonly game = inject(GameService);
  private readonly router = inject(Router);
  private readonly achievementToasts = inject(AchievementToastService);

  lives  = signal(3);
  streak = signal(0);
  score  = signal(0);
  phase  = signal<Phase>('loading');
  qIdx   = signal(0);

  feedback = signal<{ isCorrect: boolean; delta: number } | null>(null);
  errorMessage = signal<string | null>(null);

  question = signal<QuestionBinary | null>(null);
  pickedOptionId = signal<string | null>(null);
  correctOptionId = signal<string | null>(null);

  sessionId = signal<string | null>(null);
  private pendingNext: QuestionBinary | null = null;

  showBurst = computed(() => this.phase() === 'correct' && this.streak() >= 5);
  burstItems = Array.from({ length: 12 }, (_, i) => i);

  ngOnInit(): void {
    this.start();
  }

  fmt(n: number): string { return n.toLocaleString('es-ES'); }

  optionState(optionId: string): 'idle' | 'correct' | 'wrong' | 'muted' {
    if (this.phase() === 'idle' || this.phase() === 'loading') return 'idle';
    const correct = this.correctOptionId();
    const picked = this.pickedOptionId();
    if (correct && optionId === correct) return 'correct';
    if (picked === optionId) return 'wrong';
    return 'muted';
  }

  optionClass(optionId: string): string {
    const state = this.optionState(optionId);
    return [
      'vs-bopt',
      state === 'idle' ? 'vs-bopt--idle' : '',
      state === 'correct' ? 'vs-bopt--correct' : '',
      state === 'wrong' ? 'vs-bopt--wrong' : '',
      state === 'muted' ? 'vs-bopt--muted' : '',
    ].filter(Boolean).join(' ');
  }

  pick(optionId: string): void {
    if (this.phase() !== 'idle') return;
    const q = this.question();
    const sid = this.sessionId();
    if (!q || !sid) return;

    this.pickedOptionId.set(optionId);
    this.phase.set('loading');
    this.errorMessage.set(null);

    this.game
      .answerSurvival({ sessionId: sid, questionId: q.id, optionId })
      .subscribe({
        next: (res) => this.applyAnswer(res, q),
        error: () => {
          this.phase.set('idle');
          this.pickedOptionId.set(null);
          this.errorMessage.set('No se pudo enviar la respuesta. Inténtalo de nuevo.');
        },
      });
  }

  next(): void {
    if (this.pendingNext) {
      this.question.set(this.pendingNext);
      this.pendingNext = null;
    }
    this.phase.set('idle');
    this.feedback.set(null);
    this.pickedOptionId.set(null);
    this.correctOptionId.set(null);
    this.qIdx.update(i => i + 1);
  }

  heartClass(i: number): string {
    const l = this.lives();
    if (i >= l)   return 'vs-lifebar__heart vs-lifebar__heart--lost';
    if (l === 1)  return 'vs-lifebar__heart vs-lifebar__heart--low';
    if (l === 2)  return 'vs-lifebar__heart vs-lifebar__heart--mid';
    return 'vs-lifebar__heart';
  }

  private start(): void {
    this.phase.set('loading');
    this.errorMessage.set(null);
    this.game.startSurvival().subscribe({
      next: (res) => {
        if (res.question.type !== 'BINARY') {
          this.errorMessage.set('Pregunta inválida recibida del servidor.');
          this.phase.set('idle');
          return;
        }
        this.sessionId.set(res.sessionId);
        this.question.set(res.question);
        this.lives.set(3);
        this.streak.set(0);
        this.score.set(0);
        this.qIdx.set(0);
        this.phase.set('idle');
      },
      error: () => {
        this.phase.set('idle');
        this.errorMessage.set('No se pudo iniciar la partida.');
      },
    });
  }

  private applyAnswer(res: SurvivalAnswerResponse, current: QuestionBinary): void {
    const pickedId = this.pickedOptionId();
    const correctId = res.correct
      ? pickedId
      : current.options.find((o) => o.id !== pickedId)?.id ?? null;
    this.correctOptionId.set(correctId);

    this.lives.set(res.livesRemaining);
    this.streak.set(res.streak);
    this.score.update((s) => s + res.scoreDelta);
    this.feedback.set({ isCorrect: res.correct, delta: res.scoreDelta });
    this.phase.set(res.correct ? 'correct' : 'wrong');

    if (res.nextQuestion && res.nextQuestion.type === 'BINARY') {
      this.pendingNext = res.nextQuestion;
    }

    if (res.gameOver) {
      this.achievementToasts.showMany(res.achievementsUnlocked);
      const finalScore = this.score();
      const finalStreak = this.streak();
      const rounds = this.qIdx() + 1;
      setTimeout(() => this.goToResult(finalScore, finalStreak, rounds), 1600);
    }
  }

  private goToResult(score: number, bestStreak: number, rounds: number): void {
    this.router.navigate(['/play/result'], {
      state: {
        mode: 'SURVIVAL',
        score,
        bestStreak,
        rounds,
        won: rounds >= 5,
      },
    });
  }
}
