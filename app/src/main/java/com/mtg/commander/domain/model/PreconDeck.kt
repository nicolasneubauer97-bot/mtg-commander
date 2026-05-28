package com.mtg.commander.domain.model

data class PreconDeck(
    val fileName: String,
    val name: String,
    val setCode: String,
    val commanderName: String = "",
    val commanderNameDe: String = "",
    val colors: String = "",
    val scryfallId: String = "",
    val artUrl: String = ""
) {
    val displayArtUrl: String get() = when {
        artUrl.isNotBlank() -> artUrl
        scryfallId.isNotBlank() ->
            "https://cards.scryfall.io/art_crop/front/${scryfallId[0]}/${scryfallId[1]}/$scryfallId.jpg"
        commanderName.isNotBlank() -> {
            val encoded = commanderName.replace(" ", "+").replace(",", "%2C")
            "https://api.scryfall.com/cards/named?exact=$encoded&format=image&version=art_crop"
        }
        else -> ""
    }
}
