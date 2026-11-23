# Tutorial videos

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
