#!/usr/bin/env python3
"""Builds a tutorial video from a storyboard (videos/<id>.json): speech, screen recording and cut,
all generated - nobody records, speaks or edits.

    python3 scripts/make-video.py validate-xml --lang de          # all three steps
    python3 scripts/make-video.py validate-xml --lang en --steps compose
    python3 scripts/make-video.py --series --lang de                # all films of videos/series.json in one
    python3 scripts/make-video.py fundsxml-validate --set fundsxml  # a storyboard of videos/fundsxml/

A set is a series of its own in a sub-folder of videos/ (storyboards, series.json, an optional
pronunciation.json that adds to the shared one); its films are copied to a sub-folder of the same
name. A storyboard may name its own card kicker ("kicker") and closing link ("link") instead of the
FreeXmlToolkit documentation.

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
           over 19 MiB ($FXT_VIDEO_SPLIT_MB; below a WebDAV limit of 20 MB) goes there as a split zip of 19 MiB parts.

Needs ffmpeg, xvfb-run and Pillow. Piper and the voices are installed on first use into
~/.cache/fxt-video (venv + voices, about 115 MB per voice). The words in videos/pronunciation.json
are respelled for the speech only."""
import argparse
import hashlib
import json
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
SPLIT_MB = int(os.environ.get("FXT_VIDEO_SPLIT_MB", "19"))   # larger files go as split zips; 19 MiB < 20,000,000 bytes

LEAD = 0.5          # silence before the narration on a card
INTRO_TAIL = 0.7
OUTRO_TAIL = 2.0
ZOOM_TIME = 0.7     # seconds a zoom change takes
ZOOM_MAX = 2.0
ZOOM_PAD = 90       # air around a zoomed region, px
CARD_TEXT = {
    "en": {"kicker": "FreeXmlToolkit · Tutorial", "more": "Read on:",
           "ai": "Narration and editing are AI-generated. The video shows the real application."},
    "de": {"kicker": "FreeXmlToolkit · Lernvideo", "more": "Weiterlesen:",
           "ai": "Sprecherstimme und Schnitt sind KI-generiert. Das Video zeigt die echte Anwendung."},
}


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


def place_in_series(board):
    """(episode number, number of episodes, the next storyboard or None) - from the set's series.json."""
    path = BOARDS / "series.json"
    if not path.exists():
        return None, None, None
    ids = json.loads(path.read_text(encoding="utf-8"))["videos"]
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

    def canvas(self):
        """White page with the brand's blue bar on the left and the logo at the top left (as the
        presentation template's title slide)."""
        img = Image.new("RGB", (W, H), self.c["background"])
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 27, H], fill=self.c["bar"])
        logo = Image.open(BOARDS / self.theme["logo"]).convert("RGBA")
        height = 58
        logo = logo.resize((round(logo.width * height / logo.height), height), Image.LANCZOS)
        img.paste(logo, (160, 120), logo)
        return img, d

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
    img, d = b.canvas()
    n, total, _ = place_in_series(board)
    kicker = t["kicker"].format(n=n, total=total) if n else t["series_kicker"]
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
    img.save(path)


def series_titles(lang):
    """[(storyboard id, title)] of the set's series.json, planned films by their title in "planned"."""
    path = BOARDS / "series.json"
    if not path.exists():
        return []
    spec = json.loads(path.read_text(encoding="utf-8"))
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
    # left column: the series, this film marked, the next one named
    episodes = series_titles(lang)
    if episodes:
        d.text((160, 395), t["series"] + (f": {series_title}" if series_title else ""), font=b.font(34, True),
               fill=b.c["accent"])
        ids = [vid for vid, _ in episodes]
        here = ids.index(board["id"]) if board["id"] in ids else -1
        y = 455
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
            y += 56
    # right column: where to read on
    x = 1040
    d.text((x, 395), t["more"], font=b.font(34, True), fill=b.c["accent"])
    y = 455
    for label, url in t["links"]:
        d.text((x, y), label, font=b.font(28), fill=b.c["text"])
        d.text((x, y + 34), url, font=b.font(36, True), fill=b.c["heading"])
        y += 92
    # the disclaimer in a card at the bottom
    fnt = b.font(25)
    left, right, bottom = 160, W - 160, H - 50
    lines = wrap(d, t["disclaimer"], fnt, right - left - 64)
    top = bottom - 40 - 35 * len(lines)
    d.rounded_rectangle([left, top, right, bottom], radius=12, fill=b.c["panel"], outline=b.c["border"], width=2)
    y = top + 20
    for line in lines:
        d.text((left + 32, y), line, font=fnt, fill=b.c["heading"])
        y += 35
    img.save(path)


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


