#!/usr/bin/env python3
"""Builds a tutorial video from a storyboard (videos/<id>.json): speech, screen recording and cut,
all generated - nobody records, speaks or edits.

    python3 scripts/make-video.py validate-xml --lang de          # all three steps
    python3 scripts/make-video.py validate-xml --lang en --steps compose
    python3 scripts/make-video.py --series --lang de                # all films of videos/series.json in one
    python3 scripts/make-video.py --series fundsxml-business --set fundsxml   # one series of a set
    python3 scripts/make-video.py dev-setup --set fundsxml         # a storyboard of videos/fundsxml/

A set is a sub-folder of videos/ with films of their own (storyboards, one or more series files
series.json / series-<name>.json, a series may name the one that follows it with "next"; an optional
pronunciation.json that adds to the shared one); its films are copied to a sub-folder of the same
name. A storyboard may name its own card kicker ("kicker") and closing link ("link") instead of the
FreeXmlToolkit documentation. In a set with a theme.json, a scene with "slide" is drawn, not recorded
(layouts statement, bullets, tiles, steps, hub; see draw_slide); the recorded scenes between slides
become runs of the one screen recording. A storyboard with "standalone": true is not an episode of
a series; its closing card points to the series it names with "series".

Steps:
  tts      speaks every scene with Piper (a local model, no cloud service) and writes timing.json;
           the recording waits for these lengths, so picture and sound stay together in any language.
           Spoken files are cached by text in ~/.cache/fxt-video/audio.
  record   runs the storyboard's scenario class (src/test/.../screenshots/*DocVideo.java) in the real
           application under Xvfb at 1920x1080 (`./gradlew docVideos`), in the language's UI; ffmpeg
           grabs the screen, the scenario writes events.json (scenes, clicks, keys, zoom regions).
  compose  cuts the film with ffmpeg: title card, screen part with zoom, click rings and key caps,
           closing card, narration, subtitles. Output: build/videos/<id>-<lang>.mp4, .srt and .txt
           (title, description and chapters for the upload). The three files are also copied to
           $FXT_VIDEO_COPY_TO, or to a folder FXT-Videos next to the repository if it exists; a file
           over 10 MiB ($FXT_VIDEO_SPLIT_MB; a WebDAV share takes 20 MB at most) goes there as a split zip
           (<name>.zip + .z01, …) of 10 MiB parts. In a set, the copies start with the film's number ("02-…").

Needs ffmpeg, xvfb-run and Pillow. Piper and the voices are installed on first use into
~/.cache/fxt-video (venv + voices, about 115 MB per voice). The words in videos/pronunciation.json
are respelled for the speech only."""
import argparse
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import wave
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(os.environ.get("FXT_VIDEO_CACHE", Path.home() / ".cache" / "fxt-video"))
FONTS = ROOT / "src" / "main" / "resources" / "css" / "fonts"
LOGO = ROOT / "src" / "main" / "resources" / "img" / "logo-256.png"
VOICES = {"de": "de_DE-thorsten-high", "en": "en_US-lessac-high"}
SUBTITLE_LANG = {"de": "deu", "en": "eng"}
DOCS_URL = "freexmltoolkit.org/docs/"
W, H, FPS = 1920, 1080, 30
PRIMARY = (19, 115, 217)
SPLIT_MB = int(os.environ.get("FXT_VIDEO_SPLIT_MB", "10"))   # larger files go as split zips of 10 MiB parts

LEAD = 0.5          # silence before the narration on a card
INTRO_TAIL = 0.7
OUTRO_TAIL = 2.0
ZOOM_TIME = 0.7     # seconds a zoom change takes
ZOOM_MAX = 2.0
ZOOM_PAD = 90       # air around a zoomed region, px
CARD_TEXT = {
    "en": {"kicker": "FreeXmlToolkit · Tutorial", "more": "Read on:",
           "ai": "Narration voice, text and editing are AI-generated. The video shows the real application."},
    "de": {"kicker": "FreeXmlToolkit · Lernvideo", "more": "Weiterlesen:",
           "ai": "Sprecherstimme, Text und Schnitt sind KI-generiert. Das Video zeigt die echte Anwendung."},
}
# written into every film's metadata (comment), so a machine can tell the narration is synthetic (EU AI Act Art. 50)
AI_NOTE = ("AI-generated content: the narration is a synthetic voice (Piper TTS, {voice}), the text and the "
           "editing are AI-generated and reviewed by a human. The screen footage shows the real application.")
# the same for films whose texts nobody declared as reviewed (the FreeXmlToolkit tutorials; a set's theme.json
# says "reviewed": true when people checked its texts)
AI_NOTE_UNREVIEWED = ("AI-generated content: the narration is a synthetic voice (Piper TTS, {voice}), the text "
                      "and the editing are AI-generated. The screen footage shows the real application.")


BOARDS = ROOT / "videos"   # the storyboards' folder: videos/ or videos/<set>/ (--set)
SET = None


def run(cmd, **kw):
    print("+", " ".join(str(c) for c in cmd[:12]), "…" if len(cmd) > 12 else "", flush=True)
    subprocess.run([str(c) for c in cmd], check=True, **kw)


# ---------------------------------------------------------------- speech

def piper_python():
    py = CACHE / "venv" / "bin" / "python"
    if not py.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        run([sys.executable, "-m", "venv", CACHE / "venv"])
        run([py, "-m", "pip", "install", "-q", "piper-tts"])
    return py


def voice_for(board, lang):
    voice = board.get("voices", {}).get(lang) or VOICES[lang]
    voices = CACHE / "voices"
    if not (voices / f"{voice}.onnx").exists():
        voices.mkdir(parents=True, exist_ok=True)
        run([piper_python(), "-m", "piper.download_voices", voice, "--data-dir", voices])
    return voice


def respell(text, lang):
    words = json.loads((ROOT / "videos" / "pronunciation.json").read_text(encoding="utf-8")).get(lang, {})
    own = BOARDS / "pronunciation.json"
    if BOARDS != ROOT / "videos" and own.exists():
        words = {**words, **json.loads(own.read_text(encoding="utf-8")).get(lang, {})}
    for word in sorted(words, key=len, reverse=True):
        text = re.sub(r"(?<!\w)" + re.escape(word) + r"(?!\w)", words[word], text)
    return text


def wav_seconds(path):
    with wave.open(str(path)) as f:
        return f.getnframes() / f.getframerate()


def tts(board, lang, work):
    voice = voice_for(board, lang)
    audio = CACHE / "audio"
    audio.mkdir(parents=True, exist_ok=True)
    scenes = {}
    for scene in board["scenes"]:
        spoken = respell(scene["say"][lang], lang)
        key = hashlib.sha1(f"{voice}\n{spoken}".encode()).hexdigest()[:20]
        wav = audio / f"{key}.wav"
        if not wav.exists():
            run([piper_python(), "-m", "piper", "-m", voice, "--data-dir", CACHE / "voices",
                 "--sentence-silence", "0.3", "-f", wav, "--", spoken])
        scenes[scene["id"]] = {"duration": round(wav_seconds(wav), 3), "wav": str(wav)}
        print(f"  {scene['id']:10} {scenes[scene['id']]['duration']:5.1f} s  {spoken}")
    work.mkdir(parents=True, exist_ok=True)
    (work / "timing.json").write_text(json.dumps({"voice": voice, "scenes": scenes}, indent=2))


# ---------------------------------------------------------------- recording

def record(board, lang, work):
    for stale in ("screen.mkv", "events.json"):
        (work / stale).unlink(missing_ok=True)
    run(["xvfb-run", "-a", "-s", f"-screen 0 {W}x{H}x24", "./gradlew", "docVideos",
         "--tests", "*" + board["scenario"], f"-Pvideo.dir={work}", f"-Pvideo.lang={lang}"], cwd=ROOT)
    if not (work / "events.json").exists():
        sys.exit("The scenario wrote no events.json - see the test report in build/reports/tests/docVideos")


# ---------------------------------------------------------------- pictures

def font(name, size):
    return ImageFont.truetype(str(FONTS / f"{name}.ttf"), size)


def wrap(draw, text, fnt, width):
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=fnt) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + [line]


def card(path, kicker, title, footer):
    img = Image.new("RGB", (W, H), (246, 248, 251))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 14], fill=PRIMARY)
    logo = Image.open(LOGO).convert("RGBA").resize((168, 168), Image.LANCZOS)
    img.paste(logo, (160, 250), logo)
    d.text((160, 470), kicker, font=font("Inter-SemiBold", 40), fill=PRIMARY)
    y = 540
    size = 84
    while size > 44 and max(d.textlength(w, font=font("Inter-Bold", size)) for w in title.split()) > W - 320:
        size -= 4   # a long word (a URL) must fit the card
    title_font = font("Inter-Bold", size)
    for line in wrap(d, title, title_font, W - 320):
        d.text((160, y), line, font=title_font, fill=(15, 23, 42))
        y += 104
    d.text((160, H - 130), footer, font=font("Inter-Medium", 34), fill=(71, 85, 105))
    img.save(path)


