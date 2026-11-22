# CI recipes

Run the checks you use in the editor on every push: validate documents, compare transformation
results with expected files, run XSpec tests. `fxt-cli` is installed with the application, sends
no telemetry and does not check for updates.

## Before you start

- A Linux runner. The examples install the `.deb` package of the latest release; the program is
  then `/opt/freexmltoolkit/bin/fxt-cli`.
- Schemas and Schematron files that the job can reach: in the repository, or declared by the
  documents. See [Operation without internet](../administer/offline.md) for closed networks.

## How a job fails

| Exit code | Meaning | Effect in CI |
|---|---|---|
| `0` | All documents valid, no difference | Job passes |
| `1` | Problems found, a difference, or a run failed | Job fails |
| `2` | Invalid command line or a missing input file | Job fails |
| `3` | The check could not run: schema does not load, file not well-formed | Job fails |

Warnings do not fail a validation unless you pass `--fail-on-warning`. Reports go to the file
named with `-r`; the text summary still goes to standard output.

## GitHub Actions

Validate a folder against a schema and show the findings in code scanning (SARIF):

```yaml
name: XML checks
on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      security-events: write
    steps:
      - uses: actions/checkout@v4

      - name: Install fxt-cli
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release download --repo karlkauc/FreeXmlToolkit --pattern '*_amd64.deb' --dir /tmp/fxt
          sudo apt-get install -y /tmp/fxt/*.deb
          echo "/opt/freexmltoolkit/bin" >> "$GITHUB_PATH"

      - name: Validate XML
        run: fxt-cli validate --xsd schema/order.xsd -f sarif -r fxt.sarif data/

      - uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: fxt.sarif
```

`if: always()` uploads the report also when the validation step failed, which is when you need it.

## GitLab CI

Validate with a JUnit report, shown in the merge request's test tab:

```yaml
validate:
  script:
    - fxt-cli validate --xsd schema/order.xsd -f junit -r fxt-junit.xml data/
  artifacts:
    when: always
    reports:
      junit: fxt-junit.xml
```

The job needs an image or runner on which `fxt-cli` is installed and on the `PATH`.

## Recipes

Each line is one job step; combine them as you need.

**Validate everything a project binds.** The [project file](../guides/projects.md) names schema,
Schematron and JSON Schema per file pattern, so the command line stays short:

```bash
fxt-cli validate --project orders.fxtproj -f junit -r build/validation.xml data/
```

**Treat warnings as errors.**

```bash
fxt-cli validate --xsd schema/order.xsd --fail-on-warning data/
```

**Check JSON and YAML configuration**, for example the workflows of the repository itself, against
the schemas of the SchemaStore catalog:

```bash
fxt-cli validate --schemastore .github/workflows docker-compose.yml
```

**Regression-test transformations.** Scenarios with an expected result are run and compared;
a difference fails the job:

```bash
fxt-cli scenario orders.fxtproj --check -f junit -r build/scenarios.xml
```

**Run XSpec tests.** The report is JUnit XML by default:

```bash
fxt-cli xspec tests/price.xspec -r build/xspec.xml
```

**Compare a generated file with a reference.** Exit code `1` means the documents differ:

```bash
fxt-cli compare expected/report.xml build/report.xml
```

**Publish schema documentation** as a build artifact or to a pages site:

```bash
fxt-cli xsd-doc schema/order.xsd -o site/
```

## Tips

- `-q` leaves only problems and requested reports in the log; `--verbose` adds the processing log.
- For very large files, set the heap in the environment: `JAVA_TOOL_OPTIONS=-Xmx8g`.
- The command line reads the same settings file as the application (proxy, Schema Library,
  catalogs); on a fresh runner there is none, and the defaults apply.

## Next steps

- [Validate and query](cli-validate.md), [Transform and publish](cli-transform.md),
  [Compare, merge, convert](cli-compare.md) - every option
- [Projects & scenarios](../guides/projects.md) - bindings and regression checks
- [MCP server](mcp-server.md) - the same engines as tools for AI agents
