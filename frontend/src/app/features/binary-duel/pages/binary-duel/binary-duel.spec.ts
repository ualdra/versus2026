import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { BinaryDuel } from './binary-duel';
import { AuthService } from '../../../../core/services/auth.service';
import { DuelService } from '../../../../core/services/duel.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import { DuelEvent, QuestionPayload, RoundResultPayload } from '../../../../core/models/duel.models';

const SELF_ID = 'user-self';
const OPP_ID = 'user-opp';
const Q_ID = 'q-1';
const CORRECT_OPT = 'opt-correct';
const WRONG_OPT = 'opt-wrong';

function questionPayload(): QuestionPayload {
  return {
    roundNumber: 1,
    serverNow: new Date().toISOString(),
    deadline: new Date(Date.now() + 15_000).toISOString(),
    timerSeconds: 15,
    effectsApplied: {},
    question: {
      id: Q_ID,
      type: 'BINARY',
      text: '¿Es A correcta?',
      category: 'general',
      scrapedAt: null,
      options: [
        { id: CORRECT_OPT, text: 'A' },
        { id: WRONG_OPT, text: 'B' },
      ],
    },
  };
}

function roundResult(): RoundResultPayload {
  return {
    roundNumber: 1,
    questionId: Q_ID,
    reveal: { correctOptionId: CORRECT_OPT, correctValue: null },
    outcomes: [
      { userId: SELF_ID, answered: true, isCorrect: true, deviation: null, valueGiven: null, optionGiven: CORRECT_OPT, lifeDelta: 0 },
      { userId: OPP_ID, answered: true, isCorrect: false, deviation: null, valueGiven: null, optionGiven: WRONG_OPT, lifeDelta: -1 },
    ],
    runtime: {
      [SELF_ID]: { userId: SELF_ID, livesRemaining: 3, score: 50, currentStreak: 1, sabotageTokens: 0, pendingIncomingEffects: [] },
      [OPP_ID]: { userId: OPP_ID, livesRemaining: 2, score: 0, currentStreak: 0, sabotageTokens: 0, pendingIncomingEffects: [] },
    },
  };
}

describe('BinaryDuel', () => {
  let component: BinaryDuel;
  let fixture: ComponentFixture<BinaryDuel>;
  let events$: Subject<DuelEvent>;
  let duelSpy: { duelEvents$: ReturnType<typeof vi.fn>; sendAnswerOption: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    events$ = new Subject<DuelEvent>();
    duelSpy = {
      duelEvents$: vi.fn(() => events$.asObservable()),
      sendAnswerOption: vi.fn(),
    };
    routerSpy = { navigate: vi.fn().mockResolvedValue(true) };

    await TestBed.configureTestingModule({
      imports: [BinaryDuel],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ matchId: 'match-1' }) } } },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: { user: () => ({ id: SELF_ID, username: 'me' }) } },
        { provide: DuelService, useValue: duelSpy },
        { provide: WebSocketService, useValue: { connect: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BinaryDuel);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
  });

  it('connects WS and subscribes to duelEvents$', () => {
    expect(duelSpy.duelEvents$).toHaveBeenCalledWith('match-1');
    expect(component.phase()).toBe('connecting');
  });

  it('renders question on QUESTION event and moves to idle', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    expect(component.phase()).toBe('idle');
    expect(component.currentQuestion()?.id).toBe(Q_ID);
    expect(component.roundNumber()).toBe(1);
  });

  it('sends answer via duel.service when an option is picked', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    component.pick(CORRECT_OPT);
    expect(duelSpy.sendAnswerOption).toHaveBeenCalledWith('match-1', Q_ID, CORRECT_OPT);
    expect(component.phase()).toBe('answered');
  });

  it('ignores ANSWER_RESULT when accepted; resets phase on rejection', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    component.pick(CORRECT_OPT);
    events$.next({ type: 'ANSWER_RESULT', matchId: 'match-1', payload: { accepted: true, rejectionReason: null, isCorrect: null, deviation: null } });
    expect(component.phase()).toBe('answered');
    events$.next({ type: 'ANSWER_RESULT', matchId: 'match-1', payload: { accepted: false, rejectionReason: 'STALE', isCorrect: null, deviation: null } });
    expect(component.phase()).toBe('idle');
    expect(component.errorMsg()).toContain('STALE');
  });

  it('updates runtime snapshots and reveals correct option on ROUND_RESULT', () => {
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    events$.next({ type: 'ROUND_RESULT', matchId: 'match-1', payload: roundResult() });
    expect(component.phase()).toBe('between');
    expect(component.selfLives()).toBe(3);
    expect(component.oppLives()).toBe(2);
    expect(component.selfScore()).toBe(50);
    expect(component.correctOptionId()).toBe(CORRECT_OPT);
  });

  it('navigates to /play/result on MATCH_END with correct state', async () => {
    vi.useFakeTimers();
    events$.next({ type: 'QUESTION', matchId: 'match-1', payload: questionPayload() });
    events$.next({
      type: 'MATCH_END',
      matchId: 'match-1',
      payload: {
        winnerUserId: SELF_ID,
        reason: 'NORMAL',
        stats: [
          { userId: SELF_ID, username: 'me', result: 'WIN', livesRemaining: 2, score: 150, bestStreakInMatch: 3, roundsPlayed: 5, avgDeviation: null, sabotagesUsed: 0 },
          { userId: OPP_ID, username: 'opp', result: 'LOSS', livesRemaining: 0, score: 80, bestStreakInMatch: 1, roundsPlayed: 5, avgDeviation: null, sabotagesUsed: 0 },
        ],
      },
    });
    vi.advanceTimersByTime(1500);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/play/result'], expect.objectContaining({
      state: expect.objectContaining({
        mode: 'BINARY_DUEL',
        multiplayer: true,
        outcome: 'WIN',
        opponent: expect.objectContaining({ username: 'opp', score: 80 }),
      }),
    }));
  });
});
