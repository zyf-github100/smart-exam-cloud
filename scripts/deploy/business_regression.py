import argparse
import json
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any
from urllib import error, parse, request


@dataclass
class StepResult:
    name: str
    method: str
    path: str
    success: bool
    status: str
    duration_ms: int
    detail: str
    http_status: int | None = None
    app_code: int | None = None


class RegressionError(RuntimeError):
    pass


def _encode_query(params: dict[str, Any] | None) -> str:
    if not params:
        return ""
    filtered = {key: value for key, value in params.items() if value is not None}
    if not filtered:
        return ""
    return "?" + parse.urlencode(filtered, doseq=True)


def _json_headers(token: str | None = None) -> dict[str, str]:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def _decode_json(raw: bytes) -> Any:
    return json.loads(raw.decode("utf-8"))


class RegressionRunner:
    def __init__(self, gateway: str, timeout: int) -> None:
        self.gateway = gateway.rstrip("/")
        self.timeout = timeout
        self.steps: list[StepResult] = []

    def _build_url(self, path: str, params: dict[str, Any] | None = None) -> str:
        return f"{self.gateway}{path}{_encode_query(params)}"

    def _request_json(
        self,
        method: str,
        path: str,
        *,
        token: str | None = None,
        payload: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
    ) -> tuple[Any, int]:
        body = None
        headers = _json_headers(token)
        if payload is not None:
            body = json.dumps(payload).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = request.Request(
            self._build_url(path, params=params),
            data=body,
            headers=headers,
            method=method,
        )
        with request.urlopen(req, timeout=self.timeout) as resp:
            return _decode_json(resp.read()), resp.status

    def run_step(
        self,
        name: str,
        method: str,
        path: str,
        *,
        token: str | None = None,
        payload: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
        success_codes: set[int] | None = None,
    ) -> Any:
        started = time.perf_counter()
        try:
            response, http_status = self._request_json(
                method,
                path,
                token=token,
                payload=payload,
                params=params,
            )
            duration_ms = int((time.perf_counter() - started) * 1000)
            if isinstance(response, dict) and "code" in response:
                app_code = int(response.get("code", -1))
                expected = success_codes or {0}
                success = app_code in expected
                detail = str(response.get("message") or "OK")
                if not success:
                    raise RegressionError(f"Business code {app_code}: {detail}")
            else:
                app_code = None
                success = True
                detail = "OK"
            self.steps.append(
                StepResult(
                    name=name,
                    method=method,
                    path=path,
                    success=success,
                    status="passed",
                    duration_ms=duration_ms,
                    detail=detail,
                    http_status=http_status,
                    app_code=app_code,
                )
            )
            return response
        except Exception as exc:
            duration_ms = int((time.perf_counter() - started) * 1000)
            http_status = None
            app_code = None
            detail = str(exc)
            if isinstance(exc, error.HTTPError):
                http_status = exc.code
                try:
                    body = _decode_json(exc.read())
                except Exception:
                    body = None
                if isinstance(body, dict):
                    app_code = body.get("code")
                    detail = str(body.get("message") or body)
                elif body is not None:
                    detail = str(body)
            self.steps.append(
                StepResult(
                    name=name,
                    method=method,
                    path=path,
                    success=False,
                    status="failed",
                    duration_ms=duration_ms,
                    detail=detail,
                    http_status=http_status,
                    app_code=app_code if isinstance(app_code, int) else None,
                )
            )
            raise

    def login(self, username: str, password: str, label: str) -> str:
        response = self.run_step(
            f"{label}-login",
            "POST",
            "/api/v1/auth/login",
            payload={"username": username, "password": password},
        )
        token = response.get("data", {}).get("token")
        if not token:
            raise RegressionError(f"{label} login did not return token")
        return str(token)


def _pick_exam_id(items: list[dict[str, Any]]) -> str | None:
    for item in items:
        exam_id = str(item.get("examId") or "").strip()
        if exam_id:
            return exam_id
    return None


def _pick_session_id(items: list[dict[str, Any]]) -> str | None:
    for item in items:
        session_id = str(item.get("sessionId") or "").strip()
        if session_id:
            return session_id
    return None


def _load_optional_json(path: str | None) -> dict[str, Any] | None:
    if not path:
        return None
    with open(path, "r", encoding="utf-8-sig") as fp:
        payload = json.load(fp)
    if not isinstance(payload, dict):
        raise RegressionError("Answer payload must be a JSON object")
    return payload


def _ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def _run_teacher_chain(
    runner: RegressionRunner,
    *,
    username: str | None,
    password: str | None,
    exam_id: str | None,
) -> str | None:
    if not username or not password:
        return None

    token = runner.login(username, password, "teacher")
    runner.run_step("teacher-me", "GET", "/api/v1/users/me", token=token)
    runner.run_step(
        "teacher-questions",
        "GET",
        "/api/v1/questions",
        token=token,
        params={"pageNum": 1, "pageSize": 5},
    )
    runner.run_step(
        "teacher-papers",
        "GET",
        "/api/v1/papers",
        token=token,
        params={"pageNum": 1, "pageSize": 5},
    )
    exams_resp = runner.run_step(
        "teacher-exams",
        "GET",
        "/api/v1/exams/teachers/me",
        token=token,
    )
    teacher_exam_id = exam_id
    exams = exams_resp.get("data", []) if isinstance(exams_resp, dict) else []
    if not teacher_exam_id and isinstance(exams, list):
        teacher_exam_id = _pick_exam_id([item for item in exams if isinstance(item, dict)])

    if teacher_exam_id:
        runner.run_step(
            "report-score-distribution",
            "GET",
            f"/api/v1/reports/exams/{teacher_exam_id}/score-distribution",
            token=token,
        )
        runner.run_step(
            "report-question-accuracy-top",
            "GET",
            f"/api/v1/reports/exams/{teacher_exam_id}/question-accuracy-top",
            token=token,
            params={"top": 10},
        )
        runner.run_step(
            "report-score-sheet",
            "GET",
            f"/api/v1/reports/exams/{teacher_exam_id}/score-sheet",
            token=token,
            params={"pageNum": 1, "pageSize": 20},
        )
    return teacher_exam_id


