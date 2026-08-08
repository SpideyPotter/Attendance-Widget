#!/usr/bin/env python3
"""Live probe for Maitri student weekly timetable (JSON API).

Same auth flow as attendance, then:
  GET stu_getBetweenDatesTimetableForStudentSP.json
      ?startDate=Aug 2, 2026&endDate=Aug 8, 2026&termId=<semesterId>

Credentials (never commit these) — project-root `.env` or env vars:
  MAITRI_USERNAME=you@bmu.edu.in
  MAITRI_PASSWORD=…

Usage:
  python scripts/probe_timetable.py
"""

from __future__ import annotations

import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta
from pathlib import Path

BASE = "https://maitri.bmu.edu.in"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
)
OUT_DIR = Path(__file__).resolve().parents[1] / "tmp" / "timetable_probe"


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip().strip("'").strip('"'))


def require_creds() -> tuple[str, str]:
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    username = os.environ.get("MAITRI_USERNAME", "").strip()
    password = os.environ.get("MAITRI_PASSWORD", "").strip()
    if not username or not password:
        sys.exit(
            "Set MAITRI_USERNAME and MAITRI_PASSWORD (env or project-root .env)."
        )
    if "@" not in username:
        print(
            f"warning: '{username}' looks like a local-part only; "
            "Maitri usually wants the full email.",
            file=sys.stderr,
        )
    return username, password


def build_opener() -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(
    opener: urllib.request.OpenerDirector,
    url: str,
    *,
    data: dict[str, str] | None = None,
    params: dict[str, str] | None = None,
    referer: str | None = None,
    accept: str = "text/html,application/json,*/*",
    xhr: bool = False,
) -> tuple[str, str, bytes]:
    if params:
        url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
    body = None
    headers = {
        "User-Agent": USER_AGENT,
        "Accept": accept,
    }
    if referer:
        headers["Referer"] = referer
    if xhr:
        headers["X-Requested-With"] = "XMLHttpRequest"
    if data is not None:
        body = urllib.parse.urlencode(data).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(
        url,
        data=body,
        headers=headers,
        method="POST" if data is not None else "GET",
    )
    with opener.open(req, timeout=30) as resp:
        return resp.geturl(), resp.headers.get("Content-Type", ""), resp.read()


def portal_date(d: date) -> str:
    # MMM d, yyyy — no zero-padded day (matches gems date filter).
    return f"{d.strftime('%b')} {d.day}, {d.year}"


def current_week_bounds(today: date | None = None) -> tuple[date, date]:
    today = today or date.today()
    # Sunday…Saturday (weekday: Mon=0 … Sun=6)
    sunday = today - timedelta(days=(today.weekday() + 1) % 7)
    saturday = sunday + timedelta(days=6)
    return sunday, saturday


def main() -> None:
    username, password = require_creds()
    opener = build_opener()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print("→ seed session")
    final, _, _ = request(opener, f"{BASE}/loginPage.htm")
    print(f"  landed on {final}")

    print("→ authenticate")
    final, _, _ = request(
        opener,
        f"{BASE}/j_spring_security_check",
        data={"j_username": username, "j_password": password},
        referer=f"{BASE}/login.htm",
    )
    print(f"  landed on {final}")
    if "login.htm" in final.lower():
        sys.exit("Login failed (still on login.htm). Check email + password.")

    print("→ GET terms")
    _, _, terms_body = request(
        opener,
        f"{BASE}/stu_getTermsOfStudentForCourceFile.json",
        accept="application/json, text/plain, */*",
        xhr=True,
        referer=f"{BASE}/stu_StudentTimeTable.htm",
    )
    terms = json.loads(terms_body.decode("utf-8"))
    current = max(terms, key=lambda t: t.get("semesterId", 0))
    term_id = current["semesterId"]
    print(f"  current term {current.get('terms2Name')} (semesterId={term_id})")

    start, end = current_week_bounds()
    start_label, end_label = portal_date(start), portal_date(end)
    print(f"→ GET timetable {start_label} → {end_label}")
    final, ctype, body = request(
        opener,
        f"{BASE}/stu_getBetweenDatesTimetableForStudentSP.json",
        params={
            "startDate": start_label,
            "endDate": end_label,
            "termId": str(term_id),
        },
        accept="application/json, text/plain, */*",
        xhr=True,
        referer=f"{BASE}/stu_StudentTimeTable.htm",
    )
    print(f"  landed on {final} ({ctype}, {len(body)} bytes)")
    out = OUT_DIR / "timetable_week.json"
    out.write_bytes(body)

    try:
        sessions = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError:
        sys.exit(f"Response was not JSON: {body[:200]!r}")

    if not isinstance(sessions, list):
        sys.exit(f"Expected JSON array, got {type(sessions)}")

    print(f"  {len(sessions)} session(s)\n")
    for row in sessions:
        day = str(row.get("lectureDate", "")).strip()
        faculty = " ".join(str(row.get("facultyName", "")).split())
        print(
            f"{day:16} {str(row.get('lectureDay', ''))[:3]:3} "
            f"{row.get('lectureStartTime', ''):>8}-{row.get('lectureEndTime', ''):<8} "
            f"{str(row.get('subjectName', '')):<12} {faculty}"
        )

    print(f"\nWrote {out}")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as exc:
        sys.exit(f"HTTP {exc.code}: {exc.reason}")
