package com.versus.api.achievements.dto;

public record AchievementUnlockedEvent(String type, AchievementResponse achievement) {
    public static AchievementUnlockedEvent of(AchievementResponse achievement) {
        return new AchievementUnlockedEvent("ACHIEVEMENT_UNLOCKED", achievement);
    }
}
