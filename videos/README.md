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

## FundsXML basics (`videos/fundsxml/`)

A set of its own that explains the FundsXML standard with FreeXmlToolkit, English only for now; its
films are copied to `FXT-Videos/fundsxml/`. The example documents are in
`src/test/resources/fundsxml/video/` (copies of github.com/fundsxml/examples with a local schema
location and correct ISIN/LEI check digits); the schema comes from `../fundsxml_schema`
(`FundsXmlVideoFiles.java`); the steps the scenarios share are in `FundsXmlVideoKit.java`.

The title and closing cards are in the FundsXML look (`videos/fundsxml/theme.json`, logo and Manrope in
`videos/fundsxml/brand/`, after https://branding.status20.net/): the title card names the episode,
the closing card lists the series with the next film, the links and a disclaimer; the upload text gets
the same links and disclaimer. A set without `theme.json` keeps the FreeXmlToolkit cards.

```bash
python3 scripts/make-video.py fundsxml-validate --set fundsxml --lang en
python3 scripts/make-video.py --series --set fundsxml --lang en
```

| # | Id | Title | Status |
|---|----|-------|--------|
| 1 | `fundsxml-what-is` | What is FundsXML? | recorded |
| 2 | `fundsxml-extension` | Set up FreeXmlToolkit for FundsXML | recorded |
| 3 | `fundsxml-fund-shareclass` | Funds, share classes and NAV | recorded |
| 4 | `fundsxml-portfolio` | Portfolio positions and asset master data | recorded |
| 5 | `fundsxml-validate` | Valid XML is not correct data: schema and data-quality checks | recorded |

### Overview: `fundsxml-explained` (stand-alone, about 10 min)

"FundsXML explained: one language for fund data" is for business readers: what the standard is for,
what it covers, who uses it, quality, how a project runs. It is not in `series.json`
(`"standalone": true`); its closing card points to the series instead. Most of it is **slides**, with
two short runs in FreeXmlToolkit (`FundsXmlExplainedDocVideo.java`):

- A scene with `"slide"` is not recorded: `make-video.py` draws it in the set's theme (needs `theme.json`).
- Layouts: `statement` (headline, sub), `bullets`, `tiles` (items with head, text, optional tag),
  `steps` (a numbered chain), and `hub` (actors around a centre; `"mode": "mesh"` joins all with all).
- `"builds"` gives, per part, the share of the narration at which it fades in (missing ones: at once).
- Recorded scenes between two slides form one run of the screen recording. A storyboard without slides
  is cut exactly as before.

```bash
python3 scripts/make-video.py fundsxml-explained --set fundsxml --lang en
```

Files over 19 MiB are copied to `FXT-Videos` as a zip cut into 19 MiB parts (`<name>.zip.001`, …),
because the folder is copied over WebDAV, which takes files below 20 MB (20,000,000 bytes) only.

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
