import { Injectable, inject } from '@angular/core';
import { Observable, merge, shareReplay } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import {
  AnswerMessage,
  DuelEvent,
  SabotageMessage,
  SabotageType,
} from '../models/duel.models';
import { MatchEvent } from '../models/ws.models';
import { WebSocketService } from './websocket.service';

const DUEL_EVENT_TYPES = new Set<DuelEvent['type']>([
  'QUESTION',
  'ANSWER_RESULT',
  'ROUND_RESULT',
  'MATCH_END',
  'SABOTAGE_ACTIVATED',
  'SABOTAGE_REJECTED',
  'EFFECT_APPLIED',
]);

@Injectable({ providedIn: 'root' })
export class DuelService {
  private readonly ws = inject(WebSocketService);
  private readonly streams = new Map<string, Observable<DuelEvent>>();

  duelEvents$(matchId: string): Observable<DuelEvent> {
    const cached = this.streams.get(matchId);
    if (cached) return cached;

    const topic$ = this.ws
      .subscribe<MatchEvent<unknown>>(`/topic/match/${matchId}`)
      .pipe(
        filter((e) => DUEL_EVENT_TYPES.has(e.type as DuelEvent['type'])),
        map((e) => e as unknown as DuelEvent),
      );

    const personal$ = this.ws
      .subscribe<MatchEvent<unknown>>('/user/queue/match')
      .pipe(
        filter((e) => e.matchId === matchId && DUEL_EVENT_TYPES.has(e.type as DuelEvent['type'])),
        map((e) => e as unknown as DuelEvent),
      );

    const stream$ = merge(topic$, personal$).pipe(shareReplay({ bufferSize: 0, refCount: true }));
    this.streams.set(matchId, stream$);
    return stream$;
  }

  sendAnswerOption(matchId: string, questionId: string, optionId: string): void {
    const msg: AnswerMessage = { matchId, questionId, optionId };
    this.ws.publish('/app/match/answer', msg);
  }

  sendAnswerValue(matchId: string, questionId: string, value: number): void {
    const msg: AnswerMessage = { matchId, questionId, value };
    this.ws.publish('/app/match/answer', msg);
  }

  sendSabotage(matchId: string, type: SabotageType, targetUserId: string): void {
    const msg: SabotageMessage = { matchId, type, targetUserId };
    this.ws.publish('/app/match/sabotage', msg);
  }
}
