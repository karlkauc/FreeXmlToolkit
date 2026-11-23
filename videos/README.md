# Tutorial videos

## Videos

The first series, in the order it is meant to be watched. Status: **published** (film in
`FXT-Videos`, both languages), **in progress**, **planned**.

| # | Id | Title (EN / DE) | Docs page | Status |
|---|----|-----------------|-----------|--------|
| 1 | `workspace-tour` | A tour of the workspace / Ein Rundgang durch die Arbeitsfläche | `get-started/workspace-tour.md` | published |
| 2 | `validate-xml` | Validate an XML file against its schema / Eine XML-Datei gegen ihr Schema prüfen | `get-started/first-steps.md` | published (pilot) |
| 3 | `xml-grid` | The XML grid: edit a document as tables / Das XML-Grid: ein Dokument als Tabellen bearbeiten | `guides/edit-xml/grid.md` | published |
| 4 | `xsd-graphic` | Edit an XSD schema graphically / Ein XSD-Schema grafisch bearbeiten | `guides/design-schemas/graphic-view.md` | published |
| 5 | `xslt-debug` | Run and debug an XSLT stylesheet / Ein XSLT-Stylesheet ausführen und debuggen | `guides/transform-query/run-xslt.md`, `debug.md` | published |
| 6 | `schematron-quick-fixes` | Correct problems with Schematron quick fixes / Probleme mit Schematron-Schnellkorrekturen beheben | `guides/validate/schematron-quick-fixes.md` | published |
| 7 | `projects` | Projects: schemas and scenarios for a whole folder / Projekte: Schemas und Szenarien für einen ganzen Ordner | `guides/projects.md` | published |

Keep this table up to date when a video is added or re-recorded. The order is also in
`series.json`: `python3 scripts/make-video.py --series --lang de` joins the finished films into one,
`all-videos-de.mp4` with a chapter per video, one subtitle file and an `.m3u` playlist of the single
films, all copied to `FXT-Videos` as well.

## How a video is made

Every video is generated: the storyboard here, the speech from a local model (Piper), the picture
from the real application driven by a scenario class, the cut by ffmpeg.

```bash
python3 scripts/make-video.py validate-xml --lang de     # → build/videos/validate-xml-de.mp4, .srt, .txt
python3 scripts/make-video.py validate-xml --lang en --steps compose   # only cut again
```

| Part | Where |
|------|-------|
| Storyboard: scenes with the spoken text per language, chapters, title, docs link | `videos/<id>.json` |
| Spellings for the speech only (`XSD` → `X S D`) | `videos/pronunciation.json` |
| Scenario: what happens on screen in each scene (same scene ids) | `src/test/java/org/fxt/freexmltoolkit/screenshots/<Name>DocVideo.java` |
| Recorder: screen grab, pointer, clicks, typing, zoom regions → `events.json` | `DocVideoRecorder.java` (same folder) |
| Speech, recording run, cut, subtitles, upload text | `scripts/make-video.py` |

The finished film, subtitles and upload text are also copied to `$FXT_VIDEO_COPY_TO`, or to a folder
`FXT-Videos` next to the repository when it exists.

A scene with `"card": "title"` or `"outro"` is a generated card; every other scene must be played
by the scenario with `video.scene("<id>", …)`. The speech is made first and the recording waits for
it (`video.spoken(0.5)` = half of the scene's text has been said), so a longer translation needs no
change. A new video = a new storyboard + a new `*DocVideo` class.

Needs `ffmpeg`, `xvfb-run` and Pillow; Piper and the voices (`de_DE-thorsten-high`,
`en_US-lessac-high`) are installed on first use into `~/.cache/fxt-video`.
