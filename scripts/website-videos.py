#!/usr/bin/env python3
"""Prepares the tutorial videos for freexmltoolkit.org/videos/ and writes the page that plays them.

The films are made by scripts/make-video.py into build/videos/<id>-<lang>.mp4 (+ .srt subtitles,
+ .txt upload text with the chapter list). This script

- copies every single film of the series below (only the FreeXmlToolkit tutorials) into build/website-videos-mp4/, moving the MP4 index
  to the front when it is not there yet. The films are assets of the GitHub release "media-videos"
  (the web space of freexmltoolkit.org is too small for them); the page plays them from there,
- writes into build/website-videos/ (gitignored; the server folder /videos/, next to the page)
  WebVTT subtitles (<id>-<lang>.vtt), chapters (<id>-<lang>-chapters.vtt) and a poster frame
  (<id>-<lang>.webp, 1280 px wide),
- writes the page sources website-src/en/videos/index.html and website-src/de/videos/index.html.

The joined series film (all-videos) is not published; the page plays a series in order.
A film missing in build/videos/ is left out of the page.

Publish (films and side files first: the page links to them):

    python3 scripts/website-videos.py
    gh release upload media-videos build/website-videos-mp4/*.mp4 --clobber
    python3 scripts/upload-website.py --dir /www --videos      # subtitles and posters, to /www/videos
    python3 scripts/build-website.py
    python3 scripts/upload-website.py --dir /www               # the page

The release "media-videos" is a pre-release, never "latest" (the update check and the download page read
releases/latest); the package workflow skips media-* releases. Create it once as a draft, upload, then publish:

    gh release create media-videos --draft --prerelease --latest=false --title "Tutorial videos (website media)" \
        --notes "Films played on https://freexmltoolkit.org/videos/. Not a FreeXmlToolkit release."
    gh release upload media-videos build/website-videos-mp4/*.mp4
    gh release edit media-videos --draft=false --prerelease --latest=false
"""
import datetime
import html
import json
import os
import pathlib
import re
import shutil
import struct
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BUILT = ROOT / "build" / "videos"
OUT = ROOT / "build" / "website-videos"          # subtitles, chapters, posters -> server folder videos/
RELEASE = ROOT / "build" / "website-videos-mp4"  # the films -> assets of the GitHub release RELEASE_TAG
RELEASE_TAG = "media-videos"
MP4_URL = f"https://github.com/karlkauc/FreeXmlToolkit/releases/download/{RELEASE_TAG}/"
SRC = ROOT / "website-src"
BASE_URL = "https://freexmltoolkit.org/"

# series file, folder of its storyboards
# Only the FreeXmlToolkit tutorials are published; the FundsXML series (videos/fundsxml/) stays off the website.
SERIES = [
    (ROOT / "videos" / "series.json", ROOT / "videos"),
]

TEXT = {
    "en": {
        "title": "Videos – FreeXmlToolkit",
        "description": "Short tutorial films of FreeXmlToolkit: the workspace, validation, "
                       "the XML grid, the XSD diagram, the XSLT debugger, Schematron quick fixes and projects.",
        "eyebrow": "Videos",
        "h1": "Watch it in action",
        "lead": "Short films that show the real application. Each one has subtitles and chapters; "
                "a series plays on by itself.",
        "note": "Narration voice, text and editing are AI-generated. The films show the real application.",
        "ai_badge": "AI",
        "genre": "Tutorial",
        "guide": "Guide",
        "all_videos": "All videos",
        "ai_label": "AI-generated narration voice, text and editing · real application footage",
        "jump": "Series",
        "now": "Now playing",
        "chapters": "Chapters",
        "subtitles": "English",
        "english": "",
        "no_video": "Your browser cannot play this video. Download it instead:",
        "docs": "Read the guide",
        "films": "{n} films",
    },
    "de": {
        "title": "Videos – FreeXmlToolkit",
        "description": "Kurze Lernvideos zu FreeXmlToolkit: Arbeitsbereich, Validierung, "
                       "XML-Raster, XSD-Diagramm, XSLT-Debugger, Schematron-Schnellkorrekturen und Projekte.",
        "eyebrow": "Videos",
        "h1": "In Aktion sehen",
        "lead": "Kurze Filme mit der echten Anwendung. Jeder hat Untertitel und Kapitel; "
                "eine Reihe läuft von selbst weiter.",
        "note": "Sprecherstimme, Text und Schnitt sind KI-generiert. Die Filme zeigen die echte Anwendung.",
        "ai_badge": "KI",
        "genre": "Lernvideo",
        "guide": "Anleitung",
        "all_videos": "Alle Videos",
        "ai_label": "KI-generierte Sprecherstimme, Texte und Schnitt · Bilder aus der echten Anwendung",
        "jump": "Reihen",
        "now": "Läuft gerade",
        "chapters": "Kapitel",
        "subtitles": "Deutsch",
        "english": " (englisch)",
        "no_video": "Ihr Browser kann dieses Video nicht abspielen. Laden Sie es herunter:",
        "docs": "Zur Anleitung (englisch)",
        "films": "{n} Filme",
    },
}
LANG_LABEL = {"en": "English", "de": "Deutsch"}