def click_ring(path):
    size = 56
    img = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([12, 12, size * 4 - 12, size * 4 - 12], fill=PRIMARY + (40,), outline=PRIMARY + (220,), width=16)
    img.resize((size, size), Image.LANCZOS).save(path)
    return size


def key_cap(path, label):
    fnt = font("Inter-Bold", 54)
    width = int(ImageDraw.Draw(Image.new("RGB", (1, 1))).textlength(label, font=fnt)) + 84
    img = Image.new("RGBA", (width, 112), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, width - 1, 111], radius=20, fill=(15, 23, 42, 225), outline=(255, 255, 255, 200), width=3)
    d.text((width / 2, 54), label, font=fnt, fill="white", anchor="mm")
    img.save(path)


# ---------------------------------------------------------------- branded cards (videos/<set>/theme.json)

def load_theme():
    """The set's card design (theme.json next to its storyboards), or None for the FreeXmlToolkit cards."""
    path = BOARDS / "theme.json"
    if BOARDS == ROOT / "videos" or not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def film_theme(theme, board):
    """The set's theme for one film: the colours and labels of the film's audience ("audience" in the
    storyboard, a key of the theme's "audiences") over the set's own. Without an audience: the theme as is."""
    if not theme or not board.get("audience"):
        return theme
    audience = theme.get("audiences", {}).get(board["audience"])
    if audience is None:
        sys.exit(f"unknown audience {board['audience']!r} (theme.json: {', '.join(theme.get('audiences', {}))})")
    out = json.loads(json.dumps(theme))
    out["colors"].update(audience.get("colors", {}))
    out["_audience"] = audience
    return out


def series_specs():
    """The set's series files (series.json, series-<name>.json), each a dict with its "_path"."""
    out = []
    for path in sorted(BOARDS.glob("series*.json")):
        spec = json.loads(path.read_text(encoding="utf-8"))
        spec["_path"] = path
        out.append(spec)
    return out


def series_of(board):
    """The series that lists the storyboard; for one that is in none, the one it names with "series"
    (or the set's only series)."""
    specs = series_specs()
    for spec in specs:
        if board["id"] in spec["videos"]:
            return spec
    named = board.get("series")
    return next((s for s in specs if s["id"] == named), specs[0] if len(specs) == 1 else None)


def spec_by_id(spec_id):
    return next((s for s in series_specs() if s["id"] == spec_id), None)


def place_in_series(board):
    """(episode number, number of episodes, the next storyboard or None) - from the board's series."""
    spec = series_of(board)
    if not spec:
        return None, None, None
    ids = spec["videos"]
    if board["id"] not in ids:
        return None, len(ids), None
    i = ids.index(board["id"])
    following = BOARDS / f"{ids[i + 1]}.json" if i + 1 < len(ids) else None
    nxt = json.loads(following.read_text(encoding="utf-8")) if following and following.exists() else None
    return i + 1, len(ids), nxt


def rgb(hex_colour):
    h = hex_colour.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


