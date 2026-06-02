#!/usr/bin/env python3
"""
Generates app/src/main/assets/precon_decks.json from MTGJSON + Scryfall.
Run before each APK build so the app has a complete, offline-ready deck list.

Output JSON format per deck:
  fileName, name, setCode, commanderName, commanderNameDe,
  colors, scryfallId, artUrl
"""

import json
import os
import sys
import time
import urllib.request
import urllib.parse

MTGJSON_DECKLIST = "https://mtgjson.com/api/v5/DeckList.json"
MTGJSON_DECK     = "https://mtgjson.com/api/v5/decks/{}.json"
SCRYFALL_BY_ID   = "https://api.scryfall.com/cards/{}"
SCRYFALL_FUZZY   = "https://api.scryfall.com/cards/named?fuzzy={}"
HEADERS = {"User-Agent": "MTGCommander/1.0 (build-script)"}
OUT_PATH = "app/src/main/assets/precon_decks.json"


def get_json(url, timeout=20):
    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except Exception as e:
        print(f"  [WARN] {url}: {e}", file=sys.stderr)
        return None


def art_crop_from_card(card):
    if not card:
        return ""
    uris = card.get("image_uris")
    if uris:
        return uris.get("art_crop", "")
    # Double-faced cards (DFC): art lives on card_faces[0]
    faces = card.get("card_faces") or []
    if faces:
        return (faces[0].get("image_uris") or {}).get("art_crop", "")
    return ""


def main():
    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)

    print("=== Fetching Commander Deck list from MTGJSON ===")
    dl = get_json(MTGJSON_DECKLIST, timeout=30)
    if not dl:
        print("FATAL: Could not fetch MTGJSON DeckList", file=sys.stderr)
        sys.exit(1)

    all_decks = dl.get("data", [])
    commander_decks = [
        d for d in all_decks
        if d.get("type") == "Commander Deck"
        and "Collector" not in d.get("fileName", "")
    ]
    print(f"Found {len(commander_decks)} Commander Decks")

    result = []
    errors = 0

    for i, deck in enumerate(commander_decks):
        file_name = deck.get("fileName", "")
        name      = deck.get("name", file_name)
        set_code  = deck.get("code", "")
        pct       = int((i + 1) / len(commander_decks) * 100)
        print(f"[{pct:3d}%] [{i+1}/{len(commander_decks)}] {file_name} ...", end=" ", flush=True)

        # ── Deck details from MTGJSON ──────────────────────────────────────
        detail = get_json(MTGJSON_DECK.format(file_name))
        time.sleep(0.05)
        if not detail:
            print("SKIP (no detail)")
            errors += 1
            continue

        commanders = (detail.get("data") or {}).get("commander") or []
        if not commanders:
            print("SKIP (no commander array)")
            errors += 1
            continue

        cmd            = commanders[0]
        commander_name = cmd.get("name", "")
        scryfall_id    = cmd.get("scryfallId", "")
        ci             = cmd.get("colorIdentity") or []
        colors         = "".join(ci)

        # ── Art URL from Scryfall ──────────────────────────────────────────
        art_url = ""
        if scryfall_id:
            card = get_json(SCRYFALL_BY_ID.format(scryfall_id))
            time.sleep(0.1)
            art_url = art_crop_from_card(card)

        if not art_url and commander_name:
            enc  = urllib.parse.quote(commander_name)
            card = get_json(SCRYFALL_FUZZY.format(enc))
            time.sleep(0.1)
            art_url = art_crop_from_card(card)

        status = "ok" if art_url else "no-art"
        print(f"{commander_name} [{status}]")

        result.append({
            "fileName":        file_name,
            "name":            name,
            "setCode":         set_code,
            "commanderName":   commander_name,
            "commanderNameDe": "",
            "colors":          colors,
            "scryfallId":      scryfall_id,
            "artUrl":          art_url
        })

    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    with_art = sum(1 for d in result if d["artUrl"])
    print(f"\n=== Done: {len(result)} decks written to {OUT_PATH} ===")
    print(f"    With art URL : {with_art}/{len(result)}")
    print(f"    Skipped      : {errors}")

    if len(result) < 50:
        print("WARNING: very few decks — MTGJSON may have been unreachable", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
