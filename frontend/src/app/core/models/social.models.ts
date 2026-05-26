import { GameMode } from './match.models';

export type SocialStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED';
export type SocialRelation = 'SELF' | 'NONE' | 'FRIEND' | 'REQUEST_SENT' | 'REQUEST_RECEIVED';
export type SocialEventType = 'FRIEND_REQUEST' | 'MATCH_INVITE';

export interface SocialUser {
  userId: string;
  username: string;
  avatarUrl: string | null;
  relation: SocialRelation;
}

export interface Friend {
  userId: string;
  username: string;
  avatarUrl: string | null;
  friendsSince: string;
}

export interface FriendRequest {
  id: string;
  requester: SocialUser;
  addressee: SocialUser;
  status: SocialStatus;
  createdAt: string;
  respondedAt: string | null;
}

export interface MatchInvite {
  id: string;
  matchId: string;
  mode: GameMode;
  from: SocialUser;
  to: SocialUser;
  status: SocialStatus;
  createdAt: string;
  respondedAt: string | null;
}

export interface FriendRequestEvent {
  requestId: string;
  from: SocialUser;
}

export interface MatchInviteEvent {
  inviteId: string;
  matchId: string;
  mode: GameMode;
  from: SocialUser;
}

export interface SocialEvent<T = unknown> {
  type: SocialEventType;
  payload: T;
}
