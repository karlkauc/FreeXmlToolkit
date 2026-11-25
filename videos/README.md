# Tutorial videos

## Videos

The first series, in the order it is meant to be watched. Status: **published** (film in
`FXT-Videos`, both languages), **in progress**, **planned**.

| # | Id | Title (EN / DE) | Docs page | Status |
|---|----|-----------------|-----------|--------|
| 1 | `workspace-tour` | A tour of the workspace / Ein Rundgang durch die Arbeitsfläche | `get-started/workspace-tour.md` | published |
| 2 | `edit-xml-text` | Write XML in the text editor / XML im Text-Editor schreiben | `guides/edit-xml/index.md` | published |
| 3 | `validate-xml` | Validate an XML file against its schema / Eine XML-Datei gegen ihr Schema prüfen | `get-started/first-steps.md` | published |
| 4 | `xml-grid` | The XML grid: edit a document as tables / Das XML-Grid: ein Dokument als Tabellen bearbeiten | `guides/edit-xml/grid.md` | published |
| 5 | `xsd-graphic` | Edit an XSD schema graphically / Ein XSD-Schema grafisch bearbeiten | `guides/design-schemas/graphic-view.md` | published |
| 6 | `xslt-run` | Run an XSLT stylesheet / Ein XSLT-Stylesheet ausführen | `guides/transform-query/run-xslt.md` | published |
| 7 | `xslt-debug` | Run and debug an XSLT stylesheet / Ein XSLT-Stylesheet ausführen und debuggen | `guides/transform-query/run-xslt.md`, `debug.md` | published |
| 8 | `schematron-quick-fixes` | Correct problems with Schematron quick fixes / Probleme mit Schematron-Schnellkorrekturen beheben | `guides/validate/schematron-quick-fixes.md` | published |
| 9 | `projects` | Projects: schemas and scenarios for a whole folder / Projekte: Schemas und Szenarien für einen ganzen Ordner | `guides/projects.md` | published |
| 10 | `query-console` | XPath and XQuery in the Query Console / XPath und XQuery in der Abfragekonsole | `guides/transform-query/query-console.md` | published |
| 11 | `schema-library` | Find, manage and generate schemas / Schemas finden, verwalten und erzeugen | `guides/design-schemas/schema-library.md` | published |
| 12 | `compare-merge` | Compare and merge / Vergleichen und Zusammenführen | `guides/compare-merge.md` | published |

The extended storyboards (keywords, target lengths, two series) are planned in `videos/drehbuecher.md`.

Keep this table up to date when a video is added or re-recorded. The order is also in
`series.json`: `python3 scripts/make-video.py --series --lang de` joins the finished films into one,
`all-videos-de.mp4` with a chapter per video, one subtitle file and an `.m3u` playlist of the single
films, all copied to `FXT-Videos` as well.

## FundsXML (`videos/fundsxml/`)

A set of its own that explains the FundsXML standard, English only for now, in two series; its films
are copied to `FXT-Videos/fundsxml/`. The example documents are in `src/test/resources/fundsxml/video/`:
copies of the fictional *realistic* samples of github.com/fundsxml/examples
(`FundsXML_Files/4.2.9/realistic`, made by its `make_realistic.py`) with a local schema location, and two
variants with one wrong value for the validation film. The schema comes from `../fundsxml_schema`
(`FundsXmlVideoFiles.java`); the steps the scenarios share are in `FundsXmlVideoKit.java`; scenarios find
their places by element or value (`kit.line("<Bond>", from)`), never by fixed line numbers.

The title and closing cards are in the FundsXML look (`videos/fundsxml/theme.json`, logo and Manrope in
`videos/fundsxml/brand/`, after https://branding.status20.net/): the title card names the episode and
its series, the closing card lists the series with the next film, the links and a disclaimer; the
upload text gets the same links and disclaimer. A set without `theme.json` keeps the FreeXmlToolkit cards.

A set may hold several series (`series-<name>.json`, each with an `id`); a series names the one its
last film points to with `"next"`. Slides are drawn, not recorded:

- A scene with `"slide"` is drawn by `make-video.py` in the set's theme (needs `theme.json`).
- Layouts: `statement` (headline, sub), `bullets`, `tiles` (items with head, text, optional tag),
  `steps` (a numbered chain), and `hub` (actors around a centre; `"mode": "mesh"` joins all with all).
- `"builds"` gives, per part, the share of the narration at which it fades in (missing ones: at once).
- Recorded scenes between two slides form one run of the screen recording; a storyboard of slides
  only is not recorded. A storyboard without slides is cut exactly as before.

```bash
python3 scripts/make-video.py biz-why --set fundsxml --lang en
python3 scripts/make-video.py --series fundsxml-business --set fundsxml --lang en
```

**FundsXML for business** (`series-business.json`): for business readers, mostly slides, 5–8 min.

| # | Id | Title | Status |
|---|----|-------|--------|
| 1 | `biz-why` | Why fund data needs a standard | recorded |
| 2 | `biz-content` | What a FundsXML delivery contains | recorded (`FundsXmlBizContentDocVideo`) |
| 3 | `biz-regulatory` | Regulatory reporting with FundsXML | recorded |
| 4 | `biz-quality` | Trust in the data: quality and control | recorded (`FundsXmlBizQualityDocVideo`) |
| 5 | `biz-project` | Introducing FundsXML: a project from A to Z | recorded |

**FundsXML for developers** (`series-developers.json`): in a real file with FreeXmlToolkit, 3–6 min.

| # | Id | Title | Status |
|---|----|-------|--------|
| 1 | `dev-setup` | Set up FreeXmlToolkit for FundsXML | recorded (`FundsXmlDevSetupDocVideo`) |
| 2 | `dev-anatomy` | Anatomy of a delivery: structure and ControlData | recorded (`FundsXmlDevAnatomyDocVideo`) |
| 3 | `dev-funds` | Funds, share classes and NAV | recorded (`FundsXmlDevFundsDocVideo`) |
| 4 | `dev-portfolio` | Portfolios and asset master data | recorded (`FundsXmlDevPortfolioDocVideo`) |
| 5 | `dev-transactions` | Transactions and investor flows | recorded (`FundsXmlDevTransactionsDocVideo`) |
| 6 | `dev-regulatory` | Regulatory templates in XML | recorded (`FundsXmlDevRegulatoryDocVideo`) |
| 7 | `dev-validate` | Validate and check data quality | recorded (`FundsXmlDevValidateDocVideo`) |
| 8 | `dev-query` | Query, transform and integrate | recorded (`FundsXmlDevQueryDocVideo`) |

Files over 10 MiB are copied to `FXT-Videos` as a split zip in the standard Info-ZIP format
(`<name>.zip` with `<name>.z01`, … of 10 MiB each; open the `.zip` with 7-Zip, WinRAR or unzip, all
parts in one folder), because the folder is copied over WebDAV, which takes files below 20 MB only. In
a set, the copies start with the film's number across its series in watching order (`01-biz-why-en.mp4`,
`06-dev-setup-en.mp4`), so a file list shows them in order. `positions_csv.xslt` comes from
github.com/fundsxml/examples.

**Publishing.** `python3 scripts/publish-videos.py --set fundsxml --out ../../fundsxml-videos` writes the
contents of the GitHub Pages repository github.com/fundsxml/videos (served at https://fundsxml.github.io/videos/):
the films with `+faststart`, WebVTT subtitles and chapters, posters, `videos.json`, the player page `index.html`
and `wp-widget.html`, the same player as one HTML block for the draft page "FundsXML Training Videos" on
fundsxml.org. The player is `videos/web/player.html`; the full series films go to the repository's release.

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
