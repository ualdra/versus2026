export interface MatchEvent<T = unknown> {
  type: WsEventType;
  matchId: string;
  payload: T;
}

export type WsEventType =
  | 'PLAYER_JOINED'
  | 'PLAYER_LEFT'
  | 'PLAYER_READY'
  | 'MATCH_FOUND'
  | 'FRIEND_REQUEST'
  | 'MATCH_INVITE'
  | 'MATCH_STARTING'
  | 'MATCH_START'
  | 'QUESTION'
  | 'ANSWER_RESULT'
  | 'ROUND_RESULT'
  | 'MATCH_END'
  | 'SABOTAGE_ACTIVATED'
  | 'SABOTAGE_REJECTED'
  | 'EFFECT_APPLIED';
