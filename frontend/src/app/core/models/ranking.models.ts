import { GameMode, MatchHistoryItem, PagedResponse } from './game.models';

export interface RankingEntry {
  userId: string;
  username: string;
  avatarUrl: string | null;
  mode: GameMode;
  rank: number;
  rating: number;
  wins: number;
  losses: number;
  winStreak: number;
  lastDelta: number;
  updatedAt: string;
  currentUser: boolean;
}

export interface RankingSummary {
  mode: GameMode;
  rank: number;
  rating: number;
  wins: number;
  losses: number;
  winStreak: number;
  lastDelta: number;
}

export interface PublicRankingUser {
  id: string;
  username: string;
  avatarUrl: string | null;
  role: string;
  createdAt: string;
}

export interface UserRanking {
  user: PublicRankingUser;
  rankings: RankingSummary[];
  history: MatchHistoryItem[];
}

export type RankingPage = PagedResponse<RankingEntry>;