VOICES = {"de": "de_DE-thorsten-high", "en": "en_US-lessac-high"}   # as in make-video.py


def ai_note(lang):
    """The machine-readable AI disclosure written into each published MP4 (format tag "comment")."""
    return ("AI-generated content: the narration is a synthetic voice (Piper TTS, "
            f"{VOICES.get(lang, 'a local voice model')}), the text and the editing are AI-generated. "
            "The screen footage shows the real application.")


def faststart(path):
    """True when the MP4's moov box comes before mdat (the browser can start before the download ends)."""
    with path.open("rb") as f:
        while True:
            head = f.read(8)
            if len(head) < 8:
                return False
            size, kind = struct.unpack(">I4s", head)
            if kind == b"moov":
                return True
            if kind == b"mdat":
                return False
            if size == 1:
                size = struct.unpack(">Q", f.read(8))[0] - 8
            f.seek(size - 8, 1)


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                          str(path)], capture_output=True, text=True, check=True).stdout
    return float(out.strip())


def clock(seconds):
    seconds = int(round(seconds))
    h, rest = divmod(seconds, 3600)
    m, s = divmod(rest, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def vtt_time(seconds):
    h, rest = divmod(seconds, 3600)
    m, s = divmod(rest, 60)
    return f"{int(h):02d}:{int(m):02d}:{s:06.3f}"


def srt_to_vtt(srt):
    text = srt.replace("\r\n", "\n").strip()
    text = re.sub(r"(\d\d:\d\d:\d\d),(\d\d\d)", r"\1.\2", text)
    return "WEBVTT\n\n" + text + "\n"


def chapters(txt):
    """[(seconds, title)] from the upload text's chapter lines ('0:27 Title' / '0:03:46 Title')."""
    found = []
    for line in txt.splitlines():
        m = re.match(r"^(\d+):(\d\d)(?::(\d\d))?\s+(.+)$", line.strip())
        if m:
            a, b, c, title = m.groups()
            seconds = int(a) * 3600 + int(b) * 60 + int(c) if c else int(a) * 60 + int(b)
            found.append((seconds, title.strip()))
    return found


def chapters_vtt(marks, total):
    out = ["WEBVTT", ""]
    for i, (start, title) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else total
        out += [str(i + 1), f"{vtt_time(start)} --> {vtt_time(end)}", title, ""]
    return "\n".join(out)


def write_if_changed(path, text):
    if not path.exists() or path.read_text(encoding="utf-8") != text:
        path.write_text(text, encoding="utf-8")


ISO_639_2 = {"de": "deu", "en": "eng"}


def film_metadata(board, lang, series_title, number, total, made):
    """The MP4 tags of a published film: what a player, a media library or a search engine reads from the file."""
    title = pick(board.get("title", {}), lang)
    description = pick(board.get("description", {}), lang) or title
    docs = f"{BASE_URL}docs/{board['docs']}" if board.get("docs") else BASE_URL
    t = TEXT[lang]
    return {
        "title": title,
        "artist": "FreeXmlToolkit",
        "album_artist": "FreeXmlToolkit",
        "album": series_title,
        "track": f"{number}/{total}",
        "genre": t["genre"],
        "date": made.strftime("%Y-%m-%d"),
        "copyright": f"© {made.year} FreeXmlToolkit · freexmltoolkit.org",
        "description": description,
        "synopsis": f"{description} {t['guide']}: {docs} · {t['all_videos']}: {BASE_URL}{'de/' if lang == 'de' else ''}videos/",
        "comment": ai_note(lang),   # the machine-readable AI disclosure (EU AI Act Art. 50)
    }


def chapter_count(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_chapters", "-of", "json", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return len(json.loads(out).get("chapters", []))


def media_duration(path):
    return float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                                 str(path)], capture_output=True, text=True, check=True).stdout)


