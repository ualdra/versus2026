import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Subject, of } from 'rxjs';
import { describe, beforeEach, afterEach, it, expect, vi } from 'vitest';

import { Lobby } from './lobby';
import { AuthService } from '../../../../core/services/auth.service';
import { MatchService } from '../../../../core/services/match.service';
import { WebSocketService } from '../../../../core/services/websocket.service';
import { LobbyState } from '../../../../core/models/match.models';
import { MatchEvent } from '../../../../core/models/ws.models';

const SELF_ID = 'user-self';
const OPP_ID = 'user-opponent';

const initialLobby: LobbyState = {
  matchId: 'match-1',
  mode: 'BINARY_DUEL',
  status: 'WAITING',
  requiredPlayers: 2,
  roomCode: 'AB1234',
  players: [
    { userId: SELF_ID, username: 'me', avatarUrl: null, ready: false },
    { userId: OPP_ID, username: 'opp', avatarUrl: null, ready: false },
  ],
};

describe('Lobby', () => {
  let component: Lobby;
  let fixture: ComponentFixture<Lobby>;
  let events$: Subject<MatchEvent<unknown>>;
  let matchSpy: {
    getLobby: ReturnType<typeof vi.fn>;
    lobbyEvents$: ReturnType<typeof vi.fn>;
    sendReady: ReturnType<typeof vi.fn>;
    sendUnready: ReturnType<typeof vi.fn>;
    abandonMatch: ReturnType<typeof vi.fn>;
  };
  let wsSpy: { connect: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    events$ = new Subject<MatchEvent<unknown>>();
    matchSpy = {
      getLobby: vi.fn(() => of(structuredClone(initialLobby))),
      lobbyEvents$: vi.fn(() => events$.asObservable()),
      sendReady: vi.fn(),
      sendUnready: vi.fn(),
      abandonMatch: vi.fn(() => of(void 0)),
    };
    wsSpy = { connect: vi.fn() };
    routerSpy = { navigate: vi.fn().mockResolvedValue(true) };

    const authStub = {
      user: () => ({ id: SELF_ID, username: 'me', role: 'PLAYER', avatarUrl: null }),
    };

    await TestBed.configureTestingModule({
      imports: [Lobby],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ matchId: 'match-1' }) } } },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: authStub },
        { provide: MatchService, useValue: matchSpy },
        { provide: WebSocketService, useValue: wsSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Lobby);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
    fixture.destroy();
  });

  it('loads lobby and subscribes to events on init', () => {
    expect(matchSpy.getLobby).toHaveBeenCalledWith('match-1');
    expect(matchSpy.lobbyEvents$).toHaveBeenCalledWith('match-1');
    expect(wsSpy.connect).toHaveBeenCalled();
    expect(component.status()).toBe('lobby');
    expect(component.lobby()?.players.length).toBe(2);
  });

  it('redirects when matchId is missing', async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Lobby],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
        { provide: Router, useValue: routerSpy },
        { provide: AuthService, useValue: { user: () => null } },
        { provide: MatchService, useValue: matchSpy },
        { provide: WebSocketService, useValue: wsSpy },
      ],
    }).compileComponents();
    const f = TestBed.createComponent(Lobby);
    f.detectChanges();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/play/select']);
  });

  it('updates a player ready flag on PLAYER_READY event', () => {
    events$.next({
      type: 'PLAYER_READY',
      matchId: 'match-1',
      payload: { userId: SELF_ID, ready: true },
    });
    expect(component.selfReady()).toBe(true);
    expect(component.lobby()?.players.find((p) => p.userId === SELF_ID)?.ready).toBe(true);
  });

  it('starts countdown on MATCH_STARTING and decrements', () => {
    vi.useFakeTimers();
    events$.next({
      type: 'MATCH_STARTING',
      matchId: 'match-1',
      payload: { countdownSeconds: 3 },
    });
    expect(component.status()).toBe('starting');
    expect(component.countdown()).toBe(3);
    vi.advanceTimersByTime(1000);
    expect(component.countdown()).toBe(2);
    vi.advanceTimersByTime(2000);
    expect(component.countdown()).toBe(0);
  });

  it('switches to started status on MATCH_START', () => {
    events$.next({ type: 'MATCH_START', matchId: 'match-1', payload: { matchId: 'match-1', mode: 'BINARY_DUEL' } });
    expect(component.status()).toBe('started');
  });

  it('sends ready when toggling from unready', () => {
    component.toggleReady();
    expect(matchSpy.sendReady).toHaveBeenCalledWith('match-1');
    expect(matchSpy.sendUnready).not.toHaveBeenCalled();
  });

  it('sends unready when toggling from ready', () => {
    events$.next({
      type: 'PLAYER_READY',
      matchId: 'match-1',
      payload: { userId: SELF_ID, ready: true },
    });
    component.toggleReady();
    expect(matchSpy.sendUnready).toHaveBeenCalledWith('match-1');
  });

  it('cancel calls abandonMatch and navigates to /play/select', async () => {
    component.cancel();
    expect(matchSpy.abandonMatch).toHaveBeenCalledWith('match-1');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/play/select']);
  });

  it('removes opponent and goes to "left" on PLAYER_LEFT', () => {
    vi.useFakeTimers();
    events$.next({ type: 'PLAYER_LEFT', matchId: 'match-1', payload: { userId: OPP_ID } });
    expect(component.status()).toBe('left');
    expect(component.lobby()?.players.some((p) => p.userId === OPP_ID)).toBe(false);
    vi.advanceTimersByTime(2000);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/play/select']);
  });
});
