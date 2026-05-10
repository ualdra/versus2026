export interface Achievement {
  id: string;
  key: string;
  name: string;
  description: string;
  iconKey: string;
  category: string;
  unlocked: boolean;
  unlockedAt: string | null;
}

export interface AchievementUnlockedEvent {
  type: 'ACHIEVEMENT_UNLOCKED';
  achievement: Achievement;
}