def tags(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format_tags", "-of", "json", str(path)],
                         capture_output=True, text=True, check=True).stdout
    return {k.lower(): v for k, v in json.loads(out).get("format", {}).get("tags", {}).items()}


def prepare(video_id, lang, meta=None):
    """Copies one film and writes its side files; -> dict for the page, or None when it is not built."""
    src = BUILT / f"{video_id}-{lang}.mp4"
    if not src.exists():
        return None
    name = f"{video_id}-{lang}"
    mp4 = RELEASE / f"{name}.mp4"
    meta = meta or {"comment": ai_note(lang)}
    txt = BUILT / f"{name}.txt"
    marks = chapters(txt.read_text(encoding="utf-8")) if txt.exists() else []
    stale = not mp4.exists() or mp4.stat().st_mtime < src.stat().st_mtime
    if not stale:
        have = tags(mp4)
        stale = (any(have.get(k) != v for k, v in meta.items())
                 or (bool(marks) and not chapter_count(mp4)))
    if stale:
        # remuxed (a copy of the streams): index in front, metadata for players and libraries, the AI note,
        # and the chapters (players such as VLC list them)
        code = ISO_639_2.get(lang, lang)
        cmd = ["ffmpeg", "-v", "error", "-y", "-i", str(src)]
        if marks:
            total_ms = round(media_duration(src) * 1000)
            lines = [";FFMETADATA1"]
            for i, (start, title) in enumerate(marks):
                end = round(marks[i + 1][0] * 1000) if i + 1 < len(marks) else total_ms
                lines += ["[CHAPTER]", "TIMEBASE=1/1000", f"START={round(start * 1000)}", f"END={end}",
                          "title=" + title.replace("\\", "\\\\").replace("=", "\\=").replace(";", "\\;").replace("#", "\\#")]
            chapter_file = RELEASE / f"{name}.chapters.txt"
            chapter_file.write_text("\n".join(lines) + "\n", encoding="utf-8")
            cmd += ["-i", str(chapter_file), "-map_chapters", "1"]
        cmd += ["-map", "0", "-c", "copy"]   # (-map_metadata -1 would drop the QuickTime chapter track)
        for key, value in meta.items():
            cmd += ["-metadata", f"{key}={value}"]
        cmd += ["-metadata:s:v:0", f"language={code}", "-metadata:s:a:0", f"language={code}",
                "-metadata:s:s:0", f"language={code}", "-movflags", "+faststart", str(mp4)]
        subprocess.run(cmd, check=True)
        if marks:
            chapter_file.unlink()
        os.utime(mp4, (src.stat().st_atime, src.stat().st_mtime))   # the upload date stays the film's date
    total = duration(mp4)
    srt = BUILT / f"{name}.srt"
    if srt.exists():
        write_if_changed(OUT / f"{name}.vtt", srt_to_vtt(srt.read_text(encoding="utf-8")))
    if marks:
        write_if_changed(OUT / f"{name}-chapters.vtt", chapters_vtt(marks, total))
    poster = OUT / f"{name}.webp"
    if not poster.exists() or poster.stat().st_mtime < src.stat().st_mtime:
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", "2", "-i", str(mp4), "-frames:v", "1",
                        "-vf", "scale=1280:-2", "-c:v", "libwebp", "-quality", "80", str(poster)], check=True)
    return {"name": name, "lang": lang, "duration": total, "chapters": marks,
            "subtitles": srt.exists(), "chapter_track": bool(marks)}


def storyboard(folder, video_id):
    return json.loads((folder / f"{video_id}.json").read_text(encoding="utf-8"))


def pick(texts, lang):
    return texts.get(lang) or texts.get("en") or ""


