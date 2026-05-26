import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Sabotage } from './sabotage';
import { AuthService } from '../../../../core/services/auth.service';
import { DuelService } from '../../../../core/services/duel.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import { DuelEvent, QuestionPayload, RoundResultPayload } from '../../../../core/models/duel.models';

const SELF_ID = 'user-self';
const OPP_ID = 'user-opp';
const Q_ID = 'q-1';
const CORRECT_OPT = 'opt-correct';
const WRONG_OPT = 'opt-wrong';

function questionPayload(effectAgainstSelf = false): QuestionPayload {
  return {
    roundNumber: 1,
    serverNow: new Date().toISOString(),
    deadline: new Date(Date.now() + 15_000).toISOString(),
    timerSeconds: 15,
    effectsApplied: effectAgainstSelf ? { [SELF_ID]: 'TIME_BOMB' } : {},
    question: {
      id: Q_ID, type: 'BINARY', text: '¿OK?', category: 'g', scrapedAt: null,
      options: [
        { id: CORRECT_OPT, text: 'A' },
        { id: WRONG_OPT, text: 'B' },
      ],
    },
  };
}

function roundResult(selfTokens: number): RoundResultPayload {
  return {
    roundNumber: 1, questionId: Q_ID,
    reveal: { correctOptionId: CORRECT_OPT, correctValue: null },
    outcomes: [
      { userId: SELF_ID, answered: true, isCorrect: true, deviation: null, valueGiven: null, optionGiven: CORRECT_OPT, lifeDelta: 0 },
      { userId: OPP_ID, answered: true, isCorrect: false, deviation: null, valueGiven: null, optionGiven: WRONG_OPT, lifeDelta: -1 },
    ],
    runtime: {
      [SELF_ID]: { userId: SELF_ID, livesRemaining: 3, score: 50, currentStreak: 3, sabotageTokens: selfTokens, pendingIncomingEffects: [] },
      [OPP_ID]: { userId: OPP_ID, livesRemaining: 2, score: 0, currentStreak: 0, sabotageTokens: 0, pendingIncomingEffects: [] },
    },
  };
}

describe('Sabotage', () => {
  let component: Sabotage;
  let fixture: ComponentFixture<Sabotage>;
  let events$: Subject<DuelEvent>;
  let duelSpy: {
    duelEvents$: ReturnType<typeof vi.fn>;
    sendAnswerOption: ReturnType<typeof vi.fn>;
    sendSabotage: ReturnType<typeof vi.fn>;
  };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    events$ = new Subject<DuelEvent>();
    duelSpy = {
      duelEvents$: vi.fn(() => events$.asObservable()),
      sendAnswerOption: vi.fn(),
      sendSabotage: vi.fn(),
    };
    routerSpy = { navigate: vi.fn().mockResolvedValue(true) };

    await TestBed.configureTestingModule({
      imports: [Sabotage],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ matchId: 'match-1' }) } } },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: { user: () => ({ id: SELF_ID, username: 'me' }) } },
        { provide: DuelService, useValue: duelSpy },
        { provide: WebSocketService, useValue: { connect: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Sabotage);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
  });

  it('renders question on QUESTION event and exposes initial state', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    expect(component.phase()).toBe('idle');
    expect(component.activeEffectAgainstSelf()).toBeNull();
  });

  it('detects active effect on self from effectsApplied map', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload(true) });
    expect(component.activeEffectAgainstSelf()).toBe('TIME_BOMB');
  });

  it('tracks tokens from ROUND_RESULT and unlocks panel', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    events$.next({ type: 'ROUND_RESULT', matchId: 'match-1', payload: roundResult(2) });
    expect(component.selfTokens()).toBe(2);
  });

  it('publishes WS sabotage with rival as target', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    events$.next({ type: 'ROUND_RESULT', matchId: 'match-1', payload: roundResult(1) });
    // Volver a abrir un round para que canActivateSabotage = true
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: { ...questionPayload(), roundNumber: 2 } });
    component.activateSabotage('OBFUSCATION');
    expect(duelSpy.sendSabotage).toHaveBeenCalledWith('match-1', 'OBFUSCATION', OPP_ID);
  });

  it('shows notice on SABOTAGE_REJECTED', () => {
    events$.next({
      type: 'SABOTAGE_REJECTED',
      matchId: 'match-1',
      payload: { reason: 'NO_TOKENS' },
    });
    expect(component.notice()).toContain('NO_TOKENS');
  });

  it('sets active effect on EFFECT_APPLIED targeting self', () => {
    events$.next({
      type: 'EFFECT_APPLIED',
      matchId: 'match-1',
      payload: { type: 'OBFUSCATION', target: SELF_ID, roundNumber: 2 },
    });
    expect(component.activeEffectAgainstSelf()).toBe('OBFUSCATION');
  });

  it('navigates to /play/result on MATCH_END with sabotaje state', () => {
    vi.useFakeTimers();
    events$.next({
      type: 'MATCH_END',
      matchId: 'match-1',
      payload: {
        winnerUserId: SELF_ID,
        reason: 'NORMAL',
        stats: [
          { userId: SELF_ID, username: 'me', result: 'WIN', livesRemaining: 2, score: 200, bestStreakInMatch: 4, roundsPlayed: 6, avgDeviation: null, sabotagesUsed: 2 },
          { userId: OPP_ID, username: 'opp', result: 'LOSS', livesRemaining: 0, score: 100, bestStreakInMatch: 1, roundsPlayed: 6, avgDeviation: null, sabotagesUsed: 1 },
        ],
      },
    });
    vi.advanceTimersByTime(1600);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/play/result'], expect.objectContaining({
      state: expect.objectContaining({
        mode: 'SABOTAGE',
        multiplayer: true,
        outcome: 'WIN',
        sabotagesUsed: 2,
      }),
    }));
  });
});
