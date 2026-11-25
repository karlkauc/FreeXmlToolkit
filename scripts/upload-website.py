#!/usr/bin/env python3
"""Uploads website/ to the web space of freexmltoolkit.org over FTPS (explicit TLS), or with rsync over SSH.

The password is read from ~/.netrc (never from the command line or the repository):

    machine host10.ssl-net.net login freexmltoolkit_org password ...

Usage:
    python3 scripts/upload-website.py --list [PATH]      show what is on the server (two levels deep)
    python3 scripts/upload-website.py --dir /www --dry-run
    python3 scripts/upload-website.py --dir /www
    python3 scripts/upload-website.py --dir /www --docs [--dry-run]    the documentation, to <dir>/docs
    python3 scripts/upload-website.py --dir /www --videos [--dry-run]  the tutorial videos, to <dir>/videos

With --rsync DEST the files go to an own server instead, over SSH (key from your ssh configuration; no
password here). freexmltoolkit.org uses an upload-only account behind rrsync, so DEST is the alias of
~/.ssh/config with nothing after the colon (paths are relative to the document root). --docs, --videos,
--dry-run and --all work the same:

    python3 scripts/upload-website.py --rsync fxtweb-upload: --docs
    python3 scripts/upload-website.py --rsync fxtweb-upload:

Uploads only files whose size differs from the server copy (or all with --all). Never deletes anything.
Runs scripts/check-website.py first and stops when it reports a problem.

--docs builds the MkDocs documentation (mkdocs build --strict, as the GitHub workflow does) into a
temporary folder and uploads that to the docs/ folder of the website instead of website/ itself.
The website links to it with relative links, so upload the documentation before the website when
pages were added.

--videos uploads build/website-videos/ (made by scripts/website-videos.py: subtitles, chapters, posters) to the
videos/ folder of the website. Upload it before the page that plays them.
"""
import argparse
import ftplib
import netrc
import pathlib
import subprocess
import sys
import tempfile

HOST = "host10.ssl-net.net"
PORT = 21
ROOT = pathlib.Path(__file__).resolve().parent.parent
SITE = ROOT / "website"


def connect(insecure):
    auth = netrc.netrc().authenticators(HOST)
    if not auth:
        sys.exit(f"No entry for {HOST} in ~/.netrc")
    login, _, password = auth
    if insecure:
        ftp = ftplib.FTP()
        ftp.connect(HOST, PORT, timeout=30)
        ftp.login(login, password)
    else:
        ftp = ftplib.FTP_TLS()
        ftp.connect(HOST, PORT, timeout=30)
        ftp.login(login, password)
        ftp.prot_p()
    ftp.set_pasv(True)
    return ftp


def entries(ftp, path):
    """(name, is_dir, size) of a remote folder; MLSD when available, else NLST + SIZE."""
    try:
        out = []
        for name, facts in ftp.mlsd(path):
            if name in (".", ".."):
                continue
            out.append((name, facts.get("type") == "dir", int(facts.get("size", 0) or 0)))
        return out
    except ftplib.error_perm as e:
        if not str(e).startswith(("500", "502", "504")):
            raise
        out = []
        for full in ftp.nlst(path):
            name = full.rsplit("/", 1)[-1]
            if name in (".", ".."):
                continue
            try:
                out.append((name, False, ftp.size(f"{path.rstrip('/')}/{name}") or 0))
            except ftplib.error_perm:
                out.append((name, True, 0))
        return out


def list_remote(ftp, path, depth=0, max_depth=2):
    for name, is_dir, size in sorted(entries(ftp, path)):
        print("  " * depth + (f"{name}/" if is_dir else f"{name}  ({size} bytes)"))
        if is_dir and depth + 1 < max_depth:
            list_remote(ftp, f"{path.rstrip('/')}/{name}", depth + 1, max_depth)


def ensure_dir(ftp, path, known):
    parts = [p for p in path.split("/") if p]
    current = ""
    for part in parts:
        current += "/" + part
        if current in known:
            continue
        try:
            ftp.mkd(current)
        except ftplib.error_perm:
            pass
        known.add(current)


