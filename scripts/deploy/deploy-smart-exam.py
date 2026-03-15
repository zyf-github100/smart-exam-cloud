import argparse
import io
import os
import tarfile
from pathlib import Path

import paramiko

from server_config import load_servers


ROOT = Path(__file__).resolve().parents[2]
BACKEND_JARS = [
    "gateway-service",
    "auth-service",
    "user-service",
    "question-service",
    "exam-service",
    "grading-service",
    "analysis-service",
    "admin-service",
]


_SERVERS: dict | None = None


def get_servers() -> dict:
    global _SERVERS
    if _SERVERS is None:
        _SERVERS = load_servers()
    return _SERVERS


def connect_client(target: str) -> paramiko.SSHClient:
    server = get_servers()[target]
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        hostname=server["host"],
        username=server["username"],
        password=server["password"],
        timeout=20,
        banner_timeout=120,
        auth_timeout=60,
    )
    return client


def make_tar(entries: list[tuple[Path, str]]) -> bytes:
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
        for local_path, arcname in entries:
            tar.add(local_path, arcname=arcname)
    buffer.seek(0)
    return buffer.read()


def upload_bytes(client: paramiko.SSHClient, remote_path: str, data: bytes) -> None:
    sftp = client.open_sftp()
    try:
        with sftp.file(remote_path, "wb") as handle:
            handle.write(data)
    finally:
        sftp.close()


def exec_script(client: paramiko.SSHClient, script: str, timeout: int = 300) -> tuple[int, str, str]:
    stdin, stdout, stderr = client.exec_command("bash -s", timeout=timeout)
    stdin.write(script)
    stdin.channel.shutdown_write()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    status = stdout.channel.recv_exit_status()
    return status, out, err


def build_backend() -> None:
    os.system(f'cd /d "{ROOT}" && mvn -q -DskipTests package')


def build_frontend() -> None:
    os.system(f'cd /d "{ROOT / "smart-exam-web"}" && npm run build')


def deploy_backend(post_command: str) -> None:
    entries: list[tuple[Path, str]] = [
        (ROOT / "docker-compose.yml", "docker-compose.yml"),
        (ROOT / ".env.example", ".env.example"),
        (ROOT / ".env.runtime.example", ".env.runtime.example"),
        (ROOT / "README.md", "README.md"),
    ]
    entries.extend(
        (path, f"scripts/deploy/{path.name}")
        for path in (ROOT / "scripts" / "deploy").iterdir()
        if path.is_file() and path.name != "servers.local.json"
    )
    entries.extend(
        (ROOT / "docs" / "nacos" / file_name, f"docs/nacos/{file_name}")
        for file_name in [
            "common.yaml",
            "gateway-service.yaml",
            "auth-service.yaml",
            "user-service.yaml",
            "question-service.yaml",
            "exam-service.yaml",
            "grading-service.yaml",
            "analysis-service.yaml",
            "admin-service.yaml",
            "README.md",
            "import-nacos.sh",
            "import-nacos.ps1",
        ]
    )
    entries.extend(
        (
            ROOT / "services" / service / "target" / f"{service}-0.1.0-SNAPSHOT.jar",
            f"services/{service}/target/{service}-0.1.0-SNAPSHOT.jar",
        )
        for service in BACKEND_JARS
    )
    payload = make_tar(entries)

    client = connect_client("backend")
    try:
        upload_bytes(client, "/root/smart-exam-backend-deploy.tgz", payload)
        command_block = post_command or 'echo "No backend post-command configured."'
        script = f"""
set -euo pipefail
mkdir -p /opt/smart-exam-cloud
tar -xzf /root/smart-exam-backend-deploy.tgz -C /opt/smart-exam-cloud
cd /opt/smart-exam-cloud
{command_block}
echo "Backend artifacts updated under /opt/smart-exam-cloud"
"""
        status, out, err = exec_script(client, script, timeout=1800)
        if status != 0:
            raise RuntimeError(err or out)
        print(out.strip())
    finally:
        client.close()


def deploy_frontend(post_command: str) -> None:
    payload = make_tar([(ROOT / "smart-exam-web" / "dist", "dist")])
    client = connect_client("frontend")
    try:
        upload_bytes(client, "/root/smart-exam-web-deploy.tgz", payload)
        command_block = post_command or "docker restart smart-exam-web >/dev/null"
        script = f"""
set -euo pipefail
mkdir -p /opt/smart-exam-web
tar -xzf /root/smart-exam-web-deploy.tgz -C /opt/smart-exam-web
cd /opt/smart-exam-web
{command_block}
echo "Frontend dist updated under /opt/smart-exam-web"
"""
        status, out, err = exec_script(client, script, timeout=600)
        if status != 0:
            raise RuntimeError(err or out)
        print(out.strip())
    finally:
        client.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("target", choices=["backend", "frontend", "both"])
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--backend-post-command", default="")
    parser.add_argument("--frontend-post-command", default="")
    args = parser.parse_args()

    if args.build and args.target in {"backend", "both"}:
        build_backend()
    if args.build and args.target in {"frontend", "both"}:
        build_frontend()

    if args.target in {"backend", "both"}:
        deploy_backend(args.backend_post_command)
    if args.target in {"frontend", "both"}:
        deploy_frontend(args.frontend_post_command)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