def collect():
    """-> [{id, title{lang}, films: [{id, title, description, docs, files{lang: prepared}}]}]"""
    OUT.mkdir(parents=True, exist_ok=True)
    RELEASE.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.mp4"):   # the films used to sit here; the web space is too small for them
        stale.unlink()
    series = []
    for path, folder in SERIES:
        data = json.loads(path.read_text(encoding="utf-8"))
        films = []
        for number, video_id in enumerate(data["videos"], start=1):
            board = storyboard(folder, video_id)
            files = {}
            for lang in ("en", "de"):
                src = BUILT / f"{video_id}-{lang}.mp4"
                made = datetime.date.fromtimestamp(src.stat().st_mtime) if src.exists() else datetime.date.today()
                meta = film_metadata(board, lang, pick(data["title"], lang), number, len(data["videos"]), made)
                prepared = prepare(video_id, lang, meta)
                if prepared:
                    files[lang] = prepared
            if not files:
                print(f"not built, left out: {video_id}")
                continue
            films.append({"id": video_id, "title": board.get("title", {}), "description": board.get("description", {}),
                          "docs": board.get("docs"), "files": files})
        if films:
            series.append({"id": data["id"], "title": data["title"], "films": films})
    return series


def e(text):
    return html.escape(text, quote=True)


def film_html(film, lang, t):
    """One list entry; without JavaScript it is a plain link to the MP4 and its chapters jump into it."""
    f = film["files"].get(lang) or film["files"]["en"]
    shown = f["lang"]
    base = "{{root}}videos/" + f["name"]
    title, desc = pick(film["title"], shown), pick(film["description"], shown)
    mp4 = MP4_URL + f["name"] + ".mp4"
    attrs = [f'data-src="{mp4}"', f'data-poster="{base}.webp"', f'data-lang="{shown}"',
             f'data-label="{LANG_LABEL[shown]}"']
    if f["subtitles"]:
        attrs.append(f'data-vtt="{base}.vtt"')
    if f["chapter_track"]:
        attrs.append(f'data-chapters="{base}-chapters.vtt"')
    if film["docs"]:
        attrs.append(f'data-docs="{{{{root}}}}docs/{film["docs"]}"')
    o = [f'<li class="video-item" id="{film["id"]}" {" ".join(attrs)}>',
         f'<a class="video-open" href="{mp4}">'
         f'<span class="video-thumb"><img src="{base}.webp" alt="" width="1280" height="720" loading="lazy" decoding="async">'
         f'<span class="video-length">{clock(f["duration"])}</span></span>'
         f'<span class="video-title">{e(title)}</span></a>',
         f'<p class="video-desc">{e(desc)}</p>']
    if f["chapters"]:
        items = "".join(f'<li><a href="{mp4}#t={s}" data-t="{s}"><span>{clock(s)}</span> {e(name)}</a></li>'
                        for s, name in f["chapters"])
        o.append(f'<ol class="video-chapters" aria-label="{t["chapters"]}">{items}</ol>')
    o.append("</li>")
    return "\n".join(o)


def json_ld(series, lang):
    items, pos = [], 0
    for s in series:
        for film in s["films"]:
            f = film["files"].get(lang) or film["files"]["en"]
            pos += 1
            secs = int(round(f["duration"]))
            items.append({"@type": "ListItem", "position": pos, "item": {
                "@type": "VideoObject", "name": pick(film["title"], f["lang"]),
                # the AI disclosure travels with every film's description (EU AI Act Art. 50)
                "description": (pick(film["description"], f["lang"]) or pick(film["title"], f["lang"]))
                               + " " + TEXT[f["lang"]]["note"],
                "thumbnailUrl": f"{BASE_URL}videos/{f['name']}.webp",
                "contentUrl": MP4_URL + f["name"] + ".mp4",
                # schema.org / Google: an ISO 8601 date-time with a time zone, e.g. 2026-11-26T08:15:00+00:00
                "uploadDate": datetime.datetime.fromtimestamp((RELEASE / f"{f['name']}.mp4").stat().st_mtime,
                                                              tz=datetime.timezone.utc).replace(microsecond=0).isoformat(),
                "duration": f"PT{secs // 60}M{secs % 60}S", "inLanguage": f["lang"]}})
    data = {"@context": "https://schema.org", "@type": "ItemList", "itemListElement": items}
    return '<script type="application/ld+json">' + json.dumps(data, ensure_ascii=False) + "</script>"


