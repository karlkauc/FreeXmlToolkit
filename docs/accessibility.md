# Accessibility

This page documents the accessibility audit of FreeXmlToolkit (version 2.10, November 2026): what
was checked, what was fixed, and what is still open. The reference is **WCAG 2.1 level AA**, the
standard that EN 301 549 applies to the user interface of desktop software.

## Summary

| Area | Result |
|---|---|
| Names for screen readers (buttons, input fields) | All buttons and input fields of the shell have a name (before: 38 unnamed buttons, 22 unlabelled fields) |
| Text contrast (light and dark theme) | All text colours reach 4.5:1 on their backgrounds (before: 10 combinations below) |
| Keyboard | Every function of the shell can be reached from the keyboard, except four options of the grid view header |
| Visual views | The graphic XSD view and the grid view are drawn on a canvas and are not readable by screen readers; the Text and Tree views show the same content |
| Screen reader support of the platform | Windows (Narrator, NVDA, JAWS) and macOS (VoiceOver) through JavaFX; Linux: none (JavaFX has no screen reader bridge on Linux) |
| Interface language | English and German ([Settings → Language](settings.md)) |

The automated checks run with the test suite, so the results stay true for later changes.

## How it was checked

1. **Automated audit of the user interface** (`AccessibilityAuditTest`). The test opens the shell,
   visits every activity (Explorer, Search, Favorites, Validation, Transform, Schema, Schema Library,
   PDF / FOP, Signature, FundsXML, Settings), opens an XML document in the Text, Tree and Grid views,
   and walks the scene graph. It reports:
    - buttons without a name: no text and no accessible text (a tooltip alone is not announced);
    - input fields without a label: no accessible text, no associated label (`Label.labelFor`) and
      no prompt text;
    - buttons outside the keyboard tab order;
    - content drawn on a canvas.

    The test fails when a button or field without a name appears, or a control outside the tab order
    that has no documented keyboard alternative (list below).
2. **Contrast of the design tokens** (`ContrastTest`). The test computes the WCAG contrast ratio of
   every text colour on the backgrounds it is used on, in the light and the dark theme, and requires
   4.5:1.
3. **Review** of the keyboard paths (menu bar, shortcuts, focus indication) and of the views that
   are drawn rather than built from controls.

