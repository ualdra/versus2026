import { Question } from './game.models';

export type SabotageType = 'TIME_BOMB' | 'OBFUSCATION' | 'LIFE_STEAL';

export interface QuestionPayload {
  roundNumber: number;
  question: Question;
  serverNow: string;
  deadline: string;
  timerSeconds: number;
  effectsApplied: Record<string, SabotageType>;
}

export interface AnswerResultPayload {
  accepted: boolean;
  rejectionReason: string | null;
  isCorrect: boolean | null;
  deviation: number | null;
}

export interface PlayerRoundOutcome {
  userId: string;
  answered: boolean;
  isCorrect: boolean | null;
  deviation: number | null;
  valueGiven: number | null;
  optionGiven: string | null;
  lifeDelta: number;
}

export interface PlayerRuntimeSnapshot {
  userId: string;
  livesRemaining: number;
  score: number;
  currentStreak: number;
  sabotageTokens: number;
  pendingIncomingEffects: SabotageType[];
}

export interface RoundResultPayload {
  roundNumber: number;
  questionId: string;
  reveal: {
    correctOptionId: string | null;
    correctValue: number | null;
  };
  outcomes: PlayerRoundOutcome[];
  runtime: Record<string, PlayerRuntimeSnapshot>;
}

export interface FinalStatsPayload {
  userId: string;
  username: string;
  result: 'WIN' | 'LOSS' | 'DRAW' | 'ABANDONED';
  livesRemaining: number;
  score: number;
  bestStreakInMatch: number;
  roundsPlayed: number;
  avgDeviation: number | null;
  sabotagesUsed: number;
}

export interface MatchEndPayload {
  winnerUserId: string | null;
  reason: 'NORMAL' | 'DISCONNECT' | 'MAX_ROUNDS_TIE';
  stats: FinalStatsPayload[];
}

export interface SabotageActivatedPayload {
  type: SabotageType;
  by: string;
  target: string;
  appliesOnRound: number;
}

export interface SabotageRejectedPayload {
  reason: 'NO_TOKENS' | 'ALREADY_USED' | 'INVALID_TARGET' | 'WRONG_PHASE' | 'UNSUPPORTED_MODE';
}

export interface EffectAppliedPayload {
  type: SabotageType;
  target: string;
  roundNumber: number;
}

export interface AnswerMessage {
  matchId: string;
  questionId: string;
  optionId?: string;
  value?: number;
}

export interface SabotageMessage {
  matchId: string;
  type: SabotageType;
  targetUserId: string;
}

export type DuelEvent =
  | { type: 'QUESTION'; matchId: string; payload: QuestionPayload }
  | { type: 'ANSWER_RESULT'; matchId: string; payload: AnswerResultPayload }
  | { type: 'ROUND_RESULT'; matchId: string; payload: RoundResultPayload }
  | { type: 'MATCH_END'; matchId: string; payload: MatchEndPayload }
  | { type: 'SABOTAGE_ACTIVATED'; matchId: string; payload: SabotageActivatedPayload }
  | { type: 'SABOTAGE_REJECTED'; matchId: string; payload: SabotageRejectedPayload }
  | { type: 'EFFECT_APPLIED'; matchId: string; payload: EffectAppliedPayload };
