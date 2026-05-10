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
}

export interface QuestionBinary {
  id: string;
  type: 'BINARY';
  text: string;
  category: string;
  options: QuestionOption[];
  scrapedAt: string | null;
}

export interface QuestionNumeric {
  id: string;
  type: 'NUMERIC';
  text: string;
  category: string;
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

export interface PlayerStats {
  mode: GameMode;
  gamesPlayed: number;
  gamesWon: number;
  winRate: number;
  bestStreak: number;
  currentStreak: number;
  avgDeviation: number | null;
}
