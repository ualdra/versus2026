import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { PrecisionDuel } from './precision-duel';
import { AuthService } from '../../../../core/services/auth.service';
import { DuelService } from '../../../../core/services/duel.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import { DuelEvent, QuestionPayload, RoundResultPayload } from '../../../../core/models/duel.models';

const SELF_ID = 'user-self';
const OPP_ID = 'user-opp';
const Q_ID = 'q-1';

function questionPayload(): QuestionPayload {
  return {
    roundNumber: 1,
    serverNow: new Date().toISOString(),
    deadline: new Date(Date.now() + 15_000).toISOString(),
    timerSeconds: 15,
    effectsApplied: {},
    question: {
      id: Q_ID, type: 'NUMERIC', text: '¿Cuánto pesa la torre?', category: 'historia',
      unit: 'toneladas', scrapedAt: null,
    },
  };
}

function roundResult(): RoundResultPayload {
  return {
    roundNumber: 1, questionId: Q_ID,
    reveal: { correctOptionId: null, correctValue: 7300 },
    outcomes: [
      { userId: SELF_ID, answered: true, isCorrect: true, deviation: 1.5, valueGiven: 7400, optionGiven: null, lifeDelta: 0 },
      { userId: OPP_ID, answered: true, isCorrect: false, deviation: 25.0, valueGiven: 5500, optionGiven: null, lifeDelta: -1 },
    ],
    runtime: {
      [SELF_ID]: { userId: SELF_ID, livesRemaining: 3, score: 100, currentStreak: 1, sabotageTokens: 0, pendingIncomingEffects: [] },
      [OPP_ID]: { userId: OPP_ID, livesRemaining: 2, score: 0, currentStreak: 0, sabotageTokens: 0, pendingIncomingEffects: [] },
    },
  };
}

describe('PrecisionDuel', () => {
  let component: PrecisionDuel;
  let fixture: ComponentFixture<PrecisionDuel>;
  let events$: Subject<DuelEvent>;
  let duelSpy: { duelEvents$: ReturnType<typeof vi.fn>; sendAnswerValue: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    events$ = new Subject<DuelEvent>();
    duelSpy = {
      duelEvents$: vi.fn(() => events$.asObservable()),
      sendAnswerValue: vi.fn(),
    };
    routerSpy = { navigate: vi.fn().mockResolvedValue(true) };

    await TestBed.configureTestingModule({
      imports: [PrecisionDuel],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ matchId: 'match-1' }) } } },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: { user: () => ({ id: SELF_ID, username: 'me' }) } },
        { provide: DuelService, useValue: duelSpy },
        { provide: WebSocketService, useValue: { connect: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PrecisionDuel);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
  });

  it('subscribes to duelEvents$ and starts in connecting', () => {
    expect(duelSpy.duelEvents$).toHaveBeenCalledWith('match-1');
    expect(component.phase()).toBe('connecting');
  });

  it('renders numeric question on QUESTION event', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    expect(component.phase()).toBe('idle');
    expect(component.currentQuestion()?.unit).toBe('toneladas');
  });

  it('sends numeric answer on submit', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    component.submit(7400);
    expect(duelSpy.sendAnswerValue).toHaveBeenCalledWith('match-1', Q_ID, 7400);
    expect(component.phase()).toBe('answered');
  });

  it('computes roundWinner=self when self deviation < opp deviation', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    events$.next({ type: 'ROUND_RESULT', matchId: 'match-1', payload: roundResult() });
    expect(component.phase()).toBe('between');
    expect(component.roundWinner()).toBe('self');
    expect(component.correctValue()).toBe(7300);
    expect(component.selfLives()).toBe(3);
    expect(component.oppLives()).toBe(2);
  });

  it('passes avgDeviation in result state on MATCH_END', () => {
    vi.useFakeTimers();
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    events$.next({
      type: 'MATCH_END',
      matchId: 'match-1',
      payload: {
        winnerUserId: SELF_ID,
        reason: 'NORMAL',
        stats: [
          { userId: SELF_ID, username: 'me', result: 'WIN', livesRemaining: 2, score: 200, bestStreakInMatch: 2, roundsPlayed: 4, avgDeviation: 5.5, sabotagesUsed: 0 },
          { userId: OPP_ID, username: 'opp', result: 'LOSS', livesRemaining: 0, score: 100, bestStreakInMatch: 1, roundsPlayed: 4, avgDeviation: 18.2, sabotagesUsed: 0 },
        ],
      },
    });
    vi.advanceTimersByTime(1700);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/play/result'], expect.objectContaining({
      state: expect.objectContaining({
        mode: 'PRECISION_DUEL',
        multiplayer: true,
        outcome: 'WIN',
        avgDeviation: 5.5,
        opponent: expect.objectContaining({ avgDeviation: 18.2 }),
      }),
    }));
  });
});
