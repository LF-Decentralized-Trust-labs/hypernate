# Contributing to the Docs

This documentation site is built with [MkDocs](https://www.mkdocs.org/) and the
[Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) theme. All content lives as
Markdown under the `docs/` directory, and the site is published automatically — you only need to
edit Markdown.

## Setup

The documentation toolchain is Python-based. Create a virtual environment and install the pinned
dependencies (CI uses Python 3.12):

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r docs/requirements.txt
```

## Previewing Locally

Run the live-reload preview server from the repository root:

```bash
mkdocs serve
```

Then open <http://127.0.0.1:8000>. The site rebuilds automatically as you save files.

Before pushing, reproduce the strict build that CI effectively performs:

```bash
mkdocs build --strict
```

The site is configured with `strict: true`, so **warnings are treated as errors** — a broken
internal link or a page missing from the navigation will fail the build.

## Editing and Adding Pages

- **Edit a page:** change the relevant `.md` file under `docs/`; the preview reloads.
- **Add a page:** create the `.md` file in the appropriate subdirectory, then register it in the
  `nav:` section of `mkdocs.yml`. A page that is not in `nav` triggers a warning under strict mode.
- **Link between pages:** link to the Markdown *file*, not the built URL — for example
  `[Reporting Issues](issues.md)`. MkDocs resolves and validates these links.

## How the Site Is Published

You do not deploy the site manually. The **Deploy Documentation** workflow
(`.github/workflows/docs.yml`) handles it:

- On every push to `main`, the site is published as the `latest` version.
- On a `v*.*.*` tag, that release is published as a versioned snapshot.

Versioning is managed by [mike](https://github.com/jimporter/mike), so there is no need to run
`mike` locally. Each page also has an *edit* link in the top-right that points back to its source
file on GitHub.
