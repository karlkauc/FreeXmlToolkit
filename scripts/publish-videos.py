#!/usr/bin/env python3
"""Prepare the films of a video set for the web: a folder for a GitHub Pages repository.

    python3 scripts/publish-videos.py --set fundsxml --lang en --out ../fundsxml-videos

Takes the finished films from build/videos (made by make-video.py) and writes, for every series of the
set (series*.json) in watching order:

    <lang>/<NN>-<id>.mp4            the film, remuxed with +faststart (plays while it loads)
    <lang>/<NN>-<id>.vtt            subtitles (WebVTT, from the .srt)
    <lang>/<NN>-<id>-chapters.vtt   chapters (from the chapter list of the .txt)
    posters/<NN>-<id>.jpg           a frame of the title card
    videos.json                     the manifest: series, films, titles, descriptions, durations, chapters
    index.html                      a standalone player page (videos/web/player.html)
    wp-widget.html                  the same player as one block for a WordPress page (absolute URLs)

The full series films (fundsxml-business-en.mp4, ...) are too large for a Pages repository; they are
listed in videos.json as assets of a GitHub release (--release, default v1) and copied to <out>/release/
for the upload (gh release upload). Needs ffmpeg and ffprobe.
"""
import argparse
import html
import importlib.util
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build" / "videos"
TEMPLATE = ROOT / "videos" / "web" / "player.html"


def make_video():
    """make-video.py as a module (its series helpers)."""
    spec = importlib.util.spec_from_file_location("make_video", ROOT / "scripts" / "make-video.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def duration(path):
    out = subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                   "-of", "default=nw=1:nk=1", str(path)], text=True)
    return float(out.strip())


def vtt_time(t):
    h, rest = divmod(t, 3600)
    m, s = divmod(rest, 60)
    return f"{int(h):02d}:{int(m):02d}:{s:06.3f}"


def srt_to_vtt(srt, vtt):
    text = srt.read_text(encoding="utf-8").replace("\r\n", "\n")
    text = re.sub(r"(\d\d:\d\d:\d\d),(\d\d\d)", r"\1.\2", text)
    vtt.write_text("WEBVTT\n\n" + text.strip() + "\n", encoding="utf-8")


def chapters_of(txt):
    """[(seconds, title)] from the chapter lines of the upload text (0:00 Intro, 1:02:03 ...)."""
    out = []
    for line in txt.read_text(encoding="utf-8").splitlines():
        m = re.fullmatch(r"(?:(\d+):)?(\d+):(\d\d) (.+)", line.strip())
        if m:
            h, mi, s, title = m.groups()
            out.append((int(h or 0) * 3600 + int(mi) * 60 + int(s), title))
    return out


def chapters_vtt(chapters, total, path):
    lines = ["WEBVTT", ""]
    for i, (start, title) in enumerate(chapters):
        end = chapters[i + 1][0] if i + 1 < len(chapters) else total
        lines += [str(i + 1), f"{vtt_time(start)} --> {vtt_time(end)}", title, ""]
    path.write_text("\n".join(lines), encoding="utf-8")


def faststart(source, target):
    """Remux for the web: picture and sound only, keeping the metadata (title, the AI note in comment)."""
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-map", "0:v", "-map", "0:a", "-map_metadata", "0",
                    "-c", "copy", "-movflags", "+faststart", str(target)], check=True)


def poster(source, target, at=1.5):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(at), "-i", str(source), "-frames:v", "1",
                    "-vf", "scale=640:-2", "-q:v", "4", str(target)], check=True)


