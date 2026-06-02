#!/usr/bin/env python3
"""
Generates app/src/main/assets/precon_decks.json and downloads all commander
art images to app/src/main/assets/precon_art/ for full APK-bundled offline use.

Output JSON artUrl format: "file:///android_asset/precon_art/{fileName}.jpg"
Run before each APK build (see .github/workflows/build-apk.yml).
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
# Direct CDN URL — bypasses Scryfall API rate-limits on CI runners
# Format: https://cards.scryfall.io/art_crop/front/{c1}/{c2}/{uuid}.jpg
SCRYFALL_CDN     = "https://cards.scryfall.io/art_crop/front/{}/{}/{}.jpg"
HEADERS = {
    "User-Agent": "MTGCommander/1.0 (build-script; contact nicolas.neubauer97@gmail.com)",
    "Accept": "application/json"
}

JSON_OUT  = "app/src/main/assets/precon_decks.json"
ART_DIR   = "app/src/main/assets/precon_art"
ASSET_URI = "file:///android_asset/precon_art/{}.jpg"


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
    faces = card.get("card_faces") or []
    if faces:
        return (faces[0].get("image_uris") or {}).get("art_crop", "")
    return ""


def download_image(url, dest_path, retries=2, headers=None):
    """Downloads image from url to dest_path. Returns True on success."""
    hdrs = headers or HEADERS
    for attempt in range(retries + 1):
        try:
            req = urllib.request.Request(url, headers=hdrs)
            with urllib.request.urlopen(req, timeout=20) as r:
                data = r.read()
            if len(data) < 1000:
                raise ValueError(f"Image too small ({len(data)} bytes)")
            with open(dest_path, "wb") as f:
                f.write(data)
            return True
        except Exception as e:
            if attempt < retries:
                time.sleep(1)
            else:
                print(f"  [IMG-FAIL] {url}: {e}", file=sys.stderr)
    return False


def main():
    os.makedirs(ART_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(JSON_OUT), exist_ok=True)

    print("=== Fetching Commander Deck list from MTGJSON ===")
    dl = get_json(MTGJSON_DECKLIST, timeout=30)
    if not dl:
        print("FATAL: Could not fetch MTGJSON DeckList", file=sys.stderr)
        sys.exit(1)

    commander_decks = [
        d for d in dl.get("data", [])
        if d.get("type") == "Commander Deck"
        and "Collector" not in d.get("fileName", "")
    ]
    print(f"Found {len(commander_decks)} Commander Decks\n")

    result = []
    skipped = 0
    no_art  = 0

    for i, deck in enumerate(commander_decks):
        file_name = deck.get("fileName", "")
        name      = deck.get("name", file_name)
        set_code  = deck.get("code", "")
        pct       = int((i + 1) / len(commander_decks) * 100)
        print(f"[{pct:3d}%] [{i+1:3d}/{len(commander_decks)}] {file_name}", end=" ... ", flush=True)

        # ── Deck details from MTGJSON ──────────────────────────────────────
        detail = get_json(MTGJSON_DECK.format(file_name))
        time.sleep(0.05)
        if not detail:
            print("SKIP (no detail)")
            skipped += 1
            continue

        commanders = (detail.get("data") or {}).get("commander") or []
        if not commanders:
            print("SKIP (no commander)")
            skipped += 1
            continue

        cmd             = commanders[0]
        commander_name  = cmd.get("name", "")
        scryfall_id     = cmd.get("scryfallId", "")
        colors          = "".join(cmd.get("colorIdentity") or [])

        # Second commander (partner / background)
        cmd2             = commanders[1] if len(commanders) > 1 else {}
        commander_name2  = cmd2.get("name", "")
        scryfall_id2     = cmd2.get("scryfallId", "")

        # ── Art crop URL: try direct CDN first (no API quota), then API ──────
        cdn_url = ""
        if scryfall_id:
            # CDN URL is deterministic from the UUID — no API call needed
            cdn_url = SCRYFALL_CDN.format(scryfall_id[0], scryfall_id[1], scryfall_id)

        if not cdn_url and commander_name:
            # Fallback: API fuzzy search (slower, may be rate-limited)
            enc  = urllib.parse.quote(commander_name)
            card = get_json(SCRYFALL_FUZZY.format(enc))
            time.sleep(0.15)
            cdn_url = art_crop_from_card(card)

        # ── German names from Scryfall API ─────────────────────────────────
        commander_name_de  = ""
        commander_name_de2 = ""
        if scryfall_id:
            de = get_json(f"{SCRYFALL_BY_ID.format(scryfall_id)}?lang=de")
            time.sleep(0.12)
            if de and de.get("lang") == "de":
                commander_name_de = de.get("printed_name", "")
        if scryfall_id2:
            de2 = get_json(f"{SCRYFALL_BY_ID.format(scryfall_id2)}?lang=de")
            time.sleep(0.12)
            if de2 and de2.get("lang") == "de":
                commander_name_de2 = de2.get("printed_name", "")

        # ── Download image into assets ─────────────────────────────────────
        art_url = ""
        if cdn_url:
            img_path = os.path.join(ART_DIR, f"{file_name}.jpg")
            already  = os.path.exists(img_path) and os.path.getsize(img_path) > 1000

            if already:
                art_url = ASSET_URI.format(file_name)
                print(f"{commander_name} [cached]")
            else:
                # Use IMAGE_HEADERS (no Accept: application/json — it's a binary download)
                img_headers = {"User-Agent": HEADERS["User-Agent"]}
                ok = download_image(cdn_url, img_path, headers=img_headers)
                if ok:
                    art_url = ASSET_URI.format(file_name)
                    print(f"{commander_name} [dl ok]")
                else:
                    art_url = cdn_url   # fallback: keep CDN URL
                    no_art += 1
                    print(f"{commander_name} [dl failed → CDN fallback]")
            time.sleep(0.05)
        else:
            no_art += 1
            print(f"{commander_name} [no art URL]")

        result.append({
            "fileName":         file_name,
            "name":             name,
            "setCode":          set_code,
            "commanderName":    commander_name,
            "commanderNameDe":  commander_name_de,
            "colors":           colors,
            "scryfallId":       scryfall_id,
            "artUrl":           art_url,
            "commanderName2":   commander_name2,
            "commanderNameDe2": commander_name_de2
        })

    # ── Write JSON ─────────────────────────────────────────────────────────
    with open(JSON_OUT, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    bundled  = sum(1 for d in result if d["artUrl"].startswith("file://"))
    cdn_fb   = sum(1 for d in result if d["artUrl"].startswith("http"))
    img_size = sum(
        os.path.getsize(os.path.join(ART_DIR, f))
        for f in os.listdir(ART_DIR) if f.endswith(".jpg")
    ) // 1024 // 1024

    print(f"\n=== Done ===")
    print(f"  Decks written  : {len(result)}")
    print(f"  Images bundled : {bundled}  ({img_size} MB in assets)")
    print(f"  CDN fallback   : {cdn_fb}")
    print(f"  No art         : {no_art}")
    print(f"  Skipped        : {skipped}")
    print(f"  JSON           : {JSON_OUT}")

    if len(result) < 50:
        print("WARNING: very few decks — MTGJSON may have been unreachable", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