class Brand:
    def __init__(self, theme):
        self.theme = theme
        self.c = {k: rgb(v) for k, v in theme["colors"].items()}

    def font(self, size, bold=False):
        return ImageFont.truetype(str(BOARDS / self.theme["font_bold" if bold else "font"]), size)

    def canvas(self, audience_tag=True):
        """White page with the bar on the left (in the colour of the film's audience) and the logo at
        the top left (as the presentation template's title slide); with an audience, its label at the
        top right."""
        img = Image.new("RGB", (W, H), self.c["background"])
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 27, H], fill=self.c["bar"])
        logo = Image.open(BOARDS / self.theme["logo"]).convert("RGBA")
        height = self.theme.get("logo_height", 58)
        logo = logo.resize((round(logo.width * height / logo.height), height), Image.LANCZOS)
        img.paste(logo, (160, 120), logo)
        if audience_tag:
            self.audience_tag(d)
        return img, d

    def tint(self, share=0.16):
        """The accent colour, much lighter: the background of marked lines and rows."""
        return tuple(round(255 + (c - 255) * share) for c in self.c["accent"])

    def audience_label(self, lang="en"):
        audience = self.theme.get("_audience")
        return audience["label"].get(lang) if audience else None

    def audience_tag(self, d, lang="en"):
        """The audience as a filled pill in its colour, at the top right."""
        label = self.audience_label(lang)
        if not label:
            return
        fnt = self.font(28, True)
        width, height = d.textlength(label, font=fnt) + 48, 56
        right, top = W - 120, 128
        d.rounded_rectangle([right - width, top, right, top + height], radius=height // 2, fill=self.c["accent"])
        d.text((right - width / 2, top + height / 2), label, font=fnt, fill=(255, 255, 255), anchor="mm")

    def accent(self, img, x, y, width, height=14):
        """The accent line in the brand's blue-to-green gradient."""
        a, b = self.c["accent"], self.c["accent_to"]
        line = Image.new("RGB", (width, height))
        ld = ImageDraw.Draw(line)
        for i in range(width):
            u = i / max(1, width - 1)
            ld.line([(i, 0), (i, height)], fill=tuple(round(a[k] + (b[k] - a[k]) * u) for k in range(3)))
        mask = Image.new("L", (width, height), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, width - 1, height - 1], radius=height // 2, fill=255)
        img.paste(line, (x, y), mask)

    def title_lines(self, d, text, max_size, min_size, width, max_lines):
        size = max_size
        while size > min_size:
            fnt = self.font(size, True)
            lines = wrap(d, text, fnt, width)
            if len(lines) <= max_lines and max(d.textlength(l, font=fnt) for l in lines) <= width:
                return fnt, lines, size
            size -= 4
        fnt = self.font(min_size, True)
        return fnt, wrap(d, text, fnt, width), min_size


def brand_intro(path, theme, lang, board, series_title):
    b = Brand(theme)
    t = theme["text"][lang]
    img, d = b.canvas(audience_tag=False)
    n, total, _ = place_in_series(board)
    own = board.get("kicker", {}).get(lang)
    audience = b.audience_label(lang)
    if audience:
        kicker = f"{audience}  ·  " + (t["episode"].format(n=n, total=total) if n else own or t["series_kicker"])
    else:
        kicker = t["kicker"].format(n=n, total=total) if n else own or t["series_kicker"]
    if board.get("standalone"):   # not an episode: its own subtitle instead of the series' name
        series_title = board.get("subtitle", {}).get(lang)
    d.text((160, 330), kicker, font=b.font(40, True), fill=b.c["accent"])
    b.accent(img, 160, 410, 260)
    fnt, lines, size = b.title_lines(d, board["title"][lang], 96, 60, W - 400, 3)
    y = 460
    for line in lines:
        d.text((160, y), line, font=fnt, fill=b.c["heading"])
        y += round(size * 1.22)
    if series_title:
        d.text((160, y + 30), series_title, font=b.font(46), fill=b.c["text"])
    d.text((160, H - 130), t["footer"], font=b.font(34), fill=b.c["text"])
    if t.get("ai_label"):
        ai_badge(d, b, t["ai_label"])
    img.save(path)


def ai_badge(d, b, label):
    """The AI label at the bottom right of the title card: an "AI" tag and a short text in a framed pill,
    seen from the first second of the film (EU AI Act Art. 50, Code of Practice: visible at first exposure)."""
    fnt, tag_fnt = b.font(30), b.font(28, True)
    text_w = d.textlength(label, font=fnt)
    tag_w = d.textlength("AI", font=tag_fnt) + 28
    height, pad, gap = 60, 14, 16
    right, top = W - 120, H - 140
    left = right - (pad + tag_w + gap + text_w + pad + 8)
    d.rounded_rectangle([left, top, right, top + height], radius=height // 2, outline=b.c["border"], width=2,
                        fill=b.c["panel"])
    tx = left + pad
    d.rounded_rectangle([tx, top + 11, tx + tag_w, top + height - 11], radius=10, fill=b.c["heading"])
    d.text((tx + tag_w / 2, top + height / 2), "AI", font=tag_fnt, fill=(255, 255, 255), anchor="mm")
    d.text((tx + tag_w + gap, top + height / 2), label, font=fnt, fill=b.c["heading"], anchor="lm")


def series_titles(lang, spec):
    """[(storyboard id, title)] of a series, planned films by their title in "planned"."""
    if not spec:
        return []
    out = []
    for vid in spec["videos"]:
        board = BOARDS / f"{vid}.json"
        if board.exists():
            out.append((vid, json.loads(board.read_text(encoding="utf-8"))["title"][lang]))
        else:
            out.append((vid, spec.get("planned", {}).get(vid, {}).get(lang, vid)))
    return out


def brand_outro(path, theme, lang, board, series_title):
    b = Brand(theme)
    t = theme["text"][lang]
    img, d = b.canvas()
    d.text((160, 225), t["thanks"], font=b.font(80, True), fill=b.c["heading"])
    b.accent(img, 160, 335, 260)
    # the disclaimer card at the bottom (measured first: the series list must end above it)
    note_fnt = b.font(25)
    left, right, bottom = 160, W - 160, H - 50
    lines = wrap(d, t["disclaimer"], note_fnt, right - left - 64)
    top = bottom - 40 - 35 * len(lines)
    # left column: the series, this film marked, the next one named
    spec = series_of(board)
    continuing = board.get("standalone")
    if spec and spec["videos"] and spec["videos"][-1] == board["id"] and spec.get("next"):
        # the last film of a series points to the series that follows it
        spec, continuing = spec_by_id(spec["next"]) or spec, True
        series_title = spec["title"].get(lang)
    episodes = series_titles(lang, spec)
    if episodes:
        heading = t.get("series_continue", t["series"]) if continuing else t["series"]
        d.text((160, 395), heading + (f": {series_title}" if series_title else ""), font=b.font(34, True),
               fill=b.c["accent"])
        ids = [vid for vid, _ in episodes]
        here = ids.index(board["id"]) if board["id"] in ids else -1
        y = 455
        step = min(56, (top - 24 - 40 - y) / max(1, len(episodes) - 1))   # 8 films fit above the disclaimer
        for i, (vid, title) in enumerate(episodes):
            current, upcoming = i == here, i == here + 1
            fnt = b.font(30, current or upcoming)
            colour = b.c["accent"] if current else b.c["heading"] if upcoming else b.c["text"]
            label = title
            while d.textlength(label, font=fnt) > 720 and len(label) > 10:
                label = label[:-2].rstrip() + "…"
            d.text((160, y), str(i + 1), font=fnt, fill=colour)
            d.text((200, y), label, font=fnt, fill=colour)
            if upcoming:
                d.text((200 + d.textlength(label, font=fnt) + 18, y + 4), t["next"], font=b.font(24), fill=b.c["text"])
            y += step
    # right column: where to read on
    x = 1040
    d.text((x, 395), t["more"], font=b.font(34, True), fill=b.c["accent"])
    y = 455
    for label, url in t["links"]:
        d.text((x, y), label, font=b.font(28), fill=b.c["text"])
        d.text((x, y + 34), url, font=b.font(36, True), fill=b.c["heading"])
        y += 92
    # the disclaimer card
    d.rounded_rectangle([left, top, right, bottom], radius=12, fill=b.c["panel"], outline=b.c["border"], width=2)
    y = top + 20
    for line in lines:
        d.text((left + 32, y), line, font=note_fnt, fill=b.c["heading"])
        y += 35
    img.save(path)


# ---------------------------------------------------------------- slides (scenes with "slide", branded sets only)

SLIDE_TAIL = 0.8    # seconds a slide stays after its narration
BUILD_FADE = 0.4    # seconds a built-up element takes to fade in


def slide_units(slide, lang):
    """The number of parts of a slide that appear one after the other (see draw_slide)."""
    text = slide[lang]
    layout = slide["layout"]
    n = {"statement": 1 + bool(text.get("sub")), "hub": 2,
         "code": 1 + bool(text.get("highlight")), "table": 1 + bool(text.get("mark")),
         "image": 1}.get(layout)
    if layout == "terminal":
        n = len(text["blocks"])
    elif layout in ("er", "flow"):
        parts = text["tables"] if layout == "er" else text["nodes"]
        n = 1 + max(p.get("group", 0) for p in parts) + bool(text.get("mark"))
    elif not n:
        n = len(text["items"])
    return n + bool(text.get("note"))


def slide_states(slide, lang):
    """[(fraction of the narration, indexes of the parts shown)]: "builds" gives each part's fraction
    (missing ones: 0), the slide changes at every distinct one."""
    units = slide_units(slide, lang)
    builds = list(slide.get("builds", []))[:units]
    builds += [0.0] * (units - len(builds))
    return [(f, {i for i, b in enumerate(builds) if b <= f}) for f in sorted(set(builds))]


def draw_slide(path, theme, lang, slide, shown):
    """One state of a slide: the brand's page, a title, and the layout's parts whose index is in shown.
    Layouts: statement (headline, sub), bullets, tiles, steps (items with head and text), hub (actors
    around a centre: "mesh" joins every actor with every other, "hub" all through the centre), code (a
    listing; "highlight" lines appear marked as a second part), table ("columns", "rows"; "mark" rows as a
    second part), image (a picture, e.g. a web page, path relative to the set's folder)."""
    b = Brand(theme)
    t = slide[lang]
    layout = slide["layout"]
    img, d = b.canvas()
    top = 230
    if t.get("title"):
        d.text((160, top), t["title"], font=b.font(60, True), fill=b.c["heading"])
        b.accent(img, 160, top + 92, 200, 10)
        top += 150
    bottom = H - 90
    note = t.get("note")
    if note:
        bottom -= 70
    units = slide_units(slide, lang)

    if layout == "statement":
        fnt, lines, size = b.title_lines(d, t["headline"], 92, 56, W - 420, 4)
        sub = wrap(d, t.get("sub", ""), b.font(44), W - 420) if t.get("sub") else []
        height = len(lines) * size * 1.2 + (40 + len(sub) * 58 if sub else 0)
        y = top + (bottom - top - height) / 2
        if 0 in shown:
            for line in lines:
                d.text((160, y), line, font=fnt, fill=b.c["heading"])
                y += size * 1.2
        else:
            y += len(lines) * size * 1.2
        if sub and 1 in shown:
            y += 40
            for line in sub:
                d.text((160, y), line, font=b.font(44), fill=b.c["text"])
                y += 58

    elif layout == "bullets":
        items = t["items"]
        head_f, text_f = b.font(44, True), b.font(34)
        blocks = []
        for item in items:
            head, body = (item, "") if isinstance(item, str) else (item["head"], item.get("text", ""))
            blocks.append((head, wrap(d, body, text_f, W - 560) if body else []))
        heights = [58 + 46 * len(body) for _, body in blocks]
        gap = min(90, max(24, (bottom - top - sum(heights)) / max(1, len(blocks))))
        y = top + 10
        for i, ((head, body), h) in enumerate(zip(blocks, heights)):
            if i in shown:
                d.ellipse([168, y + 22, 190, y + 44], fill=b.c["accent"])
                d.text((230, y), head, font=head_f, fill=b.c["heading"])
                for j, line in enumerate(body):
                    d.text((230, y + 62 + j * 46), line, font=text_f, fill=b.c["text"])
            y += h + gap

    elif layout == "tiles":
        items = t["items"]
        n = len(items)
        cols = {1: 1, 2: 2, 3: 3, 4: 2, 5: 3, 6: 3, 7: 4, 8: 4}.get(n, 4)
        rows = -(-n // cols)
        gap = 32
        tw = (W - 320 - gap * (cols - 1)) / cols
        th = min(300, (bottom - top - gap * (rows - 1)) / rows)
        head_size = 36 if cols < 4 else 32
        for i, item in enumerate(items):
            if i not in shown:
                continue
            x = 160 + (i % cols) * (tw + gap)
            y = top + (i // cols) * (th + gap)
            d.rounded_rectangle([x, y, x + tw, y + th], radius=16, fill=b.c["panel"], outline=b.c["border"], width=2)
            b.accent(img, int(x + 28), int(y + 26), 90, 8)
            hf = b.font(head_size, True)
            heads = wrap(d, item["head"], hf, tw - 56)
            yy = y + 54
            for line in heads:
                d.text((x + 28, yy), line, font=hf, fill=b.c["heading"])
                yy += head_size * 1.25
            tf = b.font(27 if cols < 4 else 25)
            for line in wrap(d, item.get("text", ""), tf, tw - 56):
                if yy + 36 > y + th - (52 if item.get("tag") else 14):
                    break
                d.text((x + 28, yy + 6), line, font=tf, fill=b.c["text"])
                yy += 36
            if item.get("tag"):
                tagf = b.font(22, True)
                width = d.textlength(item["tag"], font=tagf) + 28
                d.rounded_rectangle([x + 28, y + th - 50, x + 28 + width, y + th - 16], radius=17, fill=b.c["accent"])
                d.text((x + 42, y + th - 33), item["tag"], font=tagf, fill=(255, 255, 255), anchor="lm")

    elif layout == "steps":
        items = t["items"]
        n = len(items)
        gap = 56
        sw = (W - 320 - gap * (n - 1)) / n
        cy = top + (bottom - top) / 2 - 130   # the chain sits in the middle of the free space
        for i, item in enumerate(items):
            x = 160 + i * (sw + gap)
            if i not in shown:
                continue
            if i > 0:   # the arrow from the step before
                ax = x - gap + 10
                d.line([(ax, cy), (x - 14, cy)], fill=b.c["text"], width=5)
                d.polygon([(x - 6, cy), (x - 24, cy - 13), (x - 24, cy + 13)], fill=b.c["text"])
            d.ellipse([x + sw / 2 - 44, cy - 44, x + sw / 2 + 44, cy + 44], fill=b.c["accent"])
            d.text((x + sw / 2, cy), str(i + 1), font=b.font(44, True), fill=(255, 255, 255), anchor="mm")
            hf = b.font(34 if n <= 5 else 30, True)
            y = cy + 80
            for line in wrap(d, item["head"], hf, sw):
                d.text((x + sw / 2, y), line, font=hf, fill=b.c["heading"], anchor="ma")
                y += 44
            tf = b.font(27 if n <= 5 else 25)
            y += 14
            for line in wrap(d, item.get("text", ""), tf, sw):
                d.text((x + sw / 2, y), line, font=tf, fill=b.c["text"], anchor="ma")
                y += 37

    elif layout == "hub":
        actors = t["items"]
        cx, cy = W / 2, (top + bottom) / 2 + 10
        rx, ry = 640, (bottom - top) / 2 - 50
        pf = b.font(32, True)
        boxes = []
        for i, name in enumerate(actors):
            a = -math.pi / 2 + 2 * math.pi * i / len(actors)
            x, y = cx + rx * math.cos(a), cy + ry * math.sin(a)
            w = d.textlength(name, font=pf) + 64
            boxes.append((x, y, w))
        mode = slide.get("mode", "hub")
        if 1 in shown:
            if mode == "mesh":
                for i in range(len(boxes)):
                    for j in range(i + 1, len(boxes)):
                        d.line([boxes[i][:2], boxes[j][:2]], fill=(203, 213, 224), width=4)
            else:
                for x, y, _ in boxes:
                    d.line([(cx, cy), (x, y)], fill=b.c["accent"], width=6)
        if 0 in shown:
            for name, (x, y, w) in zip(actors, boxes):
                d.rounded_rectangle([x - w / 2, y - 38, x + w / 2, y + 38], radius=38, fill=b.c["panel"],
                                    outline=b.c["border"], width=3)
                d.text((x, y), name, font=pf, fill=b.c["heading"], anchor="mm")
        if 1 in shown and mode == "hub":
            label = t.get("centre", "FundsXML")
            cf = b.font(54, True)
            w = d.textlength(label, font=cf) + 120
            d.rounded_rectangle([cx - w / 2, cy - 62, cx + w / 2, cy + 62], radius=62, fill=b.c["accent"])
            d.text((cx, cy), label, font=cf, fill=(255, 255, 255), anchor="mm")

    elif layout == "code":
        lines = t["code"].rstrip("\n").split("\n")
        size = max(20, min(34, int((bottom - top - 80) / (len(lines) * 1.45))))
        mono = ImageFont.truetype(str(FONTS / "JetBrainsMono-Regular.ttf"), size, layout_engine=ImageFont.Layout.BASIC)
        longest = max(d.textlength(l, font=mono) for l in lines)
        while longest > W - 420 and size > 18:
            size -= 1
            mono = ImageFont.truetype(str(FONTS / "JetBrainsMono-Regular.ttf"), size, layout_engine=ImageFont.Layout.BASIC)
            longest = max(d.textlength(l, font=mono) for l in lines)
        step = round(size * 1.45)
        box_h = len(lines) * step + 70
        y0 = top + 10
        d.rounded_rectangle([160, y0, W - 160, y0 + box_h], radius=16, fill=b.c["panel"], outline=b.c["border"], width=2)
        if t.get("file"):
            ff = b.font(26, True)
            tw = d.textlength(t["file"], font=ff) + 32
            d.rounded_rectangle([W - 160 - tw - 20, y0 - 22, W - 180, y0 + 22], radius=22, fill=b.c["heading"])
            d.text((W - 180 - tw / 2, y0), t["file"], font=ff, fill=(255, 255, 255), anchor="mm")
        marked = set(t.get("highlight", [])) if 1 in shown else set()
        for i, line in enumerate(lines):
            y = y0 + 35 + i * step
            if i + 1 in marked:
                d.rectangle([162, y - 6, W - 162, y + step - 6], fill=b.tint())
                d.rectangle([162, y - 6, 170, y + step - 6], fill=b.c["accent"])
            d.text((200, y), line, font=mono, fill=b.c["heading"])

    elif layout == "table":
        cols, rows = t["columns"], t["rows"]
        cells = [[str(c) for c in cols]] + [[str(c) for c in r] for r in rows]
        size = max(18, min(32, int((bottom - top - 40) / ((len(rows) + 1) * 1.9))))
        while True:   # the largest font at which every column fits the width
            hf, cf = b.font(size, True), b.font(size)
            widths = [max(d.textlength(row[i], font=hf if k == 0 else cf) for k, row in enumerate(cells)) + 40
                      for i in range(len(cols))]
            if sum(widths) <= W - 320 or size <= 16:
                break
            size -= 1
        row_h = round(size * 1.9)
        y = top + 10
        d.rounded_rectangle([160, y, 160 + sum(widths), y + row_h * len(cells)], radius=12, fill=(255, 255, 255),
                            outline=b.c["border"], width=2)
        marked = set(t.get("mark", [])) if 1 in shown else set()
        for k, row in enumerate(cells):
            yy = y + k * row_h
            if k == 0:
                d.rounded_rectangle([160, yy, 160 + sum(widths), yy + row_h], radius=12, fill=b.c["panel"])
            elif k - 1 in marked:
                d.rectangle([162, yy, 158 + sum(widths), yy + row_h], fill=b.tint())
            if k > 0:
                d.line([(160, yy), (160 + sum(widths), yy)], fill=b.c["border"], width=1)
            x = 160
            for i, cell in enumerate(row):
                d.text((x + 24, yy + row_h / 2), cell, font=hf if k == 0 else cf,
                       fill=b.c["heading"] if k == 0 or k - 1 in marked else b.c["text"], anchor="lm")
                x += widths[i]

    elif layout == "terminal":
        draw_terminal(b, d, t, shown, top, bottom)

    elif layout == "er":
        draw_er(b, img, d, t, shown, top, bottom)

    elif layout == "flow":
        draw_flow(b, d, t, shown, top, bottom)

    elif layout == "image":
        pic = Image.open(BOARDS / t["image"]).convert("RGB")
        box_w, box_h = W - 320, bottom - top - 10
        f = min(box_w / pic.width, box_h / pic.height)
        pic = pic.resize((round(pic.width * f), round(pic.height * f)), Image.LANCZOS)
        x, y = 160 + (box_w - pic.width) // 2, top + 10
        d.rounded_rectangle([x - 3, y - 3, x + pic.width + 2, y + pic.height + 2], radius=10, outline=b.c["border"], width=3)
        img.paste(pic, (x, y))

    if note and units - 1 in shown:
        nf = b.font(34)
        b.accent(img, 160, H - 128, 8, 46)
        d.text((190, H - 105), note, font=nf, fill=b.c["heading"], anchor="lm")
    img.save(path)


TERM_BG, TERM_BAR, TERM_TEXT, TERM_DIM, TERM_MARK = (22, 27, 38), (44, 51, 66), (226, 232, 240), (139, 150, 168), (49, 58, 78)


def mono_font(size):
    return ImageFont.truetype(str(FONTS / "JetBrainsMono-Regular.ttf"), size, layout_engine=ImageFont.Layout.BASIC)


def draw_terminal(b, d, t, shown, top, bottom):
    """A terminal window whose output appears block by block ("blocks": texts; block i is part i).
    Lines starting with "$ " are commands (in the accent colour), lines starting with "#" comments
    (dimmed); "highlight" marks lines (1-based over all blocks) once their block is shown. Lines longer
    than "columns" (116) wrap, as in a terminal of that width."""
    width = t.get("columns", 116)   # like a terminal of that many columns: longer lines wrap
    marked_src = set(t.get("highlight", []))
    lines, marked, n = [], set(), 0
    for k, block in enumerate(t["blocks"]):
        for line in block.rstrip("\n").split("\n"):
            n += 1
            for c in range(0, max(1, len(line)), width):
                lines.append((k, line[c:c + width] if c == 0 else "  " + line[c:c + width]))
                if n in marked_src:
                    marked.add(len(lines))
    size = max(14, min(30, int((bottom - top - 110) / (len(lines) * 1.42))))
    mono = mono_font(size)
    while max(d.textlength(l, font=mono) for _, l in lines) > W - 400 and size > 14:
        size -= 1
        mono = mono_font(size)
    step = round(size * 1.42)
    y0 = top + 10
    box_h = 56 + len(lines) * step + 40
    d.rounded_rectangle([160, y0, W - 160, y0 + box_h], radius=14, fill=TERM_BG)
    d.rounded_rectangle([160, y0, W - 160, y0 + 56], radius=14, fill=TERM_BAR)
    d.rectangle([160, y0 + 40, W - 160, y0 + 56], fill=TERM_BAR)
    for i, colour in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        d.ellipse([190 + i * 34, y0 + 18, 210 + i * 34, y0 + 38], fill=colour)
    d.text((W / 2, y0 + 28), t.get("window", "bash"), font=b.font(24, True), fill=TERM_DIM, anchor="mm")
    prompt = tuple(round(255 + (c - 255) * 0.75) for c in b.c["accent_to"])
    for i, (k, line) in enumerate(lines):
        if k not in shown:
            continue
        y = y0 + 56 + 20 + i * step
        if i + 1 in marked:
            d.rectangle([162, y - 5, W - 162, y + step - 5], fill=TERM_MARK)
            d.rectangle([162, y - 5, 168, y + step - 5], fill=b.c["accent_to"])
        colour = prompt if line.startswith("$ ") else TERM_DIM if line.lstrip().startswith("#") else TERM_TEXT
        d.text((196, y), line, font=mono, fill=colour)


def grid_boxes(items, top, bottom, box_w_share=0.8):
    """Centres and cell size of items placed on a grid by "col" and "row"."""
    cols = 1 + max(i.get("col", 0) for i in items)
    rows = 1 + max(i.get("row", 0) for i in items)
    cw, ch = (W - 320) / cols, (bottom - top - 10) / rows
    return {i["id"] if "id" in i else i["name"]: (160 + cw * (i.get("col", 0) + 0.5), top + 10 + ch * (i.get("row", 0) + 0.5))
            for i in items}, cw, ch


def edge_point(box, toward):
    """Where the line from the centre of box (x0, y0, x1, y1) to the point toward leaves the box."""
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    dx, dy = toward[0] - cx, toward[1] - cy
    if dx == 0 and dy == 0:
        return cx, cy
    sx = (x1 - x0) / 2 / abs(dx) if dx else float("inf")
    sy = (y1 - y0) / 2 / abs(dy) if dy else float("inf")
    f = min(sx, sy)
    return cx + dx * f, cy + dy * f


def arrow_head(d, tip, frm, colour, size=18):
    ang = math.atan2(tip[1] - frm[1], tip[0] - frm[0])
    pts = [tip] + [(tip[0] - size * math.cos(ang + s), tip[1] - size * math.sin(ang + s)) for s in (0.45, -0.45)]
    d.polygon(pts, fill=colour)


def draw_er(b, img, d, t, shown, top, bottom):
    """An entity-relationship diagram: "tables" ({"name", "col", "row", "columns", "group"}) on a grid,
    "links" [[child, parent]] drawn as lines with a crow's foot at the child once both are shown;
    "mark" (table names) is filled with the accent colour as the last part. A column is "name",
    "name PK" or "name FK"; keys are written in bold."""
    groups = 1 + max(x.get("group", 0) for x in t["tables"])
    marked = set(t.get("mark", [])) if groups in shown else set()
    centres, cw, ch = grid_boxes(t["tables"], top, bottom)
    longest = max(len(x["columns"]) for x in t["tables"])
    row_h = max(24, min(34, int((ch - 70) / (longest + 1.4))))
    size = round(row_h * 0.72)
    hf, cf = b.font(size + 3, True), mono_font(size - 2)
    boxes = {}
    for x in t["tables"]:
        cx, cy = centres[x["name"]]
        width = max([d.textlength(x["name"], font=hf) + 40] + [d.textlength(c, font=cf) + 40 for c in x["columns"]])
        width = min(width, cw - 24)
        height = row_h * (len(x["columns"]) + 1.3)
        boxes[x["name"]] = (cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2)
    visible = {x["name"] for x in t["tables"] if x.get("group", 0) in shown}
    for child, parent in t.get("links", []):
        if child in visible and parent in visible:
            a, z = boxes[child], boxes[parent]
            ca, cz = ((a[0] + a[2]) / 2, (a[1] + a[3]) / 2), ((z[0] + z[2]) / 2, (z[1] + z[3]) / 2)
            p, q = edge_point(a, cz), edge_point(z, ca)
            d.line([p, q], fill=b.c["text"], width=3)
            ang = math.atan2(q[1] - p[1], q[0] - p[0])   # crow's foot at the child (many)
            base = (p[0] + 22 * math.cos(ang), p[1] + 22 * math.sin(ang))
            for side in (-1, 0, 1):
                foot = (p[0] + 16 * math.cos(ang + math.pi / 2) * side, p[1] + 16 * math.sin(ang + math.pi / 2) * side)
                d.line([base, foot], fill=b.c["text"], width=3)
            bar = (q[0] - 16 * math.cos(ang), q[1] - 16 * math.sin(ang))   # one at the parent
            d.line([(bar[0] + 12 * math.cos(ang + math.pi / 2), bar[1] + 12 * math.sin(ang + math.pi / 2)),
                    (bar[0] - 12 * math.cos(ang + math.pi / 2), bar[1] - 12 * math.sin(ang + math.pi / 2))], fill=b.c["text"], width=3)
    for x in t["tables"]:
        if x["name"] not in visible:
            continue
        x0, y0, x1, y1 = boxes[x["name"]]
        mark = x["name"] in marked
        d.rounded_rectangle([x0, y0, x1, y1], radius=10, fill=b.tint() if mark else (255, 255, 255),
                            outline=b.c["accent"] if mark else b.c["border"], width=4 if mark else 2)
        d.rounded_rectangle([x0, y0, x1, y0 + row_h * 1.2], radius=10, fill=b.c["accent"] if mark else b.c["heading"])
        d.rectangle([x0, y0 + row_h * 0.8, x1, y0 + row_h * 1.2], fill=b.c["accent"] if mark else b.c["heading"])
        d.text(((x0 + x1) / 2, y0 + row_h * 0.6), x["name"], font=hf, fill=(255, 255, 255), anchor="mm")
        for k, col in enumerate(x["columns"]):
            yy = y0 + row_h * (1.3 + k + 0.5)
            key = col.endswith(" PK") or col.endswith(" FK")
            d.text((x0 + 18, yy), col, font=cf, fill=b.c["heading"] if key else b.c["text"], anchor="lm")


FLOW_KINDS = {"ok": ((230, 247, 238), (47, 133, 90)), "error": ((253, 236, 236), (197, 48, 48)),
              "warn": ((254, 245, 224), (192, 120, 20))}


def draw_flow(b, d, t, shown, top, bottom):
    """A flow diagram: "nodes" ({"id", "label", "sub", "col", "row", "kind", "group"}) on a grid and
    "edges" ({"from", "to", "label", "kind", "route": "vh" for down-then-sideways}) as arrows, drawn once
    both ends are shown. Kinds: box (default),
    active (filled with the accent), ok, warn, error, store. "mark" (node ids) fills nodes with the accent
    as the last part."""
    groups = 1 + max(n.get("group", 0) for n in t["nodes"])
    marked = set(t.get("mark", [])) if groups in shown else set()
    centres, cw, ch = grid_boxes(t["nodes"], top, bottom)
    lf, sf = b.font(30 if cw > 330 else 26, True), b.font(23 if cw > 330 else 21)
    boxes = {}
    for n in t["nodes"]:
        cx, cy = centres[n["id"]]
        width = min(cw - 70, max(d.textlength(n["label"], font=lf), d.textlength(n.get("sub", ""), font=sf)) + 48)
        sub = wrap(d, n.get("sub", ""), sf, width - 32) if n.get("sub") else []
        height = 66 + 30 * len(sub)
        boxes[n["id"]] = (cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2, sub)
    visible = {n["id"] for n in t["nodes"] if n.get("group", 0) in shown}
    ef = b.font(22)
    for e in t.get("edges", []):
        if e["from"] not in visible or e["to"] not in visible:
            continue
        a, z = boxes[e["from"]][:4], boxes[e["to"]][:4]
        ca, cz = ((a[0] + a[2]) / 2, (a[1] + a[3]) / 2), ((z[0] + z[2]) / 2, (z[1] + z[3]) / 2)
        if abs(ca[1] - cz[1]) < 2 or abs(ca[0] - cz[0]) < 2:   # same row or column: straight
            p, q = edge_point(a, cz), edge_point(z, ca)
            path = [p, q]
        elif e.get("route") == "vh":   # an elbow: down or up out of the source, then sideways into the target
            p = (ca[0], a[3] if cz[1] > ca[1] else a[1])
            q = (z[0] if cz[0] > ca[0] else z[2], cz[1])
            path = [p, (ca[0], cz[1]), q]
        else:   # an elbow: out of the side, then up or down into the target
            sx = a[2] if cz[0] > ca[0] else a[0]
            p = (sx, ca[1])
            q = (cz[0], z[1] if cz[1] > ca[1] else z[3])
            path = [p, (cz[0], ca[1]), q]
        colour = b.c["text"]
        if e.get("kind") in FLOW_KINDS:
            colour = FLOW_KINDS[e["kind"]][1]
        d.line(path, fill=colour, width=4, joint="curve")
        arrow_head(d, path[-1], path[-2], colour)
        if e.get("label"):
            mx, my = (path[0][0] + path[1][0]) / 2, (path[0][1] + path[1][1]) / 2
            w = d.textlength(e["label"], font=ef) + 16
            d.rounded_rectangle([mx - w / 2, my - 16, mx + w / 2, my + 16], radius=8, fill=(255, 255, 255))
            d.text((mx, my), e["label"], font=ef, fill=colour, anchor="mm")
    for n in t["nodes"]:
        if n["id"] not in visible:
            continue
        x0, y0, x1, y1, sub = boxes[n["id"]]
        kind = "active" if n["id"] in marked else n.get("kind", "box")
        fill, line, text = b.c["panel"], b.c["border"], b.c["heading"]
        if kind == "active":
            fill, line, text = b.c["accent"], b.c["accent"], (255, 255, 255)
        elif kind == "store":
            fill, line = b.tint(), b.c["accent"]
        elif kind in FLOW_KINDS:
            fill, line = FLOW_KINDS[kind]
        d.rounded_rectangle([x0, y0, x1, y1], radius=14, fill=fill, outline=line, width=3)
        d.text(((x0 + x1) / 2, y0 + 34), n["label"], font=lf, fill=text, anchor="mm")
        for k, s_line in enumerate(sub):
            d.text(((x0 + x1) / 2, y0 + 70 + 30 * k), s_line, font=sf,
                   fill=(255, 255, 255) if kind == "active" else b.c["text"], anchor="mm")


# ---------------------------------------------------------------- cut

def zoom_states(events, t0):
    """[(time, zoom, centre x, centre y)] - the picture's state from each time on."""
    states = [(0.0, 1.0, W / 2, H / 2)]
    for e in events:
        if e["type"] == "zoom-out":
            states.append((e["t"] - t0, 1.0, W / 2, H / 2))
        elif e["type"] == "zoom":
            z = min(ZOOM_MAX, W / (e["w"] + 2 * ZOOM_PAD), H / (e["h"] + 2 * ZOOM_PAD))
            z = max(1.0, z)
            cx = min(max(e["x"] + e["w"] / 2, W / z / 2), W - W / z / 2)
            cy = min(max(e["y"] + e["h"] / 2, H / z / 2), H - H / z / 2)
            states.append((e["t"] - t0, z, cx, cy))
    return states


def eased(states, index, scale=1.0):
    """An ffmpeg expression of T (seconds): the states' component, eased from one to the next."""
    expr = f"{states[-1][index] * scale:.3f}"
    for i in range(len(states) - 1, 0, -1):
        a, b, t = states[i - 1][index] * scale, states[i][index] * scale, states[i][0]
        u = f"((T-{t:.3f})/{ZOOM_TIME})"
        move = f"({a:.3f}+({b - a:.3f})*{u}*{u}*(3-2*{u}))"
        # a change that starts before the previous one has ended simply takes over
        expr = f"if(lt(T,{t:.3f}),{a:.3f},if(lt(T,{t + ZOOM_TIME:.3f}),{move},{expr}))"
    return expr.replace("T", f"(on/{FPS})")


def srt_time(t):
    ms = int(round(t * 1000))
    return f"{ms // 3600000:02}:{ms // 60000 % 60:02}:{ms // 1000 % 60:02},{ms % 1000:03}"


def subtitles(path, spoken):
    """spoken: [(start, duration, text)] - sentences share a scene's time by their length."""
    out, n = [], 0
    for start, duration, text in spoken:
        parts = [p for p in re.split(r"(?<=[.!?])\s+", text.strip()) if p]
        total = sum(len(p) for p in parts)
        t = start
        for part in parts:
            share = duration * len(part) / total
            n += 1
            out.append(f"{n}\n{srt_time(t)} --> {srt_time(t + share - 0.05)}\n{part}\n")
            t += share
    path.write_text("\n".join(out), encoding="utf-8")


def screen_runs(board):
    """The storyboard's recorded scenes in runs that follow each other without a card or slide between."""
    runs, run_ = [], []
    for scene in board["scenes"]:
        if "card" in scene or "slide" in scene:
            if run_:
                runs.append(run_)
            run_ = []
        else:
            run_.append(scene)
    return runs + ([run_] if run_ else [])


def ai_note(work):
    voice = json.loads((work / "timing.json").read_text()).get("voice", "a local voice model")
    theme = load_theme()
    return (AI_NOTE if theme and theme.get("reviewed") else AI_NOTE_UNREVIEWED).format(voice=voice)


def compose(board, lang, work, out):
    """Cuts the film from segments in the storyboard's order: the title card, slides, runs of the
    screen recording (between slides), the closing card. A storyboard without slides has one run."""
    timing = json.loads((work / "timing.json").read_text())["scenes"]
    runs = screen_runs(board)
    events = json.loads((work / "events.json").read_text())["events"] if runs else []
    starts = {e["id"]: e["t"] for e in events if e["type"] == "scene-start"}
    recorded = [s["id"] for r in runs for s in r]
    if runs and sorted(recorded, key=lambda i: starts.get(i, -1)) != recorded:
        sys.exit("the scenario plays the recorded scenes in another order than the storyboard: "
                 + ", ".join(sorted(recorded, key=lambda i: starts.get(i, -1))))
    finish = {e["id"]: e["t"] for e in events if e["type"] == "scene-end"}
    cards = [s for s in board["scenes"] if "card" in s]
    intro = next(s for s in cards if s["card"] == "title")
    outro = next(s for s in cards if s["card"] == "outro")
    intro_len = LEAD + timing[intro["id"]]["duration"] + INTRO_TAIL
    outro_len = LEAD + timing[outro["id"]]["duration"] + OUTRO_TAIL

    texts = CARD_TEXT[lang]
    link = board.get("link") or DOCS_URL + board.get("docs", "")
    theme = film_theme(load_theme(), board)
    if theme:
        spec = series_of(board)
        series_title = spec["title"].get(lang) if spec else None
        brand_intro(work / "intro.png", theme, lang, board, series_title)
        brand_outro(work / "outro.png", theme, lang, board, series_title)
        outro_len = max(outro_len, theme.get("outro_min", 0))   # time to read the closing card
    else:
        card(work / "intro.png", board.get("kicker", {}).get(lang, texts["kicker"]), board["title"][lang], texts["ai"])
        card(work / "outro.png", texts["more"], link, texts["ai"])
    if any("slide" in s for s in board["scenes"]) and not theme:
        sys.exit("slides need the set's theme.json")

    inputs = ["-loop", "1", "-framerate", FPS, "-t", f"{intro_len:.3f}", "-i", work / "intro.png",
              "-loop", "1", "-framerate", FPS, "-t", f"{outro_len:.3f}", "-i", work / "outro.png"]
    n = 2
    graph = []

    def faded(src, length, name):
        return (f"[{src}]fps={FPS},fade=t=in:st=0:d=0.4,fade=t=out:st={length - 0.4:.3f}:d=0.4,"
                f"format=yuv420p,setsar=1[{name}]")

    # the screen recording, processed as one: zoom, click rings and key caps (times from t0)
    run_parts = {}
    if runs:
        # only the scenes the storyboard still has: a recorded scene that was dropped from it is cut away
        t0 = max(0.0, min(starts[i] for i in recorded) - 0.4)
        screen_len = max(finish[i] for i in recorded) - t0
        inputs += ["-i", work / "screen.mkv"]
        graph.append(f"[{n}:v]trim=start={t0:.3f}:end={t0 + screen_len:.3f},setpts=PTS-STARTPTS,fps={FPS}[s0]")
        n += 1
        last = "s0"
        ring = click_ring(work / "ring.png")
        for i, e in enumerate(e for e in events if e["type"] == "click"):
            inputs += ["-loop", "1", "-framerate", FPS, "-t", f"{screen_len:.3f}", "-i", work / "ring.png"]
            t = e["t"] - t0
            graph.append(f"[{last}][{n}:v]overlay=x={e['x'] - ring / 2:.0f}:y={e['y'] - ring / 2:.0f}"
                         f":enable='between(t,{t - 0.1:.3f},{t + 0.45:.3f})'[c{i}]")
            last, n = f"c{i}", n + 1
        states = zoom_states(events, t0)
        if len(states) > 1:
            # zoompan places its window on whole pixels: working at twice the size keeps the glide smooth
            z, cx, cy = eased(states, 1), eased(states, 2, 2), eased(states, 3, 2)
            graph.append(f"[{last}]scale={2 * W}:{2 * H}:flags=bicubic,zoompan=z='{z}'"
                         f":x='max(0,min(iw-iw/zoom,{cx}-iw/zoom/2))':y='max(0,min(ih-ih/zoom,{cy}-ih/zoom/2))'"
                         f":d=1:s={W}x{H}:fps={FPS}[zoomed]")
            last = "zoomed"
        for i, e in enumerate(e for e in events if e["type"] == "key"):
            cap = work / f"key{i}.png"
            key_cap(cap, e["label"])
            inputs += ["-loop", "1", "-framerate", FPS, "-t", f"{screen_len:.3f}", "-i", cap]
            t = e["t"] - t0
            graph.append(f"[{last}][{n}:v]overlay=x=(W-w)/2:y=H-h-150"
                         f":enable='between(t,{t - 0.2:.3f},{t + 1.6:.3f})'[k{i}]")
            last, n = f"k{i}", n + 1
        # one part per run; with a single run that is the whole screen part, as before slides existed
        bounds = []
        for i, r in enumerate(runs):
            begin = t0 if i == 0 else starts[r[0]["id"]] - 0.4
            end = t0 + screen_len if i == len(runs) - 1 else finish[r[-1]["id"]]
            bounds.append((begin, end))
        if len(runs) == 1:
            graph.append(faded(last, screen_len, "run0"))
        else:
            graph.append(f"[{last}]split={len(runs)}" + "".join(f"[r{i}]" for i in range(len(runs))))
            for i, (begin, end) in enumerate(bounds):
                graph.append(f"[r{i}]trim=start={begin - t0:.3f}:end={end - t0:.3f},setpts=PTS-STARTPTS[rt{i}]")
                graph.append(faded(f"rt{i}", end - begin, f"run{i}"))
        for i, r in enumerate(runs):
            run_parts[r[0]["id"]] = (i, *bounds[i])

    # the segments in order, and every scene's place on the final time line
    segments, at, run_no = [], 0.0, None
    graph.append(faded("0:v", intro_len, "intro"))
    for scene in board["scenes"]:
        sid = scene["id"]
        if scene is intro:
            segments.append("intro")
            scene["_at"] = LEAD
            at += intro_len
        elif scene is outro:
            segments.append("outro")
            scene["_at"] = at + LEAD
            at += outro_len
        elif "slide" in scene:
            length = LEAD + timing[sid]["duration"] + SLIDE_TAIL
            label = f"slide_{len(segments)}"
            base = None
            for k, (fraction, shown) in enumerate(slide_states(scene["slide"], lang)):
                png = work / f"{sid}-{k}.png"
                draw_slide(png, theme, lang, scene["slide"], shown)
                inputs += ["-loop", "1", "-framerate", FPS, "-t", f"{length:.3f}", "-i", png]
                if base is None:
                    base = f"{n}:v"
                else:
                    when = 0 if fraction == 0 else LEAD + fraction * timing[sid]["duration"]
                    graph.append(f"[{n}:v]format=rgba,fade=t=in:st={when:.3f}:d={BUILD_FADE}:alpha=1[{label}f{k}]")
                    graph.append(f"[{base}][{label}f{k}]overlay=shortest=1[{label}o{k}]")
                    base = f"{label}o{k}"
                n += 1
            graph.append(f"[{base}]fps={FPS},format=yuv420p,setsar=1[{label}]")
            segments.append(label)
            scene["_at"] = at + LEAD
            at += length
        else:
            if sid in run_parts:
                run_no, begin, end = run_parts[sid]
                segments.append(f"run{run_no}")
                run_at, run_begin = at, begin
                at += end - begin
            scene["_at"] = run_at + starts[sid] - run_begin
    graph.append(faded("1:v", outro_len, "outro"))
    total = at
    graph.append("".join(f"[{s}]" for s in segments) + f"concat=n={len(segments)}:v=1:a=0[v]")

    # narration: every scene's file at its place on the final time line
    spoken, mix = [], []
    for scene in board["scenes"]:
        sid = scene["id"]
        spoken.append((scene["_at"], timing[sid]["duration"], scene["say"][lang]))
        inputs += ["-i", timing[sid]["wav"]]
        graph.append(f"[{n}:a]adelay={scene['_at'] * 1000:.0f}:all=1[a{n}]")
        mix.append(f"[a{n}]")
        n += 1
    graph.append(f"{''.join(mix)}amix=inputs={len(mix)}:normalize=0:duration=longest,"
                 f"aresample=48000,apad=whole_dur={total:.3f}[a]")

    srt = out.with_suffix(".srt")
    subtitles(srt, spoken)
    (work / "filter.txt").write_text(";\n".join(graph))
    out.parent.mkdir(parents=True, exist_ok=True)
    run(["ffmpeg", "-y", "-loglevel", "warning", *inputs, "-i", srt,
         "-/filter_complex", work / "filter.txt", "-map", "[v]", "-map", "[a]", "-map", f"{n}:s",
         "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-r", FPS,
         "-c:a", "aac", "-b:a", "160k", "-ac", "2", "-c:s", "mov_text",
         "-metadata:s:s:0", f"language={SUBTITLE_LANG.get(lang, lang)}",
         "-metadata:s:a:0", f"language={SUBTITLE_LANG.get(lang, lang)}",
         *film_metadata(board, lang, theme, work),
         "-t", f"{total:.3f}", "-movflags", "+faststart", out])

    # title, description and chapters for the upload (a chapter needs 10 s, shorter ones join the one before)
    lines = [board["title"][lang], "", board["description"][lang], "", "0:00 Intro"]
    previous = 0.0
    for scene in board["scenes"]:
        if "chapter" in scene and scene["_at"] - previous >= 10:
            previous = scene["_at"]
            lines.append(f"{int(previous) // 60}:{int(previous) % 60:02} {scene['chapter'][lang]}")
    if theme:
        lines += ["", *[f"{label}: https://{url}" for label, url in theme["text"][lang]["links"]],
                  "", theme["text"][lang]["disclaimer"]]
    else:
        lines += ["", f"https://{link}", "", texts["ai"]]
    out.with_suffix(".txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {out} ({total:.0f} s), {srt.name}, {out.with_suffix('.txt').name}")
    publish(out, board["id"])


def chapter_names(board, lang):
    return [s["chapter"][lang] for s in board["scenes"] if "chapter" in s]


def shorten(text, limit):
    """text cut at a word boundary to at most limit characters, with an ellipsis when cut."""
    if len(text) <= limit:
        return text
    return text[:limit - 1].rsplit(" ", 1)[0].rstrip(",;:") + "…"


def film_metadata(board, lang, theme, work):
    """ffmpeg -metadata arguments of a film: title, series as album and show, episode, audience as
    grouping, description and synopsis (with the topics, its chapter names), genre, year, licence, and
    the AI note as comment. Values from the theme's "metadata" and "audiences"; without a theme only
    title and comment."""
    tags = {"title": board["title"][lang], "comment": ai_note(work)}
    meta = (theme or {}).get("metadata", {})
    if meta:
        spec = series_of(board)
        n, total, _ = place_in_series(board)
        audience = (theme or {}).get("_audience")
        description = board.get("description", {}).get(lang, "")
        topics = ", ".join(chapter_names(board, lang))
        tags.update({
            "artist": meta["artist"], "album_artist": meta["artist"], "composer": meta.get("composer", meta["artist"]),
            "genre": meta["genre"], "date": meta["year"], "copyright": meta["copyright"],
            "description": shorten(description, 250),
            "synopsis": description + (f" Topics: {topics}." if topics else "")
                        + (f" Audience: {audience['description'][lang]}." if audience else "")
                        + f" {meta['url']}",
        })
        if spec:
            tags.update({"album": spec["title"][lang], "show": spec["title"][lang]})
        if n:
            tags.update({"track": f"{n}/{total}", "episode_sort": str(n), "episode_id": f"{n} of {total}"})
        if audience:
            tags["grouping"] = audience["label"][lang]
    out = []
    for key, value in tags.items():
        out += ["-metadata", f"{key}={value}"]
    return out


def film_number(film_id):
    """The film's place across the set's series, in the order of their files (series-business.json
    before series-developers.json): 1 for the first film of the first series, and so on; None for a
    film in no series."""
    n = 0
    for spec in series_specs():
        if film_id in spec["videos"]:
            return n + spec["videos"].index(film_id) + 1
        n += len(spec["videos"])
    return None


def publish(out, film_id=None):
    """Copies the film, its subtitles and its upload text to the folder the films are collected in:
    FXT_VIDEO_COPY_TO, else a folder FXT-Videos next to the repository when there is one. In a set,
    the copies start with the film's number across the series ("02-biz-content-en.mp4"), so a file
    list shows them in the order they are meant to be watched."""
    target = copy_target()
    if target is None:
        return
    target.mkdir(parents=True, exist_ok=True)
    for suffix in (".mp4", ".srt", ".txt"):
        source = out.with_suffix(suffix)
        copy_file(source, target, copy_name(source, film_id))
    print(f"copied to {target}")


def copy_name(source, film_id):
    """The name a film's file gets in the collection folder: in a set, with its number in front."""
    number = film_number(film_id) if SET and film_id else None
    return f"{number:02}-{source.name}" if number else source.name


def copy_file(source, target, name=None):
    """Copies a file into the collection folder as `name`; one larger than SPLIT_MB (a WebDAV share
    takes no more) goes there as a split zip archive (stored, not compressed) in the standard format
    of Info-ZIP: <name>.zip with <name>.z01, .z02, … of SPLIT_MB each. 7-Zip, WinRAR and unzip open
    it from the .zip file, with all parts in the same folder."""
    name = name or source.name
    for stale in [target / name, target / (name + ".zip"), *target.glob(name + ".z[0-9][0-9]"),
                  *target.glob(name + ".zip.[0-9][0-9][0-9]")]:
        stale.unlink(missing_ok=True)
    limit = SPLIT_MB * 1024 * 1024
    if source.stat().st_size <= limit:
        shutil.copy2(source, target / name)
        return
    staging = ROOT / "build" / "videos" / "split"
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    linked = staging / name
    os.link(source, linked) if source.stat().st_dev == staging.stat().st_dev else shutil.copy2(source, linked)
    run(["zip", "-q", "-0", "-s", f"{SPLIT_MB}m", "-j", staging / (name + ".zip"), linked])
    linked.unlink()
    parts = sorted(staging.iterdir())
    for part in parts:
        shutil.move(str(part), target / part.name)
    shutil.rmtree(staging, ignore_errors=True)
    print(f"  {name} is larger than {SPLIT_MB} MB: copied as a split zip of {len(parts)} parts")


def copy_target():
    """The folder the films are collected in (a set's films in a sub-folder), or None."""
    target = os.environ.get("FXT_VIDEO_COPY_TO")
    target = Path(target) if target else ROOT.parent / "FXT-Videos"
    if not target.is_dir() and not os.environ.get("FXT_VIDEO_COPY_TO"):
        return None
    return target / SET if SET else target


def ffmeta(value):
    """A value for an FFMETADATA file: = ; # \\ and line breaks escaped."""
    return re.sub(r"([=;#\\\n])", r"\\\1", value)


def seconds_of(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         check=True, capture_output=True, text=True).stdout
    return float(out.strip())


def series(lang, spec):
    """Joins the finished films of a series file, in its order, into one film with a chapter per
    video (build/videos/<series id>-<lang>.mp4, .srt, .txt) and writes an .m3u playlist of the single
    films. The films share their encoding, so the picture is joined without encoding it again."""
    films, boards = [], []
    for vid in spec["videos"]:
        film = ROOT / "build" / "videos" / f"{vid}-{lang}.mp4"
        if not film.exists():
            print(f"missing {film.name} - left out (run make-video.py {vid} --lang {lang}"
                  + (f" --set {SET})" if SET else ")"))
            continue
        films.append(film)
        boards.append(json.loads((BOARDS / f"{vid}.json").read_text(encoding="utf-8")))
    if not films:
        sys.exit("no film to join")
    out = ROOT / "build" / "videos" / f"{spec['id']}-{lang}.mp4"
    work = ROOT / "build" / "videos" / spec["id"] / lang
    work.mkdir(parents=True, exist_ok=True)
    (work / "list.txt").write_text("".join(f"file '{f}'\n" for f in films))
    meta = [";FFMETADATA1", f"title={spec['title'][lang]}", f"comment={ai_note(films[0].parent / boards[0]['id'] / lang)}"]
    theme = load_theme()
    if theme and theme.get("metadata"):
        m = theme["metadata"]
        audiences = sorted({(theme.get("audiences", {}).get(b.get("audience")) or {}).get("label", {}).get(lang, "")
                            for b in boards} - {""})
        meta += [f"artist={m['artist']}", f"album_artist={m['artist']}", f"album={spec['title'][lang]}",
                 f"show={spec['title'][lang]}", f"genre={m['genre']}", f"date={m['year']}", f"copyright={m['copyright']}",
                 "description=" + ffmeta(shorten(f"{spec['title'][lang]}: {len(boards)} films. " + "; ".join(b["title"][lang] for b in boards), 250)),
                 "synopsis=" + ffmeta(f"{spec['title'][lang]}, all {len(boards)} films in one, with a chapter per film: "
                                      + "; ".join(b["title"][lang] for b in boards) + "."
                                      + (f" Audience: {', '.join(audiences)}." if audiences else "") + f" {m['url']}")]
        if audiences:
            meta.append("grouping=" + ffmeta(", ".join(audiences)))
    chapters, srt_parts, start = [], [], 0.0
    for film, board in zip(films, boards):
        length = seconds_of(film)
        meta += ["[CHAPTER]", "TIMEBASE=1/1000", f"START={int(start * 1000)}", f"END={int((start + length) * 1000)}",
                 f"title={board['title'][lang]}"]
        chapters.append(f"{int(start) // 3600}:{int(start) % 3600 // 60:02}:{int(start) % 60:02} {board['title'][lang]}")
        srt = film.with_suffix(".srt")
        if srt.exists():
            srt_parts.append((start, srt.read_text(encoding="utf-8")))
        start += length
    (work / "chapters.txt").write_text("\n".join(meta) + "\n", encoding="utf-8")
    run(["ffmpeg", "-y", "-loglevel", "warning", "-f", "concat", "-safe", "0", "-i", work / "list.txt",
         "-i", work / "chapters.txt", "-map", "0", "-map_metadata", "1", "-map_chapters", "1", "-c:v", "copy", "-c:s", "copy",
         "-c:a", "aac", "-b:a", "160k",  # each film's audio starts with its own priming: joined anew
         "-movflags", "+faststart", out])
    # one subtitle file for the whole film: every film's cues moved by its start
    cues, number = [], 1
    stamp = re.compile(r"(\d+):(\d+):(\d+),(\d+) --> (\d+):(\d+):(\d+),(\d+)")
    for offset, text in srt_parts:
        for block in text.strip().split("\n\n"):
            lines = block.split("\n")
            m = stamp.match(lines[1]) if len(lines) > 2 else None
            if not m:
                continue
            g = [int(x) for x in m.groups()]
            a = g[0] * 3600 + g[1] * 60 + g[2] + g[3] / 1000 + offset
            b = g[4] * 3600 + g[5] * 60 + g[6] + g[7] / 1000 + offset
            cues.append(f"{number}\n{srt_time(a)} --> {srt_time(b)}\n" + "\n".join(lines[2:]))
            number += 1
    out.with_suffix(".srt").write_text("\n\n".join(cues) + "\n", encoding="utf-8")
    out.with_suffix(".txt").write_text("\n".join([spec["title"][lang], "", *chapters]) + "\n", encoding="utf-8")
    playlist = ROOT / "build" / "videos" / f"{spec['id']}-{lang}.m3u"
    playlist.write_text("#EXTM3U\n" + "".join(
        f"#EXTINF:{int(seconds_of(f))},{b['title'][lang]}\n{copy_name(f, b['id'])}\n" for f, b in zip(films, boards)),
        encoding="utf-8")
    print(f"wrote {out} ({start / 60:.1f} min, {len(films)} films), {playlist.name}")
    publish(out)
    target = copy_target()
    if target is not None:
        copy_file(playlist, target)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("storyboard", nargs="?", help="id of videos/<id>.json")
    ap.add_argument("--lang", default="en", choices=sorted(VOICES))
    ap.add_argument("--steps", default="tts,record,compose", help="comma list of tts, record, compose")
    ap.add_argument("--series", nargs="?", const="*", metavar="ID",
                    help="join the films of a series into one (its id; without one: every series of the set)")
    ap.add_argument("--set", help="a series of its own in videos/<set>/ (e.g. fundsxml)")
    args = ap.parse_args()
    global BOARDS, SET
    if args.set:
        SET, BOARDS = args.set, ROOT / "videos" / args.set
        if not BOARDS.is_dir():
            ap.error(f"no folder {BOARDS}")
    if args.series:
        specs = series_specs() if args.series == "*" else [spec_by_id(args.series)]
        if not specs or None in specs:
            ap.error(f"no series {args.series} in {BOARDS}")
        for spec in specs:
            series(args.lang, spec)
        return
    if not args.storyboard:
        ap.error("a storyboard id or --series is needed")
    board = json.loads((BOARDS / f"{args.storyboard}.json").read_text(encoding="utf-8"))
    work = ROOT / "build" / "videos" / board["id"] / args.lang
    steps = args.steps.split(",")
    if "tts" in steps:
        tts(board, args.lang, work)
    if "record" in steps and screen_runs(board):
        record(board, args.lang, work)
    if "compose" in steps:
        compose(board, args.lang, work, ROOT / "build" / "videos" / f"{board['id']}-{args.lang}.mp4")


if __name__ == "__main__":
    main()
