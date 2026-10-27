package com.mtg.commander.ui.utils

import com.mtg.commander.domain.model.Deck

/** "Englisch (Deutsch)" wenn ein abweichender deutscher Commander-Name vorhanden ist, sonst nur Englisch. */
fun commanderDisplayName(deck: Deck): String =
    if (deck.commanderNameDe.isNotBlank() &&
        !deck.commanderNameDe.equals(deck.commanderName, ignoreCase = true)
    ) "${deck.commanderName} (${deck.commanderNameDe})" else deck.commanderName