def page(series, lang):
    t = TEXT[lang]
    root = "../" if lang == "en" else "../../"
    first = series[0]["films"][0]
    f0 = first["files"].get(lang) or first["files"]["en"]
    b0 = "{{root}}videos/" + f0["name"]
    o = [f"title: {t['title']}", f"description: {t['description']}", "nav: videos",
         f'head: <script src="{root}assets/js/videos.js" defer></script>', f"head: {json_ld(series, lang)}", "",
         '<main id="main">',
         '<section class="page-head"><div class="wrap">',
         f'<div class="eyebrow">{t["eyebrow"]}</div><h1 class="page-title">{t["h1"]}</h1>',
         f'<p class="lead">{t["lead"]}</p>',
         f'<nav class="jump" aria-label="{t["jump"]}">'
         + "".join(f'<a href="#{s["id"]}">{e(pick(s["title"], lang))}{t["english"] if "de" not in s["title"] else ""}</a>'
                   for s in series) + "</nav>",
         "</div></section>",
         '<section class="section tight video-player-section"><div class="wrap">',
         '<div class="video-stage">',
         f'<video id="video-player" controls preload="metadata" playsinline poster="{b0}.webp" width="1920" height="1080">',
         f'<source src="{MP4_URL}{f0["name"]}.mp4" type="video/mp4">']
    if f0["subtitles"]:
        o.append(f'<track kind="subtitles" src="{b0}.vtt" srclang="{f0["lang"]}" label="{LANG_LABEL[f0["lang"]]}">')
    if f0["chapter_track"]:
        o.append(f'<track kind="chapters" src="{b0}-chapters.vtt" srclang="{f0["lang"]}">')
    o += [f'<p>{t["no_video"]} <a href="{MP4_URL}{f0["name"]}.mp4">{e(pick(first["title"], f0["lang"]))}</a></p>',
          "</video>",
          "</div>",
          '<div class="video-now" aria-live="polite">',
          # visible next to the player, before the first frame plays (EU AI Act Art. 50)
          f'<p class="video-ai"><b>{t["ai_badge"]}</b><span>{t["ai_label"]}</span></p>',
          f'<div class="eyebrow">{t["now"]}</div>',
          f'<h2 id="video-now-title">{e(pick(first["title"], f0["lang"]))}</h2>',
          f'<p id="video-now-desc" class="video-desc">{e(pick(first["description"], f0["lang"]))}</p>',
          f'<a id="video-now-docs" class="more" href="{{{{root}}}}docs/{first["docs"] or ""}"'
          + ("" if first["docs"] else " hidden") + f'>{t["docs"]}</a>',
          "</div>",
          "</div></section>"]
    for i, s in enumerate(series):
        alt = " section-alt" if i % 2 == 0 else ""
        heading = e(pick(s["title"], lang)) + (t["english"] if "de" not in s["title"] else "")
        o += [f'<section class="section tight{alt}" id="{s["id"]}"><div class="wrap stack-lg">',
              f'<div class="stack"><h2>{heading}</h2><p class="muted">{t["films"].format(n=len(s["films"]))}</p></div>',
              '<ol class="video-list">']
        o += [film_html(film, lang, t) for film in s["films"]]
        o += ["</ol>", "</div></section>"]
    o += [f'<section class="section tight"><div class="wrap"><p class="muted">{t["note"]}</p></div></section>',
          "</main>"]
    return "\n".join(o) + "\n"


def prune(series):
    """Deletes local files of films that are no longer on the page (the server and the release keep theirs)."""
    names = {f["name"] for s in series for film in s["films"] for f in film["files"].values()}
    for folder in (OUT, RELEASE):
        for path in folder.iterdir():
            stem = path.name.split(".")[0].removesuffix("-chapters")
            if stem not in names:
                print(f"no longer published, deleted: {path.relative_to(ROOT)}")
                path.unlink()


def main():
    if not shutil.which("ffmpeg") or not shutil.which("ffprobe"):
        sys.exit("ffmpeg and ffprobe are needed")
    series = collect()
    prune(series)
    for lang in ("en", "de"):
        target = SRC / lang / "videos" / "index.html"
        target.parent.mkdir(parents=True, exist_ok=True)
        write_if_changed(target, page(series, lang))
    films = sorted(RELEASE.glob("*.mp4"))
    files = sorted(OUT.iterdir())
    print(f"{len(films)} films ({sum(p.stat().st_size for p in films) / 1e6:.0f} MB) in {RELEASE.relative_to(ROOT)}; "
          f"{len(files)} files ({sum(p.stat().st_size for p in files) / 1e6:.1f} MB) in {OUT.relative_to(ROOT)}; "
          f"pages in website-src/{{en,de}}/videos/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
