import { Achievement } from './achievement.models';

export type QuestionType = 'BINARY' | 'NUMERIC';
export type GameMode =
  | 'SURVIVAL'
  | 'PRECISION'
  | 'BINARY_DUEL'
  | 'PRECISION_DUEL'
  | 'SABOTAGE';

export interface QuestionOption {
  id: string;
  text: string;
  sub?: string | null;
  unit?: string | null;
}

export interface QuestionBinary {
  id: string;
  type: 'BINARY';
  text: string;
  category: string;
  subcategory?: string | null;
  inverse?: boolean;
  options: QuestionOption[];
  scrapedAt: string | null;
}

export interface QuestionNumeric {
  id: string;
  type: 'NUMERIC';
  text: string;
  category: string;
  subcategory?: string | null;
  unit: string | null;
  scrapedAt: string | null;
}

export type Question = QuestionBinary | QuestionNumeric;

export interface StartGameResponse {
  sessionId: string;
  question: Question;
}

export interface SurvivalAnswerRequest {
  sessionId: string;
  questionId: string;
  optionId: string;
}

export interface SurvivalAnswerResponse {
  correct: boolean;
  livesRemaining: number;
  lifeDelta: number;
  streak: number;
  scoreDelta: number;
  nextQuestion?: Question | null;
  gameOver: boolean;
  achievementsUnlocked?: Achievement[];
  revealedValues?: Record<string, number>;
}

export interface PrecisionAnswerRequest {
  sessionId: string;
  questionId: string;
  value: number;
}

export interface PrecisionAnswerResponse {
  correctValue: number;
  deviation: number;
  deviationPercent: number;
  lifeDelta: number;
  livesRemaining: number;
  nextQuestion?: Question | null;
  gameOver: boolean;
  achievementsUnlocked?: Achievement[];
}

export interface PracticeAnswerRequest {
  questionId: string;
  optionId?: string;
  value?: number;
}

export interface PracticeAnswerResponse {
  correct: boolean;
  correctOptionId?: string;
  correctValue?: number;
  deviationPercent?: number;
  unit?: string;
  explanation?: string | null;
}

export interface PlayerStats {
  mode: GameMode;
  gamesPlayed: number;
  gamesWon: number;
  winRate: number;
  bestStreak: number;
  currentStreak: number;
  avgDeviation: number | null;
  avgScore: number | null;
}

export interface PlayerStatsOverview {
  byMode: PlayerStats[];
  favoriteMode: GameMode | null;
  totalPlayTimeSeconds: number;
}

export interface OpponentSummary {
  id: string;
  username: string;
  avatarUrl: string | null;
}

export interface MatchHistoryItem {
  id: string;
  mode: GameMode;
  result: 'WIN' | 'LOSS' | 'DRAW' | 'ABANDONED' | null;
  score: number;
  bestStreak: number;
  livesRemaining: number;
  roundsPlayed: number;
  finishedAt: string;
  opponent: OpponentSummary | null;
}

export interface RoundDetail {
  roundNumber: number;
  questionId: string;
  questionText: string;
  correct: boolean;
  answerGiven: string;
  deviation: number | null;
}

export interface MatchDetail {
  id: string;
  mode: GameMode;
  createdAt: string;
  finishedAt: string;
  players: {
    userId: string;
    username: string;
    score: number;
    livesRemaining: number;
    bestStreakInMatch: number;
    result: 'WIN' | 'LOSS' | 'DRAW' | 'ABANDONED' | null;
  }[];
  rounds: RoundDetail[];
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last: boolean;
}
