import { Component, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DecimalPipe, UpperCasePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';
import { DuelService } from '../../../../core/services/duel.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import { NumericInputComponent } from '../../../../shared/components/ui/numeric-input/numeric-input';
import {
  DuelEvent,
  MatchEndPayload,
  PlayerRoundOutcome,
  PlayerRuntimeSnapshot,
  QuestionPayload,
  RoundResultPayload,
} from '../../../../core/models/duel.models';
import { QuestionNumeric } from '../../../../core/models/game.models';

type Phase = 'connecting' | 'idle' | 'answered' | 'between' | 'ended';

@Component({
  selector: 'app-precision-duel',
  standalone: true,
  imports: [RouterLink, NumericInputComponent, DecimalPipe, UpperCasePipe],
  templateUrl: './precision-duel.html',
  styleUrl: './precision-duel.scss',
})
export class PrecisionDuel implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly duel = inject(DuelService);
  private readonly ws = inject(WebSocketService);

  @ViewChild(NumericInputComponent) private input?: NumericInputComponent;

  readonly matchId = signal<string | null>(null);
  readonly phase = signal<Phase>('connecting');
  readonly currentQuestion = signal<QuestionNumeric | null>(null);
  readonly roundNumber = signal<number>(0);
  readonly deadline = signal<number | null>(null);
  readonly serverOffsetMs = signal<number>(0);
  readonly secondsLeft = signal<number>(0);
  readonly correctValue = signal<number | null>(null);
  readonly lastRoundOutcomes = signal<PlayerRoundOutcome[]>([]);
  readonly errorMsg = signal<string | null>(null);

  readonly selfId = computed(() => this.auth.user()?.id ?? null);
  readonly selfRuntime = signal<PlayerRuntimeSnapshot | null>(null);
  readonly oppRuntime = signal<PlayerRuntimeSnapshot | null>(null);
  readonly oppUserId = signal<string | null>(null);

  readonly selfLives = computed(() => this.selfRuntime()?.livesRemaining ?? 3);
  readonly oppLives = computed(() => this.oppRuntime()?.livesRemaining ?? 3);
  readonly selfLifePct = computed(() => Math.max(0, Math.min(100, this.selfLives() * 33.34)));
  readonly oppLifePct = computed(() => Math.max(0, Math.min(100, this.oppLives() * 33.34)));
  readonly inputDisabled = computed(() => this.phase() !== 'idle');

  private sub?: Subscription;
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('matchId');
    if (!id) {
      this.router.navigate(['/play/select']);
      return;
    }
    this.matchId.set(id);
    this.ws.connect();
    this.sub = this.duel.duelEvents$(id).subscribe((e) => this.handleEvent(e));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.stopTimer();
  }

  submit(value: number): void {
    if (this.phase() !== 'idle') return;
    const q = this.currentQuestion();
    const mid = this.matchId();
    if (!q || !mid) return;
    this.phase.set('answered');
    this.duel.sendAnswerValue(mid, q.id, value);
  }

  selfOutcome(): PlayerRoundOutcome | null {
    const id = this.selfId();
    return this.lastRoundOutcomes().find((o) => o.userId === id) ?? null;
  }

  oppOutcome(): PlayerRoundOutcome | null {
    const id = this.selfId();
    return this.lastRoundOutcomes().find((o) => o.userId !== id) ?? null;
  }

  roundWinner(): 'self' | 'opp' | 'tie' | null {
    const self = this.selfOutcome();
    const opp = this.oppOutcome();
    if (!self || !opp || self.deviation == null || opp.deviation == null) return null;
    if (self.deviation < opp.deviation) return 'self';
    if (self.deviation > opp.deviation) return 'opp';
    return 'tie';
  }

  private handleEvent(event: DuelEvent): void {
    switch (event.type) {
      case 'QUESTION': return this.onQuestion(event.payload);
      case 'ANSWER_RESULT':
        if (!event.payload.accepted) {
          this.errorMsg.set(`Respuesta rechazada (${event.payload.rejectionReason}).`);
          this.phase.set('idle');
        }
        return;
      case 'ROUND_RESULT': return this.onRoundResult(event.payload);
      case 'MATCH_END': return this.onMatchEnd(event.payload);
      default: return;
    }
  }

  private onQuestion(payload: QuestionPayload): void {
    if (payload.question.type !== 'NUMERIC') {
      this.errorMsg.set('Pregunta no numérica en duelo de precisión.');
      return;
    }
    this.currentQuestion.set(payload.question as QuestionNumeric);
    this.roundNumber.set(payload.roundNumber);
    this.correctValue.set(null);
    this.lastRoundOutcomes.set([]);
    this.errorMsg.set(null);
    this.input?.reset();
    this.phase.set('idle');

    const serverNow = new Date(payload.serverNow).getTime();
    this.serverOffsetMs.set(serverNow - Date.now());
    this.deadline.set(new Date(payload.deadline).getTime());
    this.startTimer();
  }

  private onRoundResult(payload: RoundResultPayload): void {
    this.correctValue.set(payload.reveal.correctValue);
    this.lastRoundOutcomes.set(payload.outcomes);
    this.phase.set('between');
    this.stopTimer();

    const selfId = this.selfId();
    if (selfId && payload.runtime[selfId]) this.selfRuntime.set(payload.runtime[selfId]);

    const ids = Object.keys(payload.runtime);
    const oppId = ids.find((id) => id !== selfId);
    if (oppId) {
      this.oppUserId.set(oppId);
      this.oppRuntime.set(payload.runtime[oppId]);
    }
  }

  private onMatchEnd(payload: MatchEndPayload): void {
    this.stopTimer();
    this.phase.set('ended');
    const selfId = this.selfId();
    const self = payload.stats.find((s) => s.userId === selfId);
    const opp = payload.stats.find((s) => s.userId !== selfId);
    const outcome = payload.winnerUserId == null
      ? 'DRAW'
      : payload.winnerUserId === selfId ? 'WIN' : 'LOSS';
    setTimeout(() => this.router.navigate(['/play/result'], {
      state: {
        mode: 'PRECISION_DUEL',
        multiplayer: true,
        outcome,
        score: self?.score ?? 0,
        bestStreak: self?.bestStreakInMatch ?? 0,
        rounds: self?.roundsPlayed ?? 0,
        livesRemaining: self?.livesRemaining ?? 0,
        avgDeviation: self?.avgDeviation ?? null,
        won: outcome === 'WIN',
        reason: payload.reason,
        opponent: opp ? {
          username: opp.username,
          avatarUrl: null,
          score: opp.score,
          bestStreak: opp.bestStreakInMatch,
          livesRemaining: opp.livesRemaining,
          avgDeviation: opp.avgDeviation,
        } : undefined,
      },
    }), 1600);
  }

  private startTimer(): void {
    this.stopTimer();
    this.recompute();
    this.timer = setInterval(() => this.recompute(), 200);
  }

  private stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }

  private recompute(): void {
    const dl = this.deadline();
    if (dl == null) return this.secondsLeft.set(0);
    const ms = dl - (Date.now() + this.serverOffsetMs());
    this.secondsLeft.set(Math.max(0, Math.ceil(ms / 1000)));
  }
}