def compose(board, lang, work, out):
    timing = json.loads((work / "timing.json").read_text())["scenes"]
    events = json.loads((work / "events.json").read_text())["events"]
    starts = {e["id"]: e["t"] for e in events if e["type"] == "scene-start"}
    ends = [e["t"] for e in events if e["type"] == "scene-end"]
    cards = [s for s in board["scenes"] if "card" in s]
    intro = next(s for s in cards if s["card"] == "title")
    outro = next(s for s in cards if s["card"] == "outro")
    t0 = max(0.0, min(starts.values()) - 0.4)
    screen_len = max(ends) - t0
    intro_len = LEAD + timing[intro["id"]]["duration"] + INTRO_TAIL
    outro_len = LEAD + timing[outro["id"]]["duration"] + OUTRO_TAIL

    texts = CARD_TEXT[lang]
    link = board.get("link") or DOCS_URL + board.get("docs", "")
    theme = load_theme()
    if theme:
        series_path = BOARDS / "series.json"
        series_title = json.loads(series_path.read_text(encoding="utf-8"))["title"].get(lang) if series_path.exists() else None
        brand_intro(work / "intro.png", theme, lang, board, series_title)
        brand_outro(work / "outro.png", theme, lang, board, series_title)
        outro_len = max(outro_len, theme.get("outro_min", 0))   # time to read the closing card
    else:
        card(work / "intro.png", board.get("kicker", {}).get(lang, texts["kicker"]), board["title"][lang], texts["ai"])
        card(work / "outro.png", texts["more"], link, texts["ai"])
    ring = click_ring(work / "ring.png")

    inputs = ["-i", work / "screen.mkv",
              "-loop", "1", "-framerate", FPS, "-t", f"{intro_len:.3f}", "-i", work / "intro.png",
              "-loop", "1", "-framerate", FPS, "-t", f"{outro_len:.3f}", "-i", work / "outro.png"]
    n = 3
    graph = [f"[0:v]trim=start={t0:.3f}:end={t0 + screen_len:.3f},setpts=PTS-STARTPTS,fps={FPS}[s0]"]
    last = "s0"
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

    def faded(src, length, name):
        return (f"[{src}]fps={FPS},fade=t=in:st=0:d=0.4,fade=t=out:st={length - 0.4:.3f}:d=0.4,"
                f"format=yuv420p,setsar=1[{name}]")
    graph += [faded(last, screen_len, "screen"), faded("1:v", intro_len, "intro"), faded("2:v", outro_len, "outro"),
              "[intro][screen][outro]concat=n=3:v=1:a=0[v]"]

    # narration: every scene's file at its place on the final time line
    spoken, mix = [], []
    for scene in board["scenes"]:
        sid = scene["id"]
        if scene is intro:
            at = LEAD
        elif scene is outro:
            at = intro_len + screen_len + LEAD
        else:
            at = intro_len + starts[sid] - t0
        scene["_at"] = at
        spoken.append((at, timing[sid]["duration"], scene["say"][lang]))
        inputs += ["-i", timing[sid]["wav"]]
        graph.append(f"[{n}:a]adelay={at * 1000:.0f}:all=1[a{n}]")
        mix.append(f"[a{n}]")
        n += 1
    total = intro_len + screen_len + outro_len
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
         "-metadata", f"title={board['title'][lang]}", "-t", f"{total:.3f}", "-movflags", "+faststart", out])

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
    publish(out)


