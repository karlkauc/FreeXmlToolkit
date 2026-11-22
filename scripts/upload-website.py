#!/usr/bin/env python3
"""Uploads website/ to the web space of freexmltoolkit.org over FTPS (explicit TLS).

The password is read from ~/.netrc (never from the command line or the repository):

    machine host10.ssl-net.net login freexmltoolkit_org password ...

Usage:
    python3 scripts/upload-website.py --list [PATH]      show what is on the server (two levels deep)
    python3 scripts/upload-website.py --dir /html --dry-run
    python3 scripts/upload-website.py --dir /html

Uploads only files whose size differs from the server copy (or all with --all). Never deletes anything.
Runs scripts/check-website.py first and stops when it reports a problem.
"""
import argparse
import ftplib
import netrc
import pathlib
import subprocess
import sys

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


def upload(ftp, target, dry_run, everything):
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
    for local in sorted(p for p in SITE.rglob("*") if p.is_file()):
        rel = local.relative_to(SITE).as_posix()
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


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--list", nargs="?", const="/", metavar="PATH", help="list the server folder and exit")
    ap.add_argument("--dir", help="server folder that holds the website (the document root)")
    ap.add_argument("--dry-run", action="store_true", help="only show what would be uploaded")
    ap.add_argument("--all", action="store_true", help="upload every file, not only changed ones")
    ap.add_argument("--insecure", action="store_true", help="plain FTP without TLS (only if the server has no TLS)")
    args = ap.parse_args()

    ftp = connect(args.insecure)
    try:
        if args.list is not None:
            list_remote(ftp, args.list)
            return 0
        if not args.dir:
            sys.exit("--dir is required (see --list)")
        if subprocess.run([sys.executable, str(ROOT / "scripts" / "check-website.py")]).returncode != 0:
            sys.exit("check-website.py reported problems; nothing uploaded")
        upload(ftp, args.dir, args.dry_run, args.all)
    finally:
        try:
            ftp.quit()
        except Exception:
            ftp.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