The audit did **not** include a session with a screen reader user or manual testing with NVDA or
VoiceOver; that is the recommended next step (see [Open points](#open-points)).

## Fixes made in the audit

### Names for screen readers

Icon-only buttons (toolbar, side panel headers, favourites stars, Inspector actions) only had a
tooltip, so screen readers announced "button". Input fields in the settings and panels had a visible
label next to them that was not associated with the field.

- Every window now names its controls automatically (`AccessibleNames`): a button without text takes
  the first line of its tooltip, a card button (Welcome page) the title inside it; an input field
  is associated with the label right before it (also in the same row of a grid). The names follow
  the interface language.
- The few controls without tooltip or label got an explicit name (Validation panel ⋮ menu, the
  favourites and recent menus of the Transform and Validation panels, the ◀ / ▶ favourites buttons,
  the language selection, the output method, the proxy test address).

### Contrast

| Colour | Light theme | Dark theme |
|---|---|---|
| Secondary hints (`-fxt-text-muted`), e.g. explanations under settings | #8a93a0 → **#667080** (2.8–3.1:1 → 4.6–5.0:1) | #6b7480 → **#858f9c** (3.3–3.9:1 → 4.8–5.3:1) |
| Primary colour as text (links, selected entries) | new token `-fxt-primary-text` **#1068c4** (4.1–4.4:1 → 4.9–5.5:1); the brand colour #1373d9 stays for fills | unchanged #4c9bf5 (6.0:1) |
| Text on primary buttons | unchanged (4.7:1) | new fill `-fxt-primary-fill` **#1f6fd1** behind white text (2.9:1 → 4.9:1) |
| Warning as text | new token `-fxt-warning-text` **#a65500** (2.5:1 → 5.0:1); the icon colour #f08c00 stays | unchanged #fab005 (9.3:1) |

### Keyboard

- The theme switch (Light / Dark) on the Settings page and the FAVORITES / RECENT tabs of the
  Explorer are now in the tab order.

## Keyboard access

All commands are in the menu bar, which opens with **F10** or **Alt** (menu titles have access keys:
**Alt+F** File, **Alt+E** Edit, **Alt+V** View, **Alt+T** Tools, **Alt+H** Help; in German **Alt+D**
Datei, **Alt+B** Bearbeiten, **Alt+A** Ansicht, **Alt+W** Werkzeuge, **Alt+H** Hilfe) and is navigated with
the arrow keys. Frequent commands have shortcuts — see [Keyboard Shortcuts](keyboard-shortcuts.md).
Focused controls show the focus ring of the theme.

Some controls of the window frame are deliberately left out of the tab order, so that a mouse click
on them does not take the focus away from the document. Each one has a keyboard alternative:

| Control | Keyboard alternative |
|---|---|
| Activity Bar buttons (Explorer, Search, …) | **View → Activities**, **Ctrl+Shift+D** (Favorites) |
| Menu bar titles | **F10** / **Alt** |
| Help button (**?**) | **F1** |
| Theme button in the header | **View → Theme**, Settings → Appearance |
| Favourites stars (header, Explorer) | **File → Open Favorite** |
| Side panel and Properties toggles | **Ctrl+L**, **Ctrl+Shift+P** (also in the View menu) |
| View switch (Text / Tree / Graphic) | **View → Text / Tree / Graphic View** |
| Grid zoom buttons | **Ctrl++**, **Ctrl+−**, **Ctrl+0** |

## Known limitations

- **Canvas views.** The Graphic view of XSD schemas (diagram) and the Grid view of XML and JSON
  documents are drawn on a canvas. A screen reader cannot read their content. The grid view is fully
  keyboard-operable ([shortcuts](keyboard-shortcuts.md#grid-view-graphic)), and the Text and Tree views
  show the same document with standard controls.
- **Grid view header options** — *Optimal widths*, *Word wrap*, *Entry helpers* and the ⋮ grid
  settings menu — can only be used with the mouse.
- **Linux.** JavaFX provides no screen reader support on Linux.
- **No high-contrast theme.** The application has a light and a dark theme; it does not follow the
  high-contrast mode of Windows. Text in editors can be enlarged with **Ctrl++**; the size of the
  rest of the interface follows the system's display scaling.
- **Interface texts.** Most texts are translated into German; some messages built in code are still
  English (see [Settings → Language](settings.md)).

## Open points

1. Test with screen readers (NVDA and Narrator on Windows, VoiceOver on macOS), ideally with users who
   rely on them, and fix what the automated checks cannot find (reading order, announcements of
   status changes such as validation results).
2. Keyboard access to the grid view header options.
3. A high-contrast theme, or following the system's high-contrast setting.
4. An accessible alternative description for the XSD diagram (for example the structure as a
   navigable tree next to it).

## For developers

- `AccessibilityAuditTest` and `ContrastTest` (package `org.fxt.freexmltoolkit.a11y`) fail for
  regressions; the audit writes its findings to `build/a11y-audit.md`.
- Give every icon-only button a tooltip (it becomes its name) or `accessibleText`; put a `Label`
  right before each input field, or set `accessibleText` / `labelFor`.
- Use `-fxt-primary-text` for text in the primary colour, `-fxt-primary-fill` for fills behind
  `-fxt-on-primary` text, and `-fxt-warning-text` for warning text.
- A control that should stay out of the tab order needs a keyboard alternative listed in
  `AccessibilityAuditTest.KEYBOARD_ALTERNATIVES` and on this page.
