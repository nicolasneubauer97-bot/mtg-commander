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

    private val prefs = context.getSharedPreferences("precon_cache_v3", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val cacheMaxAgeMs = 7 * 24 * 60 * 60 * 1000L  // 7 Tage

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Returns the full deck list. Refreshes from MTGJSON if cache is older than 7 days. */
    suspend fun getDeckList(forceRefresh: Boolean = false): List<PreconDeck> = withContext(Dispatchers.IO) {
        val cacheAge = prefs.getLong("cache_timestamp", 0L)
        val isStale = System.currentTimeMillis() - cacheAge > cacheMaxAgeMs

        if (!forceRefresh && !isStale) {
            val cached = loadListFromCache()
            if (cached.isNotEmpty()) return@withContext cached
        }

        val fetched = fetchMtgJsonDeckList()
        val combined = (ALL_STATIC_DECKS + fetched)
            .distinctBy { it.fileName }
            .sortedBy { it.name }
        saveListToCache(combined)
        prefs.edit().putLong("cache_timestamp", System.currentTimeMillis()).apply()
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
                val fileName = item.optString("fileName", "")
                if (fileName.isBlank()) continue
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

    // ─── Statische Deck-Daten (komplett, mit Commander-Namen für Bilder) ──────

    companion object {
        private fun d(fn: String, n: String, sc: String, cmd: String, col: String) =
            PreconDeck(fn, n, sc, cmd, "", col)

        val ALL_STATIC_DECKS = listOf(
            // ─── Commander 2011 (CMD) ─────────────────────────────────────────
            d("MirrorMastery_CMD",      "Mirror Mastery",      "CMD", "Riku of Two Reflections",         "URG"),
            d("PlantedFear_CMD",        "Planted Fear",        "CMD", "The Mimeoplasm",                  "UBG"),
            d("EternalVigilance_CMD",   "Eternal Vigilance",   "CMD", "Ghave, Guru of Spores",           "WBG"),
            d("HeavenlyInferno_CMD",    "Heavenly Inferno",    "CMD", "Kaalia of the Vast",              "WBR"),
            d("CraftyConquerer_CMD",    "Crafty Conqueror",    "CMD", "Zedruu the Greathearted",         "WUR"),
            // ─── Commander 2013 (C13) ─────────────────────────────────────────
            d("EternalBargain_C13",     "Eternal Bargain",     "C13", "Oloro, Ageless Ascetic",          "WUB"),
            d("EvasiveManeuvers_C13",   "Evasive Maneuvers",   "C13", "Derevi, Empyrial Tactician",      "WUG"),
            d("MindSeize_C13",          "Mind Seize",          "C13", "Nekusar, the Mindrazer",          "UBR"),
            d("NatureOfTheBeast_C13",   "Nature of the Beast", "C13", "Marath, Will of the Wild",        "WRG"),
            d("PowerHungry_C13",        "Power Hungry",        "C13", "Prossh, Skyraider of Kher",       "BRG"),
            // ─── Commander 2014 (C14) ─────────────────────────────────────────
            d("BuiltFromScratch_C14",   "Built From Scratch",  "C14", "Daretti, Scrap Savant",           "R"),
            d("ForgedInStone_C14",      "Forged In Stone",     "C14", "Nahiri, the Lithomancer",         "W"),
            d("GuidedByNature_C14",     "Guided By Nature",    "C14", "Freyalise, Llanowar's Fury",      "G"),
            d("PeerThroughTime_C14",    "Peer Through Time",   "C14", "Teferi, Temporal Archmage",       "U"),
            d("SwornToDarkness_C14",    "Sworn To Darkness",   "C14", "Ob Nixilis of the Black Oath",    "B"),
            // ─── Commander 2015 (C15) ─────────────────────────────────────────
            d("CallTheSpirits_C15",     "Call the Spirits",    "C15", "Daxos the Returned",              "WB"),
            d("PlunderTheGraves_C15",   "Plunder the Graves",  "C15", "Meren of Clan Nel Toth",          "BG"),
            d("SeizeControl_C15",       "Seize Control",       "C15", "Mizzix of the Izmagnus",          "UR"),
            d("SwellTheHost_C15",       "Swell the Host",      "C15", "Ezuri, Claw of Progress",         "UG"),
            d("WadeIntoBattle_C15",     "Wade into Battle",    "C15", "Kalemne, Disciple of Iroas",      "WR"),
            // ─── Commander 2016 (C16) ─────────────────────────────────────────
            d("BreedLethality_C16",     "Breed Lethality",     "C16", "Atraxa, Praetors' Voice",         "WUBG"),
            d("EntropicUprising_C16",   "Entropic Uprising",   "C16", "Yidris, Maelstrom Wielder",       "UBRG"),
            d("InventSuperiority_C16",  "Invent Superiority",  "C16", "Breya, Etherium Shaper",          "WUBR"),
            d("OpenHostility_C16",      "Open Hostility",      "C16", "Saskia the Unyielding",           "WBRG"),
            d("StalwartUnity_C16",      "Stalwart Unity",      "C16", "Kynaios and Tiro of Meletis",     "WURG"),
            // ─── Commander 2017 (C17) ─────────────────────────────────────────
            d("ArcaneWizardry_C17",     "Arcane Wizardry",     "C17", "Inalla, Archmage Ritualist",      "UBR"),
            d("DraconicDomination_C17", "Draconic Domination", "C17", "The Ur-Dragon",                   "WUBRG"),
            d("FelineFerocity_C17",     "Feline Ferocity",     "C17", "Arahbo, Roar of the World",       "WG"),
            d("VampiricBloodlust_C17",  "Vampiric Bloodlust",  "C17", "Edgar Markov",                    "WBR"),
            // ─── Commander 2018 (C18) ─────────────────────────────────────────
            d("AdaptiveEnchantment_C18","Adaptive Enchantment","C18", "Estrid, the Masked",              "WUG"),
            d("ExquisiteInvention_C18", "Exquisite Invention", "C18", "Saheeli, the Gifted",             "UR"),
            d("NaturesVengeance_C18",   "Nature's Vengeance",  "C18", "Lord Windgrace",                  "BRG"),
            d("SubjectiveReality_C18",  "Subjective Reality",  "C18", "Aminatou, the Fateshifter",       "WUB"),
            // ─── Commander 2019 (C19) ─────────────────────────────────────────
            d("FacelessMenace_C19",     "Faceless Menace",     "C19", "Kadena, Slinking Sorcerer",       "UBG"),
            d("MercilessRage_C19",      "Merciless Rage",      "C19", "Anje Falkenrath",                 "BR"),
            d("MysticIntellect_C19",    "Mystic Intellect",    "C19", "Sevinne, the Chronoclasm",        "WUR"),
            d("PrimalGenesis_C19",      "Primal Genesis",      "C19", "Ghired, Conclave Exile",          "WRG"),
            // ─── Commander 2020 / Ikoria (C20) ───────────────────────────────
            d("ArcaneMaelstrom_C20",    "Arcane Maelstrom",    "C20", "Kalamax, the Stormsire",          "URG"),
            d("EnhancedEvolution_C20",  "Enhanced Evolution",  "C20", "Otrimi, the Ever-Playful",        "UBG"),
            d("RuthlessRegiment_C20",   "Ruthless Regiment",   "C20", "Jirina Kudro",                    "WBR"),
            d("SymbioticSwarm_C20",     "Symbiotic Swarm",     "C20", "Kathril, Aspect Warper",          "WBG"),
            d("TimelessWisdom_C20",     "Timeless Wisdom",     "C20", "Gavi, Nest Warden",               "WUR"),
            // ─── Commander 2021 / Strixhaven (C21) ───────────────────────────
            d("LoreholdLegacies_C21",   "Lorehold Legacies",   "C21", "Osgir, the Reconstructor",       "WR"),
            d("PrismariPerformance_C21","Prismari Performance", "C21", "Zaffai, Thunder Conductor",      "UR"),
            d("QuantumQuandrix_C21",    "Quantum Quandrix",    "C21", "Adrix and Nev, Twincasters",      "UG"),
            d("SilverquillStatement_C21","Silverquill Statement","C21","Breena, the Demagogue",          "WB"),
            d("WitherbloomWitchcraft_C21","Witherbloom Witchcraft","C21","Willowdusk, Essence Seer",     "BG"),
            // ─── Forgotten Realms Commander (AFC) ────────────────────────────
            d("AuraOfCourage_AFC",      "Aura of Courage",     "AFC", "Galea, Kindler of Hope",         "WUG"),
            d("DraconicRage_AFC",       "Draconic Rage",       "AFC", "Vrondiss, Rage of Ancients",     "RG"),
            d("DungeonsOfDeath_AFC",    "Dungeons of Death",   "AFC", "Sefris of the Hidden Ways",      "WUB"),
            d("PlanarPortal_AFC",       "Planar Portal",       "AFC", "Prosper, Tome-Bound",            "BR"),
            // ─── Midnight Hunt Commander (MIC) ───────────────────────────────
            d("UndeadUnleashed_MIC",    "Undead Unleashed",    "MIC", "Wilhelt, the Rotcleaver",        "UB"),
            d("CovenCounters_MIC",      "Coven Counters",      "MIC", "Leinore, Autumn Sovereign",      "WRG"),
            // ─── Crimson Vow Commander (VOC) ─────────────────────────────────
            d("VampiricBloodline_VOC",  "Vampiric Bloodline",  "VOC", "Edgar, Charmed Groom",           "WBR"),
            d("SpiritSquadron_VOC",     "Spirit Squadron",     "VOC", "Millicent, Restless Revenant",   "WU"),
            // ─── Neon Dynasty Commander (NEC) ────────────────────────────────
            d("BuckleUp_NEC",           "Buckle Up",           "NEC", "Kotori, Pilot Prodigy",          "WU"),
            d("UpgradesUnleashed_NEC",  "Upgrades Unleashed",  "NEC", "Hinata, Dawn-Crowned",           "WUR"),
            // ─── New Capenna Commander (NCC) ─────────────────────────────────
            d("BedeckedBrokers_NCC",    "Bedecked Brokers",    "NCC", "Perrie, the Pulverizer",         "WUG"),
            d("CabarettiCacophony_NCC", "Cabaretti Cacophony", "NCC", "Kitt Kanto, Mayhem Diva",        "WRG"),
            d("MaestrosMassacre_NCC",   "Maestros Massacre",   "NCC", "Anhelo, the Painter",            "UBR"),
            d("ObscuraOperation_NCC",   "Obscura Operation",   "NCC", "Kamiz, Obscura Oculus",          "WUB"),
            d("RiveteersRampage_NCC",   "Riveteers Rampage",   "NCC", "Ognis, the Dragon's Lash",       "BRG"),
            // ─── Baldur's Gate Commander (CLB) ───────────────────────────────
            d("DraconicDissent_CLB",    "Draconic Dissent",    "CLB", "Firkraag, Cunning Instigator",   "UR"),
            d("ExitFromExile_CLB",      "Exit from Exile",     "CLB", "Faldorn, Dread Wolf Herald",     "RG"),
            d("MindFlayarrrs_CLB",      "Mind Flayarrrs",      "CLB", "Bhaal, Lord of Murder",          "BRG"),
            d("PartyTime_CLB",          "Party Time",          "CLB", "Nalia de'Arnise",                "WBG"),
            // ─── Dominaria United Commander (DMC) ────────────────────────────
            d("LegendsLegacy_DMC",      "Legends' Legacy",     "DMC", "Dihada, Binder of Wills",        "WBR"),
            d("Painbow_DMC",            "Painbow",             "DMC", "Jared Carthalion",               "WUBRG"),
            // ─── Warhammer 40K (40K) ─────────────────────────────────────────
            d("ForcesOfTheImperium_40K","Forces of the Imperium","40K","Commander Shadowsun",           "WU"),
            d("NecronDynasties_40K",    "Necron Dynasties",    "40K", "Szarekh, the Silent King",       "B"),
            d("TheRuinousPowers_40K",   "The Ruinous Powers",  "40K", "Be'lakor, the Dark Master",      "UBR"),
            d("TyranidSwarm_40K",       "Tyranid Swarm",       "40K", "The Swarmlord",                  "URG"),
            // ─── Brothers' War Commander (BRC) ───────────────────────────────
            d("MishraSBurnishedBanner_BRC","Mishra's Burnished Banner","BRC","Mishra, Claimed by Gix",  "UBR"),
            d("UrzaSIronAlliance_BRC",  "Urza's Iron Alliance","BRC", "Urza, Chief Artificer",          "WUB"),
            // ─── All Will Be One Commander (ONC) ─────────────────────────────
            d("CorruptingInfluence_ONC","Corrupting Influence", "ONC", "Ixhel, Scion of Atraxa",        "WBG"),
            d("RebellionRising_ONC",    "Rebellion Rising",    "ONC", "Neyali, Suns' Vanguard",         "WBR"),
            // ─── March of the Machine Commander (MOC) ────────────────────────
            d("CavalryCharge_MOC",      "Cavalry Charge",      "MOC", "Bright-Palm, Soul Awakener",     "WR"),
            d("GrowingThreat_MOC",      "Growing Threat",      "MOC", "Plargg and Nassari",             "URG"),
            d("CallForBackup_MOC",      "Call for Backup",     "MOC", "Kasla, the Broken Halo",         "WUG"),
            d("TinkerTime_MOC",         "Tinker Time",         "MOC", "Gimbal, Gremlin Prodigy",        "URG"),
            // ─── Commander Masters (CMM) ──────────────────────────────────────
            d("SliverSwarm_CMM",        "Sliver Swarm",        "CMM", "Sliver Gravemother",             "WUBRG"),
            d("EnduringEnchantments_CMM","Enduring Enchantments","CMM","Anikthea, Hand of Erebos",      "WBG"),
            d("EldraziUnbound_CMM",     "Eldrazi Unbound",     "CMM", "Zhulodok, Void Gorger",          "C"),
            d("PlaneswalkerParty_CMM",  "Planeswalker Party",  "CMM", "Commodore Guff",                 "WUBR"),
            // ─── Wilds of Eldraine Commander (WOC) ───────────────────────────
            d("FaeDominion_WOC",        "Fae Dominion",        "WOC", "Tegwyll, Duke of Spite",         "UB"),
            d("VirtueAndValor_WOC",     "Virtue and Valor",    "WOC", "Ellivere of the Wild Court",     "WG"),
            d("RestlessInPeace_WOC",    "Restless in Peace",   "WOC", "Gylwain, Casting Director",      "WBR"),
            // ─── Doctor Who Commander (WHO) ───────────────────────────────────
            d("BlastFromThePast_WHO",   "Blast from the Past", "WHO", "The Fourth Doctor",              "WUR"),
            d("ParadoxPower_WHO",       "Paradox Power",       "WHO", "The Tenth Doctor",               "WUR"),
            d("MastersOfEvil_WHO",      "Masters of Evil",     "WHO", "Davros, Dalek Creator",          "UBR"),
            d("TimeyWimey_WHO",         "Timey-Wimey",         "WHO", "The Thirteenth Doctor",          "WUG"),
            // ─── Lost Caverns of Ixalan Commander (LCC) ──────────────────────
            d("BloodRites_LCC",         "Blood Rites",         "LCC", "Clavileño, First of the Blessed","WBR"),
            d("ExplorersMaps_LCC",      "Explorer's Maps",     "LCC", "Hakbal of the Surging Soul",     "UG"),
            d("VelociRampTor_LCC",      "Veloci-Ramp-Tor",     "LCC", "Pantlaza, Sun-Favored",          "WUG"),
            // ─── Karlov Manor Commander (MKC) ────────────────────────────────
            d("BlameGame_MKC",          "Blame Game",          "MKC", "Mirko, Obsessive Theorist",      "WU"),
            d("DeepClueSea_MKC",        "Deep Clue Sea",       "MKC", "Morska, Undersea Sleuth",        "UG"),
            d("DeadlyDisguise_MKC",     "Deadly Disguise",     "MKC", "Kaust, Eyes of the Glade",       "UBG"),
            // ─── Thunder Junction Commander (OTC) ────────────────────────────
            d("DesertBloom_OTC",        "Desert Bloom",        "OTC", "Yuma, Proud Protector",          "WBG"),
            d("GrandLarceny_OTC",       "Grand Larceny",       "OTC", "Gonti, Canny Acquisitor",        "UBG"),
            d("MostWanted_OTC",         "Most Wanted",         "OTC", "Olivia, Opulent Outlaw",         "WBR"),
            d("QuickDraw_OTC",          "Quick Draw",          "OTC", "Stella Lee, Wild Card",          "UR"),
            // ─── Bloomburrow Commander (BLC) ─────────────────────────────────
            d("AnimatedArmy_BLC",       "Animated Army",       "BLC", "Warren Soultrader",              "WB"),
            d("FamilyMatters_BLC",      "Family Matters",      "BLC", "Zinnia, Valley's Voice",         "WG"),
            d("PeaceOffering_BLC",      "Peace Offering",      "BLC", "Bello, Bard of the Brambles",    "WR"),
            d("SquirreledAway_BLC",     "Squirreled Away",     "BLC", "Hazel of the Rootbloom",         "UG"),
            // ─── Duskmourn Commander (DSC) ────────────────────────────────────
            d("DeathToll_DSC",          "Death Toll",          "DSC", "Mirko, Obsessive Theorist",      "UB"),
            d("EndlessPunishment_DSC",  "Endless Punishment",  "DSC", "Zimone, All-Questioning",        "BG"),
            d("JumpScare_DSC",          "Jump Scare!",         "DSC", "The Haunting of Heretat",        "RG"),
            d("MiracleWorker_DSC",      "Miracle Worker",      "DSC", "Heliod's Champion",             "WU"),
            // ─── Aetherdrift Commander (ARC / DRC) ───────────────────────────
            d("EternalMight_DRC",       "Eternal Might",       "DRC", "Aethon, the Cylix Seeker",      "WR"),
            d("LivingEnergy_DRC",       "Living Energy",       "DRC", "Vashu, First of the Electrids",  "URG"),
            // ─── ECC (Explorer Commander / unbekannt) ────────────────────────
            d("BlightCurse_ECC",        "Blight Curse",        "ECC", "Toxrill, the Corrosive",         "UB"),
            d("DanceOfTheElements_ECC", "Dance of the Elements","ECC","Omnath, Locus of Creation",      "WURG"),
        )
    }
}
