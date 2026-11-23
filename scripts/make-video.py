#!/usr/bin/env python3
"""Builds a tutorial video from a storyboard (videos/<id>.json): speech, screen recording and cut,
all generated - nobody records, speaks or edits.

    python3 scripts/make-video.py validate-xml --lang de          # all three steps
    python3 scripts/make-video.py validate-xml --lang en --steps compose

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
           $FXT_VIDEO_COPY_TO, or to a folder FXT-Videos next to the repository if it exists.

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
    card(work / "intro.png", texts["kicker"], board["title"][lang], texts["ai"])
    card(work / "outro.png", texts["more"], DOCS_URL + board.get("docs", ""), texts["ai"])
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
    lines += ["", f"https://{DOCS_URL}{board.get('docs', '')}", "", texts["ai"]]
    out.with_suffix(".txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {out} ({total:.0f} s), {srt.name}, {out.with_suffix('.txt').name}")
    publish(out)


def publish(out):
    """Copies the film, its subtitles and its upload text to the folder the films are collected in:
    FXT_VIDEO_COPY_TO, else a folder FXT-Videos next to the repository when there is one."""
    target = os.environ.get("FXT_VIDEO_COPY_TO")
    target = Path(target) if target else ROOT.parent / "FXT-Videos"
    if not target.is_dir() and not os.environ.get("FXT_VIDEO_COPY_TO"):
        return
    target.mkdir(parents=True, exist_ok=True)
    for suffix in (".mp4", ".srt", ".txt"):
        shutil.copy2(out.with_suffix(suffix), target)
    print(f"copied to {target}")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("storyboard", help="id of videos/<id>.json")
    ap.add_argument("--lang", default="en", choices=sorted(VOICES))
    ap.add_argument("--steps", default="tts,record,compose", help="comma list of tts, record, compose")
    args = ap.parse_args()
    board = json.loads((ROOT / "videos" / f"{args.storyboard}.json").read_text(encoding="utf-8"))
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