def upload_text(txt):
    """The description: the paragraph after the title in the .txt."""
    parts = [p.strip() for p in txt.read_text(encoding="utf-8").split("\n\n")]
    return parts[1] if len(parts) > 1 else ""


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--set", required=True)
    ap.add_argument("--lang", default="en")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--base-url", default="https://fundsxml.github.io/videos/",
                    help="where the Pages site is served (for the WordPress block)")
    ap.add_argument("--repo", default="fundsxml/videos")
    ap.add_argument("--release", default="v1")
    ap.add_argument("--partial", action="store_true", help="skip films that are not made yet (and empty series)")
    ap.add_argument("--films", nargs="+", help="publish only these films (with --partial: an early release)")
    args = ap.parse_args()

    mv = make_video()
    mv.BOARDS = ROOT / "videos" / args.set
    theme = json.loads((mv.BOARDS / "theme.json").read_text(encoding="utf-8")) if (mv.BOARDS / "theme.json").exists() else {}
    lang = args.lang
    out = args.out
    (out / lang).mkdir(parents=True, exist_ok=True)
    (out / "posters").mkdir(exist_ok=True)
    (out / "release").mkdir(exist_ok=True)

    manifest = {"lang": lang, "series": [], "release": f"https://github.com/{args.repo}/releases/tag/{args.release}",
                "disclaimer": theme.get("text", {}).get(lang, {}).get("disclaimer", "")}
    for spec in mv.series_specs():
        series = {"id": spec["id"], "title": spec["title"][lang], "films": []}
        for film_id in spec["videos"]:
            board = json.loads((mv.BOARDS / f"{film_id}.json").read_text(encoding="utf-8"))
            mp4 = BUILD / f"{film_id}-{lang}.mp4"
            if args.films and film_id not in args.films:
                continue
            if not mp4.exists():
                if args.partial:
                    continue
                sys.exit(f"missing {mp4}: make the film first")
            name = f"{mv.film_number(film_id):02d}-{film_id}"
            faststart(mp4, out / lang / f"{name}.mp4")
            srt_to_vtt(BUILD / f"{film_id}-{lang}.srt", out / lang / f"{name}.vtt")
            total = duration(mp4)
            chapters = chapters_of(BUILD / f"{film_id}-{lang}.txt")
            chapters_vtt(chapters, total, out / lang / f"{name}-chapters.vtt")
            poster(mp4, out / "posters" / f"{name}.jpg")
            series["films"].append({
                "id": film_id, "number": mv.film_number(film_id), "title": board["title"][lang],
                "description": board.get("description", {}).get(lang) or upload_text(BUILD / f"{film_id}-{lang}.txt"),
                "duration": round(total, 1), "chapters": [{"t": t, "title": c} for t, c in chapters],
                "mp4": f"{lang}/{name}.mp4", "subtitles": f"{lang}/{name}.vtt",
                "chapters_vtt": f"{lang}/{name}-chapters.vtt", "poster": f"posters/{name}.jpg"})
            print("film", name)
        full = BUILD / f"{spec['id']}-{lang}.mp4"
        if full.exists() and not args.films:
            faststart(full, out / "release" / full.name)
            shutil.copy2(BUILD / f"{spec['id']}-{lang}.srt", out / "release" / f"{spec['id']}-{lang}.srt")
            series["full"] = {"title": f"{spec['title'][lang]} (all films)", "duration": round(duration(full), 1),
                              "url": f"https://github.com/{args.repo}/releases/download/{args.release}/{full.name}"}
            print("full film", full.name)
        if series["films"]:
            manifest["series"].append(series)

    (out / "videos.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    template = TEMPLATE.read_text(encoding="utf-8")
    widget = template.split("<!-- widget -->")[1].split("<!-- /widget -->")[0].strip()
    data = json.dumps(manifest, ensure_ascii=False).replace("</", "<\\/")
    page = template.replace("/*VIDEOS*/null", data).replace("/*BASE*/''", "''")
    (out / "index.html").write_text(page, encoding="utf-8")
    block = widget.replace("/*VIDEOS*/null", data).replace("/*BASE*/''", json.dumps(args.base_url))
    (out / "wp-widget.html").write_text(block + "\n", encoding="utf-8")
    print("wrote", out / "videos.json", out / "index.html", out / "wp-widget.html")
    return 0


if __name__ == "__main__":
    sys.exit(main())
