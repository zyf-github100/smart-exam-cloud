import argparse
import json
from urllib import error, request


def fetch_json(url: str, token: str | None = None) -> dict:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = request.Request(url, headers=headers)
    with request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def post_json(url: str, payload: dict) -> dict:
    req = request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", required=True, help="Gateway base URL, for example http://127.0.0.1:9000")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    args = parser.parse_args()

    gateway = args.gateway.rstrip("/")
    health_urls = [
        f"{gateway}/actuator/health",
    ]
    for url in health_urls:
        payload = fetch_json(url)
        print(url, payload.get("status", payload))

    login_resp = post_json(
        f"{gateway}/api/v1/auth/login",
        {"username": args.username, "password": args.password},
    )
    token = login_resp.get("data", {}).get("token")
    if not token:
        raise RuntimeError(f"Login failed: {login_resp}")
    me_resp = fetch_json(f"{gateway}/api/v1/users/me", token=token)
    print("/api/v1/users/me", me_resp.get("code", me_resp))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"HTTP {exc.code}: {body}")
