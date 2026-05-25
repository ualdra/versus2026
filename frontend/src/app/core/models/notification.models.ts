export type NotificationTone = 'info' | 'success' | 'warning' | 'danger';

export type NotificationType =
  | 'ACHIEVEMENT_UNLOCKED'
  | 'MATCH_FOUND'
  | 'SYSTEM';

export interface NotificationItem {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  createdAt: string;
  read: boolean;
  tone: NotificationTone;
  route?: string;
  sourceId?: string;
}

export interface NotificationPrefs {
  friendRequests: boolean;
  matchInvites: boolean;
  achievements: boolean;
}

export const NOTIFICATION_PREFS_KEY = 'vs.notificationPrefs';

export const DEFAULT_NOTIFICATION_PREFS: NotificationPrefs = {
  friendRequests: true,
  matchInvites: true,
  achievements: true,
};
