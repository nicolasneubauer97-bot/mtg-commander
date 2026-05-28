package com.mtg.commander.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mtg.commander.domain.model.PreconDeck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PreconRepository(context: Context) {

    private val prefs = context.getSharedPreferences("precon_cache_v2", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ─── Public API ──────────────────────────────────────────────────────────

    suspend fun getDeckList(): List<PreconDeck> = withContext(Dispatchers.IO) {
        val cached = loadListFromCache()
        if (cached.isNotEmpty()) return@withContext cached

        val fetched = fetchMtgJsonDeckList()
        val combined = (STATIC_RECENT_DECKS + fetched)
            .distinctBy { it.fileName }
            .sortedBy { it.name }
        saveListToCache(combined)
        combined
    }

    suspend fun loadDeckDetails(deck: PreconDeck): PreconDeck = withContext(Dispatchers.IO) {
        if (deck.commanderName.isNotBlank()) return@withContext deck

        val cacheKey = "detail_${deck.fileName}"
        val cachedJson = prefs.getString(cacheKey, null)
        if (cachedJson != null) {
            return@withContext try { gson.fromJson(cachedJson, PreconDeck::class.java) }
            catch (_: Exception) { deck }
        }

        val detailed = fetchMtgJsonDeckDetails(deck)
        prefs.edit().putString(cacheKey, gson.toJson(detailed)).apply()
        detailed
    }

    suspend fun fetchGermanName(scryfallId: String): String = withContext(Dispatchers.IO) {
        if (scryfallId.isBlank()) return@withContext ""
        val cacheKey = "de_$scryfallId"
        val cached = prefs.getString(cacheKey, null)
        if (cached != null) return@withContext cached

        val name = try {
            val url = URL("https://api.scryfall.com/cards/$scryfallId?lang=de")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "MTGCommander/1.0")
            conn.connectTimeout = 5000; conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                json.optString("printed_name", "")
            } else ""
        } catch (_: Exception) { "" }

        if (name.isNotBlank()) prefs.edit().putString(cacheKey, name).apply()
        name
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    // ─── MTGJSON Fetching ────────────────────────────────────────────────────

    private fun fetchMtgJsonDeckList(): List<PreconDeck> {
        return try {
            val url = URL("https://mtgjson.com/api/v5/DeckList.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "MTGCommander/1.0")
            conn.connectTimeout = 10000; conn.readTimeout = 10000
            if (conn.responseCode != 200) return emptyList()

            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            val data = root.getJSONArray("data")
            val result = mutableListOf<PreconDeck>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                if (item.optString("type") != "Commander Deck") continue
                val fileName = item.optString("fileName", "").ifBlank { continue }
                if (fileName.contains("Collector", ignoreCase = true)) continue
                result.add(PreconDeck(
                    fileName = fileName,
                    name = item.optString("name", fileName),
                    setCode = item.optString("code", "")
                ))
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    private fun fetchMtgJsonDeckDetails(deck: PreconDeck): PreconDeck {
        return try {
            val url = URL("https://mtgjson.com/api/v5/decks/${deck.fileName}.json")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "MTGCommander/1.0")
            conn.connectTimeout = 10000; conn.readTimeout = 15000
            if (conn.responseCode != 200) return deck

            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            val data = root.getJSONObject("data")
            val commanders = data.optJSONArray("commander")
            val cmd = commanders?.optJSONObject(0) ?: return deck

            val name = cmd.optString("name", "")
            val scryfallId = cmd.optString("scryfallId", "")
            val ciArray = cmd.optJSONArray("colorIdentity")
            val colors = buildString {
                if (ciArray != null) for (j in 0 until ciArray.length()) append(ciArray.getString(j))
            }

            deck.copy(commanderName = name, scryfallId = scryfallId, colors = colors)
        } catch (_: Exception) { deck }
    }

    // ─── Cache ───────────────────────────────────────────────────────────────

    private fun loadListFromCache(): List<PreconDeck> {
        val json = prefs.getString("deck_list", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PreconDeck>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun saveListToCache(decks: List<PreconDeck>) {
        prefs.edit().putString("deck_list", gson.toJson(decks)).apply()
    }

    // ─── Static supplement for newer/popular precon sets ─────────────────────

    companion object {
        val STATIC_RECENT_DECKS = listOf(
            // Duskmourn Commander (DSC) 2024
            PreconDeck("DeathToll_DSC", "Death Toll", "DSC", "Mirko, Obsessive Theorist", "", "UB"),
            PreconDeck("EndlessPunishment_DSC", "Endless Punishment", "DSC", "Zimone, All-Questioning", "", "BG"),
            PreconDeck("JumpScare_DSC", "Jump Scare!", "DSC", "Hasty Besecher", "", "RG"),
            PreconDeck("MiracleWorker_DSC", "Miracle Worker", "DSC", "Heliod's Champion", "", "WU"),
            // Bloomburrow Commander (BLC) 2024
            PreconDeck("AnimatedArmy_BLC", "Animated Army", "BLC", "Warren Soultrader", "", "WB"),
            PreconDeck("FamilyMatters_BLC", "Family Matters", "BLC", "Zinnia, Valley's Voice", "", "WG"),
            PreconDeck("PeaceOffering_BLC", "Peace Offering", "BLC", "Bello, Bard of the Brambles", "", "WR"),
            PreconDeck("SquirreledAway_BLC", "Squirreled Away", "BLC", "Hazel of the Rootbloom", "", "UG"),
            // Thunder Junction Commander (OTC) 2024
            PreconDeck("DesertBloom_OTC", "Desert Bloom", "OTC", "Yuma, Proud Protector", "", "WBG"),
            PreconDeck("GrandLarceny_OTC", "Grand Larceny", "OTC", "Gonti, Canny Acquisitor", "", "UBG"),
            PreconDeck("MostWanted_OTC", "Most Wanted", "OTC", "Olivia, Opulent Outlaw", "", "WBR"),
            PreconDeck("QuickDraw_OTC", "Quick Draw", "OTC", "Stella Lee, Wild Card", "", "UR"),
            // Karlov Manor Commander (MKC) 2024
            PreconDeck("BlameGame_MKC", "Blame Game", "MKC", "Mirko, Obsessive Theorist", "", "WU"),
            PreconDeck("DeepClueSea_MKC", "Deep Clue Sea", "MKC", "Morska, Undersea Sleuth", "", "UG"),
            PreconDeck("DeadlyDisguise_MKC", "Deadly Disguise", "MKC", "Kaust, Eyes of the Glade", "", "UBG"),
            // Lost Caverns of Ixalan Commander (LCC) 2023
            PreconDeck("BloodRites_LCC", "Blood Rites", "LCC", "Clavileño, First of the Blessed", "", "WBR"),
            PreconDeck("ExplorersMaps_LCC", "Explorer's Maps", "LCC", "Hakbal of the Surging Soul", "", "UG"),
            PreconDeck("VelociRampTor_LCC", "Veloci-Ramp-Tor", "LCC", "Pantlaza, Sun-Favored", "", "WUG"),
            // Doctor Who Commander (WHO) 2023
            PreconDeck("BlastFromThePast_WHO", "Blast from the Past", "WHO", "The Fourth Doctor", "", "WUR"),
            PreconDeck("ParadoxPower_WHO", "Paradox Power", "WHO", "The Tenth Doctor", "", "WUR"),
            PreconDeck("MastersOfEvil_WHO", "Masters of Evil", "WHO", "Davros, Dalek Creator", "", "UBR"),
            PreconDeck("TimeyWimey_WHO", "Timey-Wimey", "WHO", "The Thirteenth Doctor", "", "WUG"),
            // Wilds of Eldraine Commander (WOC) 2023
            PreconDeck("FaeDominion_WOC", "Fae Dominion", "WOC", "Tegwyll, Duke of Spite", "", "UB"),
            PreconDeck("VirtueAndValor_WOC", "Virtue and Valor", "WOC", "Ellivere of the Wild Court", "", "WG"),
            PreconDeck("RestlessInPeace_WOC", "Restless in Peace", "WOC", "Gylwain, Casting Director", "", "WBR"),
            // Commander Masters (CMM) 2023
            PreconDeck("SliverSwarm_CMM", "Sliver Swarm", "CMM", "Sliver Gravemother", "", "WUBRG"),
            PreconDeck("EnduringEnchantments_CMM", "Enduring Enchantments", "CMM", "Anikthea, Hand of Erebos", "", "WBG"),
            PreconDeck("EldraziUnbound_CMM", "Eldrazi Unbound", "CMM", "Zhulodok, Void Gorger", "", "C"),
            PreconDeck("PlaneswalkerParty_CMM", "Planeswalker Party", "CMM", "Commodore Guff", "", "WUBR"),
            // March of the Machine Commander (MOC) 2023
            PreconDeck("CavalryCharge_MOC", "Cavalry Charge", "MOC", "Bright-Palm, Soul Awakener", "", "WR"),
            PreconDeck("GrowingThreat_MOC", "Growing Threat", "MOC", "Plargg and Nassari", "", "URG"),
            PreconDeck("CallForBackup_MOC", "Call for Backup", "MOC", "Kasla, the Broken Halo", "", "WUG"),
            PreconDeck("TinkerTime_MOC", "Tinker Time", "MOC", "Gimbal, Gremlin Prodigy", "", "URG"),
            // All Will Be One Commander (ONC) 2023
            PreconDeck("CorruptingInfluence_ONC", "Corrupting Influence", "ONC", "Ixhel, Scion of Atraxa", "", "WBG"),
            PreconDeck("RebellionRising_ONC", "Rebellion Rising", "ONC", "Neyali, Suns' Vanguard", "", "WBR"),
            // Brothers' War Commander (BRC) 2022
            PreconDeck("MishraSBurnishedBanner_BRC", "Mishra's Burnished Banner", "BRC", "Mishra, Claimed by Gix", "", "UBR"),
            PreconDeck("UrzaSIronAlliance_BRC", "Urza's Iron Alliance", "BRC", "Urza, Chief Artificer", "", "WUB"),
            // Dominaria United Commander (DMC) 2022
            PreconDeck("LegendsLegacy_DMC", "Legends' Legacy", "DMC", "Dihada, Binder of Wills", "", "WBR"),
            PreconDeck("Painbow_DMC", "Painbow", "DMC", "Jared Carthalion", "", "WUBRG"),
            // New Capenna Commander (NCC) 2022
            PreconDeck("BedeckedBrokers_NCC", "Bedecked Brokers", "NCC", "Perrie, the Pulverizer", "", "WUG"),
            PreconDeck("CabarettiCacophony_NCC", "Cabaretti Cacophony", "NCC", "Kitt Kanto, Mayhem Diva", "", "WRG"),
            PreconDeck("MaestrosMassacre_NCC", "Maestros Massacre", "NCC", "Anhelo, the Painter", "", "UBR"),
            PreconDeck("ObscuraOperation_NCC", "Obscura Operation", "NCC", "Kamiz, Obscura Oculus", "", "WUB"),
            PreconDeck("RiveteersRampage_NCC", "Riveteers Rampage", "NCC", "Ognis, the Dragon's Lash", "", "BRG"),
            // Neon Dynasty Commander (NEC) 2022
            PreconDeck("BuckleUp_NEC", "Buckle Up", "NEC", "Kotori, Pilot Prodigy", "", "WU"),
            PreconDeck("UpgradesUnleashed_NEC", "Upgrades Unleashed", "NEC", "Hinata, Dawn-Crowned", "", "WUR"),
            // Crimson Vow Commander (VOC) 2021
            PreconDeck("VampiricBloodline_VOC", "Vampiric Bloodline", "VOC", "Edgar, Charmed Groom", "", "WBR"),
            PreconDeck("SpiritSquadron_VOC", "Spirit Squadron", "VOC", "Millicent, Restless Revenant", "", "WU"),
            // Midnight Hunt Commander (MIC) 2021
            PreconDeck("UndeadUnleashed_MIC", "Undead Unleashed", "MIC", "Wilhelt, the Rotcleaver", "", "UB"),
            PreconDeck("CovenCounters_MIC", "Coven Counters", "MIC", "Leinore, Autumn Sovereign", "", "WRG"),
            // Commander 2011 (CMD)
            PreconDeck("MirrorMastery_CMD", "Mirror Mastery", "CMD", "Riku of Two Reflections", "", "URG"),
            PreconDeck("PlantedFear_CMD", "Planted Fear", "CMD", "The Mimeoplasm", "", "UBG"),
            PreconDeck("EternalVigilance_CMD", "Eternal Vigilance", "CMD", "Ghave, Guru of Spores", "", "WBG"),
            PreconDeck("HeavenlyInferno_CMD", "Heavenly Inferno", "CMD", "Kaalia of the Vast", "", "WBR"),
            PreconDeck("CraftyConquerer_CMD", "Crafty Conqueror", "CMD", "Zedruu the Greathearted", "", "WUR"),
        )
    }
}