def _run_student_chain(
    runner: RegressionRunner,
    *,
    username: str | None,
    password: str | None,
    exam_id: str | None,
    session_id: str | None,
    allow_mutation: bool,
    save_answers_payload: dict[str, Any] | None,
    submit_session: bool,
) -> tuple[str | None, str | None]:
    if not username or not password:
        return exam_id, session_id

    token = runner.login(username, password, "student")
    runner.run_step("student-me", "GET", "/api/v1/users/me", token=token)
    exams_resp = runner.run_step(
        "student-exams",
        "GET",
        "/api/v1/exams/students/me",
        token=token,
    )

    exams = exams_resp.get("data", []) if isinstance(exams_resp, dict) else []
    if isinstance(exams, list):
        exam_id = exam_id or _pick_exam_id([item for item in exams if isinstance(item, dict)])
        session_id = session_id or _pick_session_id([item for item in exams if isinstance(item, dict)])

    if allow_mutation and not session_id and exam_id:
        start_resp = runner.run_step(
            "student-start-exam",
            "POST",
            f"/api/v1/exams/{exam_id}/start",
            token=token,
        )
        session_id = str(start_resp.get("data", {}).get("sessionId") or "").strip() or session_id

    if session_id:
        runner.run_step(
            "student-session-paper",
            "GET",
            f"/api/v1/sessions/{session_id}/paper",
            token=token,
        )
        runner.run_step(
            "student-session-answers",
            "GET",
            f"/api/v1/sessions/{session_id}/answers",
            token=token,
        )
        if allow_mutation and save_answers_payload is not None:
            runner.run_step(
                "student-save-answers",
                "PUT",
                f"/api/v1/sessions/{session_id}/answers",
                token=token,
                payload=save_answers_payload,
            )
        if allow_mutation and submit_session:
            runner.run_step(
                "student-submit-session",
                "POST",
                f"/api/v1/sessions/{session_id}/submit",
                token=token,
            )
        runner.run_step(
            "student-session-result",
            "GET",
            f"/api/v1/grading/sessions/{session_id}/result",
            token=token,
        )

    return exam_id, session_id


def _build_report(
    runner: RegressionRunner,
    *,
    teacher_exam_id: str | None,
    student_exam_id: str | None,
    session_id: str | None,
) -> dict[str, Any]:
    passed = sum(1 for step in runner.steps if step.success)
    failed = sum(1 for step in runner.steps if not step.success)
    return {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "gateway": runner.gateway,
        "summary": {
            "total": len(runner.steps),
            "passed": passed,
            "failed": failed,
        },
        "resolvedContext": {
            "teacherExamId": teacher_exam_id,
            "studentExamId": student_exam_id,
            "sessionId": session_id,
        },
        "steps": [asdict(step) for step in runner.steps],
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", required=True, help="Gateway base URL, for example http://127.0.0.1:9000")
    parser.add_argument("--timeout", type=int, default=20)
    parser.add_argument("--teacher-username")
    parser.add_argument("--teacher-password")
    parser.add_argument("--student-username")
    parser.add_argument("--student-password")
    parser.add_argument("--teacher-exam-id")
    parser.add_argument("--student-exam-id")
    parser.add_argument("--session-id")
    parser.add_argument("--allow-mutation", action="store_true")
    parser.add_argument("--save-answers-json", help="Optional JSON payload file for PUT /sessions/{id}/answers")
    parser.add_argument("--submit-session", action="store_true")
    parser.add_argument("--report-file", help="Optional JSON report output path")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    runner = RegressionRunner(args.gateway, args.timeout)
    save_answers_payload = _load_optional_json(args.save_answers_json)

    runner.run_step("gateway-health", "GET", "/actuator/health")
    teacher_exam_id = _run_teacher_chain(
        runner,
        username=args.teacher_username,
        password=args.teacher_password,
        exam_id=args.teacher_exam_id,
    )
    student_exam_id, session_id = _run_student_chain(
        runner,
        username=args.student_username,
        password=args.student_password,
        exam_id=args.student_exam_id,
        session_id=args.session_id,
        allow_mutation=args.allow_mutation,
        save_answers_payload=save_answers_payload,
        submit_session=args.submit_session,
    )

    report = _build_report(
        runner,
        teacher_exam_id=teacher_exam_id,
        student_exam_id=student_exam_id,
        session_id=session_id,
    )
    if args.report_file:
        output = Path(args.report_file)
        _ensure_parent(output)
        output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    for step in runner.steps:
        print(f"[{step.status.upper()}] {step.name} {step.method} {step.path} ({step.duration_ms}ms) {step.detail}")

    return 0 if report["summary"]["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