def publish(out):
    """Copies the film, its subtitles and its upload text to the folder the films are collected in:
    FXT_VIDEO_COPY_TO, else a folder FXT-Videos next to the repository when there is one."""
    target = copy_target()
    if target is None:
        return
    target.mkdir(parents=True, exist_ok=True)
    for suffix in (".mp4", ".srt", ".txt"):
        copy_file(out.with_suffix(suffix), target)
    print(f"copied to {target}")


def copy_file(source, target):
    """Copies a file into the collection folder; one larger than SPLIT_MB (a WebDAV share takes no
    more) goes there as a zip archive (stored, not compressed) cut into parts of SPLIT_MB:
    <name>.zip.001, .002, … - 7-Zip opens the first part directly, or join them with
    `cat <name>.zip.0* > <name>.zip` (Windows: `copy /b <name>.zip.001+<name>.zip.002 <name>.zip`)."""
    for stale in [target / source.name, *target.glob(source.name + ".zip.[0-9][0-9][0-9]")]:
        stale.unlink(missing_ok=True)
    limit = SPLIT_MB * 1024 * 1024
    if source.stat().st_size <= limit:
        shutil.copy2(source, target)
        return
    archive = ROOT / "build" / "videos" / (source.name + ".zip")
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_STORED, allowZip64=True) as z:
        z.write(source, source.name)
    with archive.open("rb") as whole:
        part = 0
        while chunk := whole.read(limit):
            part += 1
            (target / f"{source.name}.zip.{part:03}").write_bytes(chunk)
    archive.unlink()
    print(f"  {source.name} is larger than {SPLIT_MB} MB: copied as {part} zip parts")


def copy_target():
    """The folder the films are collected in (a set's films in a sub-folder), or None."""
    target = os.environ.get("FXT_VIDEO_COPY_TO")
    target = Path(target) if target else ROOT.parent / "FXT-Videos"
    if not target.is_dir() and not os.environ.get("FXT_VIDEO_COPY_TO"):
        return None
    return target / SET if SET else target


def seconds_of(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         check=True, capture_output=True, text=True).stdout
    return float(out.strip())


def series(lang):
    """Joins the finished films of videos/series.json, in its order, into one film with a chapter per
    video (build/videos/all-videos-<lang>.mp4, .srt, .txt) and writes an .m3u playlist of the single
    films. The films share their encoding, so the picture is joined without encoding it again."""
    spec = json.loads((BOARDS / "series.json").read_text(encoding="utf-8"))
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
    meta = [";FFMETADATA1", f"title={spec['title'][lang]}"]
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
        f"#EXTINF:{int(seconds_of(f))},{b['title'][lang]}\n{f.name}\n" for f, b in zip(films, boards)), encoding="utf-8")
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
    ap.add_argument("--series", action="store_true", help="join the films of videos/series.json into one")
    ap.add_argument("--set", help="a series of its own in videos/<set>/ (e.g. fundsxml)")
    args = ap.parse_args()
    global BOARDS, SET
    if args.set:
        SET, BOARDS = args.set, ROOT / "videos" / args.set
        if not BOARDS.is_dir():
            ap.error(f"no folder {BOARDS}")
    if args.series:
        series(args.lang)
        return
    if not args.storyboard:
        ap.error("a storyboard id or --series is needed")
    board = json.loads((BOARDS / f"{args.storyboard}.json").read_text(encoding="utf-8"))
    work = ROOT / "build" / "videos" / board["id"] / args.lang
    steps = args.steps.split(",")
    if "tts" in steps:
        tts(board, args.lang, work)
    if "record" in steps:
        record(board, args.lang, work)
    if "compose" in steps:
        compose(board, args.lang, work, ROOT / "build" / "videos" / f"{board['id']}-{args.lang}.mp4")


if __name__ == "__main__":
    main()
