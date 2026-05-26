import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';
import { DuelService } from '../../../../core/services/duel.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import { SabotagePanelComponent } from '../../components/sabotage-panel/sabotage-panel';
import { EffectIndicatorComponent } from '../../components/effect-indicator/effect-indicator';
import {
  DuelEvent,
  EffectAppliedPayload,
  MatchEndPayload,
  PlayerRoundOutcome,
  PlayerRuntimeSnapshot,
  QuestionPayload,
  RoundResultPayload,
  SabotageActivatedPayload,
  SabotageRejectedPayload,
  SabotageType,
} from '../../../../core/models/duel.models';
import { QuestionBinary } from '../../../../core/models/game.models';

type Phase = 'connecting' | 'idle' | 'answered' | 'between' | 'ended';

@Component({
  selector: 'app-sabotage',
  standalone: true,
  imports: [RouterLink, UpperCasePipe, SabotagePanelComponent, EffectIndicatorComponent],
  templateUrl: './sabotage.html',
  styleUrl: './sabotage.scss',
})
export class Sabotage implements OnInit, OnDestroy {
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
  readonly lastOutcomes = signal<PlayerRoundOutcome[]>([]);
  readonly errorMsg = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly selfId = computed(() => this.auth.user()?.id ?? null);
  readonly selfRuntime = signal<PlayerRuntimeSnapshot | null>(null);
  readonly oppRuntime = signal<PlayerRuntimeSnapshot | null>(null);
  readonly oppUserId = signal<string | null>(null);
  /** Efecto activo aplicado al SELF en este round (visualizado como overlay). */
  readonly activeEffectAgainstSelf = signal<SabotageType | null>(null);

  readonly selfLives = computed(() => this.selfRuntime()?.livesRemaining ?? 3);
  readonly oppLives = computed(() => this.oppRuntime()?.livesRemaining ?? 3);
  readonly selfTokens = computed(() => this.selfRuntime()?.sabotageTokens ?? 0);
  readonly selfStreak = computed(() => this.selfRuntime()?.currentStreak ?? 0);
  readonly canActivateSabotage = computed(() =>
    this.phase() === 'idle' && this.selfTokens() > 0 && this.pickedOptionId() === null
  );

  readonly selfOutcome = computed<PlayerRoundOutcome | null>(() => {
    const id = this.selfId();
    return this.lastOutcomes().find((o) => o.userId === id) ?? null;
  });
  readonly oppOutcome = computed<PlayerRoundOutcome | null>(() => {
    const id = this.selfId();
    return this.lastOutcomes().find((o) => o.userId !== id) ?? null;
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
    this.sub = this.duel.duelEvents$(id).subscribe((e) => this.handleEvent(e));
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

  activateSabotage(type: SabotageType): void {
    const mid = this.matchId();
    const target = this.oppUserId();
    if (!mid || !target) return;
    this.duel.sendSabotage(mid, type, target);
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
      case 'SABOTAGE_ACTIVATED': return this.onSabotageActivated(event.payload);
      case 'SABOTAGE_REJECTED': return this.onSabotageRejected(event.payload);
      case 'EFFECT_APPLIED': return this.onEffectApplied(event.payload);
      default: return;
    }
  }

  private onQuestion(payload: QuestionPayload): void {
    if (payload.question.type !== 'BINARY') {
      this.errorMsg.set('Pregunta no binaria en modo sabotaje.');
      return;
    }
    this.currentQuestion.set(payload.question as QuestionBinary);
    this.roundNumber.set(payload.roundNumber);
    this.pickedOptionId.set(null);
    this.correctOptionId.set(null);
    this.lastOutcomes.set([]);
    this.errorMsg.set(null);
    this.notice.set(null);
    this.phase.set('idle');

    const selfId = this.selfId();
    const myEffect = selfId ? payload.effectsApplied[selfId] : null;
    this.activeEffectAgainstSelf.set(myEffect ?? null);

    const serverNow = new Date(payload.serverNow).getTime();
    this.serverOffsetMs.set(serverNow - Date.now());
    this.deadline.set(new Date(payload.deadline).getTime());
    this.startTimer();
  }

  private onRoundResult(payload: RoundResultPayload): void {
    this.correctOptionId.set(payload.reveal.correctOptionId ?? null);
    this.lastOutcomes.set(payload.outcomes);
    this.phase.set('between');
    this.stopTimer();
    this.activeEffectAgainstSelf.set(null);

    const selfId = this.selfId();
    if (selfId && payload.runtime[selfId]) this.selfRuntime.set(payload.runtime[selfId]);
    const ids = Object.keys(payload.runtime);
    const oppId = ids.find((id) => id !== selfId);
    if (oppId) {
      this.oppUserId.set(oppId);
      this.oppRuntime.set(payload.runtime[oppId]);
    }
  }

  private onSabotageActivated(payload: SabotageActivatedPayload): void {
    const self = this.selfId();
    if (payload.by === self) {
      this.notice.set(`Has activado ${this.labelFor(payload.type)} (próximo round).`);
    } else if (payload.target === self) {
      this.notice.set(`¡Rival activó ${this.labelFor(payload.type)} contra ti!`);
    }
  }

  private onSabotageRejected(payload: SabotageRejectedPayload): void {
    this.notice.set(`Sabotaje rechazado (${payload.reason}).`);
  }

  private onEffectApplied(payload: EffectAppliedPayload): void {
    const self = this.selfId();
    if (payload.target === self) {
      this.activeEffectAgainstSelf.set(payload.type);
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
        mode: 'SABOTAGE',
        multiplayer: true,
        outcome,
        score: self?.score ?? 0,
        bestStreak: self?.bestStreakInMatch ?? 0,
        rounds: self?.roundsPlayed ?? 0,
        livesRemaining: self?.livesRemaining ?? 0,
        sabotagesUsed: self?.sabotagesUsed ?? 0,
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
    }), 1500);
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

  private labelFor(t: SabotageType): string {
    return t === 'TIME_BOMB' ? 'BOMBA DE TIEMPO'
      : t === 'OBFUSCATION' ? 'OFUSCACIÓN'
      : 'ROBO DE VIDA';
  }
}
