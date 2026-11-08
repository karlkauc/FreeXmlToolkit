# Contributing to FreeXmlToolkit

Thank you for your interest in FreeXmlToolkit! Contributions of every kind are welcome:
bug reports, feature ideas, documentation fixes and code.

By participating you agree to follow our [Code of Conduct](https://github.com/karlkauc/FreeXmlToolkit/blob/main/CODE_OF_CONDUCT.md).

## Ways to Help

- 🐞 **Report bugs** — see [Reporting Bugs](#reporting-bugs)
- 💡 **Suggest features** — open a [feature request](https://github.com/karlkauc/FreeXmlToolkit/issues/new/choose)
- 📖 **Improve the documentation** — the user guide lives in [`docs/`](https://github.com/karlkauc/FreeXmlToolkit/tree/main/docs)
- 🛠 **Contribute code** — see [Development Setup](#development-setup) and [Pull Requests](#pull-requests)
- ❤️ **Sponsor** the project on [GitHub Sponsors](https://github.com/sponsors/karlkauc)

Questions are welcome on the [Issues page](https://github.com/karlkauc/FreeXmlToolkit/issues) too —
but please check the [user guide](https://karlkauc.github.io/FreeXmlToolkit/) and the
[FAQ & Troubleshooting](https://karlkauc.github.io/FreeXmlToolkit/faq/) page first.

## Reporting Bugs

You can send a report straight from the application with **Help → Report a Problem…**, or open an
issue on GitHub. Before opening an issue, search the [existing issues](https://github.com/karlkauc/FreeXmlToolkit/issues)
to see whether it has already been reported. A good bug report contains:

- The **version** (**Help → About FreeXmlToolkit**) and how you installed it (installer, portable,
  Scoop, Homebrew, built from source)
- Your **operating system** and version
- **Steps to reproduce**, what you expected and what happened instead
- A **minimal sample file** (XML, XSD, XSLT, …) that shows the problem — please remove confidential
  content first
- The relevant part of the **log file**: **Settings → Files & Cache → LOG FILES → Open log folder**

### Security Vulnerabilities

Please **do not** report security vulnerabilities in public issues. See the
[Security Policy](https://github.com/karlkauc/FreeXmlToolkit/security/policy) for how to report them privately.

## Development Setup

### Requirements

- **Java 25** with JavaFX 25 — we use the [Liberica Full JDK](https://bell-sw.com/pages/downloads/),
  which bundles JavaFX. The build uses preview features (`--enable-preview`).
- **Git**. Gradle is provided by the wrapper (`./gradlew`), no separate installation needed.

### Build and Run

```bash
git clone https://github.com/karlkauc/FreeXmlToolkit.git
cd FreeXmlToolkit
./gradlew run                                    # start the application
./gradlew test                                   # run all tests
./gradlew test --tests "ClassName.methodName"    # run a single test
./gradlew clean build                            # clean build
```

The UI tests use TestFX and need a display. On a headless Linux machine run them with
`xvfb-run ./gradlew test`. The full suite takes a while — run the tests related to your change
while you work, and the full suite before opening a pull request.

Every pull request runs the whole suite on GitHub Actions (workflow `Tests`): it is split into four parallel
jobs and takes about half an hour. The job summary lists failed tests and the line coverage; the HTML test
report of a failed job and the coverage report are attached to the run as artifacts. To run one of the
four parts locally: `./gradlew test -PtestShards=4 -PtestShard=0` (0 to 3).

## Coding Guidelines

The project has detailed architecture notes in [`CLAUDE.md`](https://github.com/karlkauc/FreeXmlToolkit/blob/main/CLAUDE.md) and
[`.claude/rules/`](https://github.com/karlkauc/FreeXmlToolkit/tree/main/.claude/rules) (architecture, domain model, quick reference). The most important
rules:

- **English everywhere** — comments, JavaDoc and the source texts of the interface.
- **Translations** — the interface texts live in `src/main/resources/i18n/messages.properties`
  (English) and `messages_de.properties` (German). In FXML write the English text and run
  `scripts/i18n-extract-fxml.py`: it replaces it with a `%Layout.key` reference and adds it to the
  English bundle. In Java use `I18n.t("key")` / `I18n.t("key", args…)` (MessageFormat). Add the German
  text for every new key — `I18nBundlesTest` fails for missing keys or mismatched placeholders.
- **Tests** — add unit tests for new features and bug fixes; make sure existing tests still pass.
- **UI styling** — consult [`STYLE_GUIDE.jsonc`](https://github.com/karlkauc/FreeXmlToolkit/blob/main/STYLE_GUIDE.jsonc) before changing or creating UI.
  Use the existing `-fxt-*` design tokens and CSS classes instead of hard-coded colors.
- **Icons** — use the `IconifyIcon` control with Bootstrap Icon names (`bi-*`,
  [icons.getbootstrap.com](https://icons.getbootstrap.com/)). `IconifyIconCoverageTest` fails for
  unknown names. Menu items and context menus always have an icon and text.
- **Layout in FXML, behaviour in Java** — panels, pages and dialogs load their layout from
  `src/main/resources/pages/…/<ClassName>.fxml` via `FxmlViews.load(this, …)`. `@FXML` handlers must be
  `public` (required by the native packages). `FxmlContractTest` checks every FXML against its class.
- **Commands for model changes** — XSD/XML/JSON model changes always go through a command and the
  `CommandManager` (undo/redo), never by modifying the model directly from UI code.
- **Never block the UI thread** — load and compute in the background (`ThreadPoolManager` /
  `FxtGui.executorService`) and update the UI with `Platform.runLater()`.
- **Network access** only through `ConnectionService`, so proxy settings are respected
  (`HttpCallsGuardTest` enforces it).
- **Circular references** — XSD types can reference themselves; always guard tree traversals.
- Keep classes focused; split large classes and remove unused code.

## Documentation

The user guide is an [MkDocs](https://www.mkdocs.org/) site in [`docs/`](https://github.com/karlkauc/FreeXmlToolkit/tree/main/docs), published at
[karlkauc.github.io/FreeXmlToolkit](https://karlkauc.github.io/FreeXmlToolkit/). When you change a
feature, update the matching page. New pages need an entry in the `nav` section of
[`mkdocs.yml`](https://github.com/karlkauc/FreeXmlToolkit/blob/main/mkdocs.yml). To preview locally:

```bash
pip install -r requirements-docs.txt
mkdocs serve
```

## Pull Requests

1. For larger changes, **open an issue first** so we can agree on the approach before you invest time.
2. Fork the repository and create a branch from `main`.
3. Keep pull requests **focused** — one feature or fix per PR.
4. Write commit messages in the [Conventional Commits](https://www.conventionalcommits.org/) style
   used in this repository, e.g. `feat(xsd): …`, `fix(grid): …`, `docs: …`.
5. Make sure the tests pass and add screenshots for UI changes.
6. Fill in the pull request template and link the related issue (`Closes #123`).

## License

FreeXmlToolkit is licensed under the [Apache License 2.0](https://github.com/karlkauc/FreeXmlToolkit/blob/main/LICENSE.md). By submitting a contribution
you agree that it is licensed under the same terms.
