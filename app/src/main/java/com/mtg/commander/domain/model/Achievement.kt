package com.mtg.commander.domain.model

enum class AchievementTier(val order: Int, val germanLabel: String) {
    NONE(0, "Gesperrt"),
    BRONZE(1, "Bronze"),
    SILVER(2, "Silber"),
    GOLD(3, "Gold"),
    PLATINUM(4, "Platin")
}

enum class AchievementId(val germanName: String, val description: String) {
    SERIENSIEGER("Seriensieger", "Gesamtzahl gewonnener Partien."),
    SCHARFRICHTER("Scharfrichter", "Spieler eliminiert, über alle Partien."),
    KOMMANDANT_DES_SCHADENS("Kommandant des Schadens", "Insgesamt ausgeteilter Commander-Schaden."),
    VETERAN("Veteran", "Anzahl gespielter Partien."),
    LEBENSRETTER("Lebensretter", "Insgesamt geheiltes Leben."),
    NEMESIS("Nemesis", "Höchste Anzahl Kills gegen ein und denselben Gegner."),
    DAUERGAST("Dauergast", "Wie oft zufällig als Startspieler gelost."),
    UNSTERBLICH("Unsterblich", "Eine Partie gewonnen, ohne beim Sieg unter 40 Leben zu stehen."),
    VERWUESTER("Verwüster", "Insgesamt ausgeteilter Schaden (alle Quellen)."),
    ZIELSCHEIBE("Zielscheibe", "Wie oft vom Zufallsgegner-Zufallsgenerator als Ziel gewählt."),
    RANG("Rang", "Die niedrigste Stufe über alle anderen Achievements.")
}

data class AchievementProgress(
    val id: AchievementId,
    val tier: AchievementTier,
    val value: Int,
    val nextThreshold: Int?
)

data class AchievementPopupEvent(
    val playerName: String,
    val achievementId: AchievementId,
    val newTier: AchievementTier
)