def upload(ftp, source, target, dry_run, everything):
    target = "/" + target.strip("/") if target.strip("/") else ""
    known_dirs = set()
    remote_sizes = {}

    def remote_size(rpath):
        folder, name = rpath.rsplit("/", 1)
        folder = folder or "/"
        if folder not in remote_sizes:
            try:
                remote_sizes[folder] = {n: s for n, d, s in entries(ftp, folder) if not d}
            except (ftplib.error_perm, ftplib.error_temp):
                remote_sizes[folder] = {}
        return remote_sizes[folder].get(name)

    sent = skipped = 0
    for local in sorted(p for p in source.rglob("*") if p.is_file()):
        rel = local.relative_to(source).as_posix()
        if local.name in (".DS_Store", "._.DS_Store"):
            continue
        rpath = f"{target}/{rel}"
        size = local.stat().st_size
        if not everything and remote_size(rpath) == size:
            skipped += 1
            continue
        print(("would upload " if dry_run else "upload ") + rpath)
        if not dry_run:
            ensure_dir(ftp, rpath.rsplit("/", 1)[0], known_dirs)
            with local.open("rb") as fh:
                ftp.storbinary(f"STOR {rpath}", fh)
        sent += 1
    print(f"{sent} file(s) {'to upload' if dry_run else 'uploaded'}, {skipped} unchanged")


def rsync(source, dest, dry_run, everything):
    """Copies source/ into dest/ over SSH; never deletes (website, docs and videos share one tree)."""
    cmd = ["rsync", "-rt", "--no-links", "--itemize-changes", "--chmod=D755,F644",
           "--exclude", ".DS_Store", "--exclude", "._.DS_Store"]
    if dry_run:
        cmd.append("--dry-run")
    if everything:
        cmd.append("--ignore-times")
    cmd += [str(source).rstrip("/") + "/", dest.rstrip("/") + "/"]
    return subprocess.run(cmd).returncode


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", nargs="?", const="/", metavar="PATH", help="list the server folder and exit")
    ap.add_argument("--dir", help="server folder that holds the website (the document root)")
    ap.add_argument("--rsync", metavar="DEST",
                    help="rsync over SSH to DEST (user@host:/document/root) instead of FTPS")
    ap.add_argument("--dry-run", action="store_true", help="only show what would be uploaded")
    ap.add_argument("--all", action="store_true", help="upload every file, not only changed ones")
    ap.add_argument("--insecure", action="store_true", help="plain FTP without TLS (only if the server has no TLS)")
    ap.add_argument("--docs", action="store_true",
                    help="build the MkDocs documentation and upload it to <dir>/docs instead of the website")
    ap.add_argument("--videos", action="store_true",
                    help="upload build/website-videos (subtitles and posters, scripts/website-videos.py) to <dir>/videos instead of the website")
    args = ap.parse_args()

    if args.rsync and (args.dir or args.list is not None):
        sys.exit("--rsync replaces --dir and --list")
    source, target, build = SITE, args.rsync or args.dir, None
    if args.list is None:
        if not target:
            sys.exit("--dir or --rsync is required (see --list)")
        if args.docs:
            # built before the connection is opened: the server closes an idle control connection
            build = tempfile.TemporaryDirectory(prefix="fxt-docs-")
            if subprocess.run(["mkdocs", "build", "--strict", "-q", "-d", build.name], cwd=ROOT).returncode != 0:
                sys.exit("mkdocs build failed; nothing uploaded")
            source, target = pathlib.Path(build.name), target.rstrip("/") + "/docs"
        elif args.videos:
            source, target = ROOT / "build" / "website-videos", target.rstrip("/") + "/videos"
            if not any(source.glob("*.vtt")):
                sys.exit("build/website-videos is empty; run scripts/website-videos.py first")
        elif subprocess.run([sys.executable, str(ROOT / "scripts" / "check-website.py")]).returncode != 0:
            sys.exit("check-website.py reported problems; nothing uploaded")

    if args.rsync:
        try:
            return rsync(source, target, args.dry_run, args.all)
        finally:
            if build:
                build.cleanup()

    ftp = connect(args.insecure)
    try:
        if args.list is not None:
            list_remote(ftp, args.list)
            return 0
        upload(ftp, source, target, args.dry_run, args.all)
    finally:
        try:
            ftp.quit()
        except Exception:
            ftp.close()
        if build:
            build.cleanup()
    return 0


if __name__ == "__main__":
    sys.exit(main())
