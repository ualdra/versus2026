import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';
import { DuelService } from '../../../../core/services/duel.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import {
  DuelEvent,
  MatchEndPayload,
  PlayerRoundOutcome,
  PlayerRuntimeSnapshot,
  QuestionPayload,
  RoundResultPayload,
} from '../../../../core/models/duel.models';
import { QuestionBinary } from '../../../../core/models/game.models';

type Phase = 'connecting' | 'idle' | 'answered' | 'between' | 'ended';

interface OpponentMeta {
  userId: string;
  username: string;
}

@Component({
  selector: 'app-binary-duel',
  standalone: true,
  imports: [RouterLink, UpperCasePipe],
  templateUrl: './binary-duel.html',
  styleUrl: './binary-duel.scss',
})
export class BinaryDuel implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly duel = inject(DuelService);
  private readonly ws = inject(WebSocketService);

  readonly matchId = signal<string | null>(null);
  readonly phase = signal<Phase>('connecting');
  readonly currentQuestion = signal<QuestionBinary | null>(null);
  readonly roundNumber = signal<number>(0);
  readonly deadline = signal<number | null>(null);
  readonly serverOffsetMs = signal<number>(0);
  readonly secondsLeft = signal<number>(0);
  readonly pickedOptionId = signal<string | null>(null);
  readonly correctOptionId = signal<string | null>(null);
  readonly lastRoundResult = signal<RoundResultPayload | null>(null);
  readonly errorMsg = signal<string | null>(null);

  readonly selfId = computed(() => this.auth.user()?.id ?? null);
  readonly selfRuntime = signal<PlayerRuntimeSnapshot | null>(null);
  readonly opponent = signal<OpponentMeta | null>(null);
  readonly oppRuntime = signal<PlayerRuntimeSnapshot | null>(null);

  readonly selfLives = computed(() => this.selfRuntime()?.livesRemaining ?? 3);
  readonly oppLives = computed(() => this.oppRuntime()?.livesRemaining ?? 3);
  readonly selfScore = computed(() => this.selfRuntime()?.score ?? 0);
  readonly oppScore = computed(() => this.oppRuntime()?.score ?? 0);
  readonly selfStreak = computed(() => this.selfRuntime()?.currentStreak ?? 0);

  readonly selfOutcome = computed<PlayerRoundOutcome | null>(() => {
    const id = this.selfId();
    return this.lastRoundResult()?.outcomes.find((o) => o.userId === id) ?? null;
  });
  readonly oppOutcome = computed<PlayerRoundOutcome | null>(() => {
    const id = this.selfId();
    return this.lastRoundResult()?.outcomes.find((o) => o.userId !== id) ?? null;
  });

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
    this.sub = this.duel.duelEvents$(id).subscribe((event) => this.handleEvent(event));
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.stopTimer();
  }

  pick(optionId: string): void {
    if (this.phase() !== 'idle') return;
    const q = this.currentQuestion();
    const mid = this.matchId();
    if (!q || !mid) return;
    this.pickedOptionId.set(optionId);
    this.phase.set('answered');
    this.duel.sendAnswerOption(mid, q.id, optionId);
  }

  optionClass(optionId: string): string {
    const picked = this.pickedOptionId();
    const correct = this.correctOptionId();
    const phase = this.phase();
    const base = 'vs-bopt';
    if (phase === 'between' || phase === 'ended') {
      if (correct && optionId === correct) return `${base} vs-bopt--correct`;
      if (picked === optionId) return `${base} vs-bopt--wrong`;
      return `${base} vs-bopt--muted`;
    }
    if (phase === 'answered' && picked === optionId) return `${base} vs-bopt--picked`;
    return `${base} vs-bopt--idle`;
  }

  private handleEvent(event: DuelEvent): void {
    switch (event.type) {
      case 'QUESTION': return this.onQuestion(event.payload);
      case 'ANSWER_RESULT':
        if (!event.payload.accepted) {
          this.errorMsg.set(`Respuesta rechazada (${event.payload.rejectionReason}).`);
          this.phase.set('idle');
          this.pickedOptionId.set(null);
        }
        return;
      case 'ROUND_RESULT': return this.onRoundResult(event.payload);
      case 'MATCH_END': return this.onMatchEnd(event.payload);
      default: return;
    }
  }

  private onQuestion(payload: QuestionPayload): void {
    if (payload.question.type !== 'BINARY') {
      this.errorMsg.set('Pregunta no binaria recibida en duelo binario.');
      return;
    }
    this.currentQuestion.set(payload.question as QuestionBinary);
    this.roundNumber.set(payload.roundNumber);
    this.pickedOptionId.set(null);
    this.correctOptionId.set(null);
    this.errorMsg.set(null);
    this.phase.set('idle');

    const serverNow = new Date(payload.serverNow).getTime();
    const offset = serverNow - Date.now();
    this.serverOffsetMs.set(offset);
    this.deadline.set(new Date(payload.deadline).getTime());
    this.startTimer();
  }

  private onRoundResult(payload: RoundResultPayload): void {
    this.lastRoundResult.set(payload);
    this.correctOptionId.set(payload.reveal.correctOptionId ?? null);
    this.phase.set('between');
    this.stopTimer();
    const selfId = this.selfId();
    if (selfId) {
      const selfSnap = payload.runtime[selfId];
      if (selfSnap) this.selfRuntime.set(selfSnap);
    }
    const opp = this.opponent();
    if (opp) {
      const oppSnap = payload.runtime[opp.userId];
      if (oppSnap) this.oppRuntime.set(oppSnap);
    } else {
      // Inferir oponente del primer userId distinto al propio.
      const ids = Object.keys(payload.runtime);
      const oppId = ids.find((id) => id !== selfId);
      if (oppId) {
        const oppOutcome = payload.outcomes.find((o) => o.userId === oppId);
        this.opponent.set({ userId: oppId, username: 'Rival' });
        this.oppRuntime.set(payload.runtime[oppId]);
        if (oppOutcome) {
          // nothing more for now
        }
      }
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
        mode: 'BINARY_DUEL',
        multiplayer: true,
        outcome,
        score: self?.score ?? 0,
        bestStreak: self?.bestStreakInMatch ?? 0,
        rounds: self?.roundsPlayed ?? 0,
        livesRemaining: self?.livesRemaining ?? 0,
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
    }), 1400);
  }

  private startTimer(): void {
    this.stopTimer();
    this.recomputeSecondsLeft();
    this.timer = setInterval(() => this.recomputeSecondsLeft(), 200);
  }

  private stopTimer(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }

  private recomputeSecondsLeft(): void {
    const dl = this.deadline();
    if (dl == null) {
      this.secondsLeft.set(0);
      return;
    }
    const offset = this.serverOffsetMs();
    const ms = dl - (Date.now() + offset);
    this.secondsLeft.set(Math.max(0, Math.ceil(ms / 1000)));
  }
}
