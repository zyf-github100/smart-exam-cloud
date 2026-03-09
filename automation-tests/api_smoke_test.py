
#!/usr/bin/env python3
"""
Smart Exam Cloud API smoke test (Python stdlib only).

Usage:
  python automation-tests/api_smoke_test.py
  python automation-tests/api_smoke_test.py --base-url http://127.0.0.1:9000
  python automation-tests/api_smoke_test.py --include auth,student --fail-fast
  python automation-tests/api_smoke_test.py --report-json automation-tests/report.json
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime
from datetime import timedelta
from datetime import timezone
from typing import Any
from typing import Callable


@dataclass
class HttpResult:
    status: int
    body_text: str
    json_body: Any


@dataclass
class TestCase:
    case_id: str
    name: str
    tags: tuple[str, ...]
    handler: Callable[[], None]


@dataclass
class CaseResult:
    case_id: str
    name: str
    tags: tuple[str, ...]
    status: str
    duration_s: float
    error: str | None


class TestFailure(Exception):
    pass


def _trim(text: str, limit: int = 220) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 3] + "..."


def parse_selector(raw: str) -> set[str]:
    if not raw:
        return set()
    return {part.strip() for part in raw.split(",") if part.strip()}


def request_json(
    base_url: str,
    method: str,
    path: str,
    token: str | None = None,
    payload: dict[str, Any] | None = None,
    timeout: float = 8.0,
) -> HttpResult:
    url = base_url.rstrip("/") + path
    data = None
    headers: dict[str, str] = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url=url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.getcode()
            body_text = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as err:
        status = err.code
        body_text = err.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as err:
        raise TestFailure(f"request failed for {method} {url}: {err.reason}") from err

    parsed_json: Any = None
    try:
        parsed_json = json.loads(body_text)
    except json.JSONDecodeError:
        parsed_json = None

    return HttpResult(status=status, body_text=body_text, json_body=parsed_json)


class SmokeRunner:
    def __init__(self, args: argparse.Namespace) -> None:
        self.base_url = args.base_url.rstrip("/")
        self.timeout = args.timeout
        self.fail_fast = bool(args.fail_fast)
        self.report_json = args.report_json
        self.include = parse_selector(args.include)
        self.exclude = parse_selector(args.exclude)
        self.admin_user = args.admin_user
        self.admin_pass = args.admin_pass
        self.teacher_user = args.teacher_user
        self.teacher_pass = args.teacher_pass
        self.student_user = args.student_user
        self.student_pass = args.student_pass

        self.tokens: dict[str, str] = {}
        self.user_ids: dict[str, str] = {}
        self.results: list[CaseResult] = []
        self.accounts: dict[str, tuple[str, str, str]] = {
            "admin": (self.admin_user, self.admin_pass, "ADMIN"),
            "teacher": (self.teacher_user, self.teacher_pass, "TEACHER"),
            "student": (self.student_user, self.student_pass, "STUDENT"),
        }
        self.cases: list[TestCase] = [
            TestCase(
                "auth.anonymous_rejected",
                "anonymous access should be rejected",
                ("auth", "negative"),
                self.case_anonymous_rejected,
            ),
            TestCase(
                "auth.login",
                "login with admin/teacher/student",
                ("auth", "core"),
                self.case_login,
            ),
            TestCase(
                "auth.invalid_login_rejected",
                "invalid login should be rejected",
                ("auth", "negative"),
                self.case_invalid_login_rejected,
            ),
            TestCase(
                "auth.logout",
                "authenticated logout should succeed",
                ("auth",),
                self.case_logout,
            ),
            TestCase(
                "student.users_me",
                "student can access /users/me",
                ("student", "rbac"),
                self.case_student_users_me,
            ),
            TestCase(
                "student.questions_forbidden",
                "student cannot access /questions",
                ("student", "rbac", "negative"),
                self.case_student_cannot_list_questions,
            ),
            TestCase(
                "student.reports_forbidden",
                "student cannot access report endpoints",
                ("student", "rbac", "negative"),
                self.case_student_cannot_access_reports,
            ),
            TestCase(
                "student.exams_new_path",
                "student can list assigned exams (/exams/students/me)",
                ("student", "exam"),
                self.case_student_list_my_exams,
            ),
            TestCase(
                "student.exams_legacy_path",
                "student can list assigned exams (legacy path)",
                ("student", "exam", "compat"),
                self.case_student_list_my_exams_legacy,
            ),
            TestCase(
                "teacher.questions",
                "teacher can access /questions",
                ("teacher", "rbac"),
                self.case_teacher_list_questions,
            ),
            TestCase(
                "teacher.exams_me",
                "teacher can list published exams",
                ("teacher", "exam"),
                self.case_teacher_list_my_exams,
            ),
            TestCase(
                "teacher.users_list",
                "teacher can access /users",
                ("teacher", "rbac"),
                self.case_teacher_users_list,
            ),
            TestCase(
                "teacher.admin_overview_forbidden",
                "teacher cannot access /admin/overview",
                ("teacher", "rbac", "negative"),
                self.case_teacher_cannot_admin_overview,
            ),
            TestCase(
                "admin.overview",
                "admin can access /admin/overview",
                ("admin", "rbac"),
                self.case_admin_overview,
            ),
            TestCase(
                "admin.users",
                "admin can access /admin/users",
                ("admin", "rbac"),
                self.case_admin_users,
            ),
            TestCase(
                "e2e.teacher_create_exam_student_submit",
                "teacher creates exam and student completes session",
                ("e2e", "flow", "write"),
                self.case_e2e_teacher_create_exam_student_submit,
            ),
        ]

    def run_case(self, case: TestCase) -> None:
        print(f"[RUN ] {case.name} [{case.case_id}]")
        started = time.time()
        try:
            case.handler()
            elapsed = time.time() - started
            self.results.append(
                CaseResult(
                    case_id=case.case_id,
                    name=case.name,
                    tags=case.tags,
                    status="passed",
                    duration_s=elapsed,
                    error=None,
                )
            )
            print(f"[PASS] {case.name} ({elapsed:.2f}s)")
        except Exception as exc:
            elapsed = time.time() - started
            message = _trim(str(exc), 300)
            self.results.append(
                CaseResult(
                    case_id=case.case_id,
                    name=case.name,
                    tags=case.tags,
                    status="failed",
                    duration_s=elapsed,
                    error=message,
                )
            )
            print(f"[FAIL] {case.name} ({elapsed:.2f}s)")
            print(f"       {message}")

    def assert_true(self, condition: bool, message: str) -> None:
        if not condition:
            raise TestFailure(message)

    def assert_api_success(self, result: HttpResult, context: str) -> dict[str, Any]:
        self.assert_true(
            result.status == 200,
            f"{context}: expected HTTP 200, got {result.status}; body={_trim(result.body_text)}",
        )
        self.assert_true(
            isinstance(result.json_body, dict),
            f"{context}: response is not JSON object; body={_trim(result.body_text)}",
        )
        body = result.json_body
        self.assert_true(
            body.get("code") == 0,
            f"{context}: expected code=0, got code={body.get('code')}; body={_trim(result.body_text)}",
        )
        return body

    def api_data(self, result: HttpResult, context: str, allow_none: bool = False) -> Any:
        body = self.assert_api_success(result, context)
        data = body.get("data")
        if not allow_none:
            self.assert_true(data is not None, f"{context}: data is null")
        return data

    def assert_forbidden_or_unauthorized(self, result: HttpResult, context: str) -> None:
        if result.status in (401, 403):
            return
        if result.status == 200 and isinstance(result.json_body, dict):
            code = result.json_body.get("code")
            if code in (40100, 40300):
                return
        raise TestFailure(
            f"{context}: expected forbidden/unauthorized, got HTTP {result.status}; body={_trim(result.body_text)}"
        )

    def assert_conflict_or_forbidden_or_unauthorized(self, result: HttpResult, context: str) -> None:
        if result.status in (401, 403, 409):
            return
        if result.status == 200 and isinstance(result.json_body, dict):
            code = result.json_body.get("code")
            if code in (40100, 40300, 40900):
                return
        raise TestFailure(
            f"{context}: expected conflict/forbidden/unauthorized, got HTTP {result.status}; "
            f"body={_trim(result.body_text)}"
        )

    def assert_code_in(self, result: HttpResult, context: str, expected: set[int]) -> None:
        self.assert_true(
            isinstance(result.json_body, dict),
            f"{context}: response is not JSON object; body={_trim(result.body_text)}",
        )
        code = result.json_body.get("code")
        self.assert_true(
            isinstance(code, int) and code in expected,
            f"{context}: expected code in {sorted(expected)}, got {code}; body={_trim(result.body_text)}",
        )

    def login_and_store(self, alias: str, username: str, password: str, expected_role: str) -> None:
        result = request_json(
            self.base_url,
            method="POST",
            path="/api/v1/auth/login",
            payload={"username": username, "password": password},
            timeout=self.timeout,
        )
        data = self.api_data(result, f"login:{alias}")
        self.assert_true(isinstance(data, dict), f"login:{alias}: data is not object")
        token = data.get("token")
        self.assert_true(isinstance(token, str) and token.strip(), f"login:{alias}: token is missing")
        user = data.get("user")
        self.assert_true(isinstance(user, dict), f"login:{alias}: user payload is missing")
        actual_role = user.get("role")
        self.assert_true(
            actual_role == expected_role,
            f"login:{alias}: expected role={expected_role}, got role={actual_role}",
        )
        self.tokens[alias] = token

    def ensure_token(self, alias: str) -> str:
        token = self.tokens.get(alias)
        if token:
            return token
        account = self.accounts.get(alias)
        self.assert_true(account is not None, f"unknown account alias: {alias}")
        username, password, expected_role = account or ("", "", "")
        self.login_and_store(alias, username, password, expected_role)
        return self.tokens[alias]

    def ensure_user_id(self, alias: str) -> str:
        user_id = self.user_ids.get(alias)
        if user_id:
            return user_id

        token = self.ensure_token(alias)
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/users/me",
            token=token,
            timeout=self.timeout,
        )
        data = self.api_data(result, f"users/me:{alias}")
        self.assert_true(isinstance(data, dict), f"users/me:{alias}: data is not object")
        actual_id = data.get("id")
        self.assert_true(isinstance(actual_id, str) and actual_id.strip(), f"users/me:{alias}: id is missing")

        expected_role = self.accounts[alias][2]
        actual_role = data.get("role")
        self.assert_true(
            actual_role == expected_role,
            f"users/me:{alias}: expected role={expected_role}, got role={actual_role}",
        )
        self.user_ids[alias] = actual_id
        return actual_id

    def _fmt_dt(self, value: datetime) -> str:
        return value.strftime("%Y-%m-%d %H:%M:%S")

    def case_anonymous_rejected(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/users/me",
            timeout=self.timeout,
        )
        self.assert_true(
            result.status == 401,
            f"anonymous /users/me should be 401, got {result.status}; body={_trim(result.body_text)}",
        )
        if isinstance(result.json_body, dict):
            code = result.json_body.get("code")
            self.assert_true(code == 40100, f"anonymous /users/me expected code=40100, got {code}")

    def case_login(self) -> None:
        self.login_and_store("admin", self.admin_user, self.admin_pass, "ADMIN")
        self.login_and_store("teacher", self.teacher_user, self.teacher_pass, "TEACHER")
        self.login_and_store("student", self.student_user, self.student_pass, "STUDENT")

    def case_invalid_login_rejected(self) -> None:
        result = request_json(
            self.base_url,
            method="POST",
            path="/api/v1/auth/login",
            payload={"username": self.student_user, "password": "__wrong_password__"},
            timeout=self.timeout,
        )
        if result.status in (401, 403):
            return
        self.assert_true(
            result.status == 200,
            f"invalid login: expected HTTP 200/401/403, got {result.status}; body={_trim(result.body_text)}",
        )
        self.assert_code_in(result, "invalid login", {40100})

    def case_logout(self) -> None:
        result = request_json(
            self.base_url,
            method="POST",
            path="/api/v1/auth/logout",
            token=self.ensure_token("student"),
            timeout=self.timeout,
        )
        self.api_data(result, "student:/auth/logout", allow_none=True)

    def case_student_users_me(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/users/me",
            token=self.ensure_token("student"),
            timeout=self.timeout,
        )
        data = self.api_data(result, "student:/users/me")
        self.assert_true(isinstance(data, dict), "student:/users/me data is missing")
        self.assert_true(data.get("role") == "STUDENT", f"student:/users/me role mismatch: {data.get('role')}")

    def case_student_cannot_list_questions(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/questions",
            token=self.ensure_token("student"),
            timeout=self.timeout,
        )
        self.assert_forbidden_or_unauthorized(result, "student:/questions")

    def case_student_cannot_access_reports(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/reports/exams/1/score-distribution",
            token=self.ensure_token("student"),
            timeout=self.timeout,
        )
        self.assert_forbidden_or_unauthorized(result, "student:/reports/exams/1/score-distribution")

    def case_teacher_list_questions(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/questions",
            token=self.ensure_token("teacher"),
            timeout=self.timeout,
        )
        self.api_data(result, "teacher:/questions")

    def case_student_list_my_exams(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/exams/students/me",
            token=self.ensure_token("student"),
            timeout=self.timeout,
        )
        self.api_data(result, "student:/exams/students/me")

    def case_student_list_my_exams_legacy(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/students/me/exams",
            token=self.ensure_token("student"),
            timeout=self.timeout,
        )
        self.api_data(result, "student:/students/me/exams")

    def case_teacher_list_my_exams(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/exams/teachers/me",
            token=self.ensure_token("teacher"),
            timeout=self.timeout,
        )
        self.api_data(result, "teacher:/exams/teachers/me")

    def case_teacher_users_list(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/users",
            token=self.ensure_token("teacher"),
            timeout=self.timeout,
        )
        self.api_data(result, "teacher:/users")

    def case_admin_overview(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/admin/overview",
            token=self.ensure_token("admin"),
            timeout=self.timeout,
        )
        self.api_data(result, "admin:/admin/overview")

    def case_admin_users(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/admin/users?page=1&size=5",
            token=self.ensure_token("admin"),
            timeout=self.timeout,
        )
        self.api_data(result, "admin:/admin/users")

    def case_teacher_cannot_admin_overview(self) -> None:
        result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/admin/overview",
            token=self.ensure_token("teacher"),
            timeout=self.timeout,
        )
        self.assert_forbidden_or_unauthorized(result, "teacher:/admin/overview")

    def case_e2e_teacher_create_exam_student_submit(self) -> None:
        teacher_token = self.ensure_token("teacher")
        student_token = self.ensure_token("student")
        student_id = self.ensure_user_id("student")

        unique = f"{datetime.now().strftime('%Y%m%d%H%M%S')}-{int(time.time() * 1000) % 1000:03d}"

        question_payload = {
            "type": "SINGLE",
            "stem": f"[AUTO-E2E][{unique}] What is 2 + 2?",
            "options": [
                {"key": "A", "text": "4"},
                {"key": "B", "text": "5"},
            ],
            "answer": "A",
            "difficulty": 1,
            "knowledgePoint": "automation",
            "analysis": "2+2 equals 4",
        }
        create_question_result = request_json(
            self.base_url,
            method="POST",
            path="/api/v1/questions",
            token=teacher_token,
            payload=question_payload,
            timeout=self.timeout,
        )
        question_data = self.api_data(create_question_result, "e2e:create question")
        self.assert_true(isinstance(question_data, dict), "e2e:create question data is not object")
        question_id = question_data.get("id")
        self.assert_true(isinstance(question_id, str) and question_id.strip(), "e2e:create question id is missing")

        paper_payload = {
            "name": f"[AUTO-E2E][{unique}] Paper",
            "timeLimitMinutes": 30,
            "questions": [
                {
                    "questionId": question_id,
                    "score": 5,
                    "orderNo": 1,
                }
            ],
        }
        create_paper_result = request_json(
            self.base_url,
            method="POST",
            path="/api/v1/papers",
            token=teacher_token,
            payload=paper_payload,
            timeout=self.timeout,
        )
        paper_data = self.api_data(create_paper_result, "e2e:create paper")
        self.assert_true(isinstance(paper_data, dict), "e2e:create paper data is not object")
        paper_id = paper_data.get("id")
        self.assert_true(isinstance(paper_id, str) and paper_id.strip(), "e2e:create paper id is missing")

        now = datetime.now()
        exam_payload = {
            "paperId": paper_id,
            "title": f"[AUTO-E2E][{unique}] Exam",
            "startTime": self._fmt_dt(now - timedelta(minutes=2)),
            "endTime": self._fmt_dt(now + timedelta(minutes=30)),
            "antiCheatLevel": 1,
            "studentIds": [student_id],
        }
        create_exam_result = request_json(
            self.base_url,
            method="POST",
            path="/api/v1/exams",
            token=teacher_token,
            payload=exam_payload,
            timeout=self.timeout,
        )
        exam_data = self.api_data(create_exam_result, "e2e:create exam")
        self.assert_true(isinstance(exam_data, dict), "e2e:create exam data is not object")
        exam_id = exam_data.get("id")
        self.assert_true(isinstance(exam_id, str) and exam_id.strip(), "e2e:create exam id is missing")

        list_exams_result = request_json(
            self.base_url,
            method="GET",
            path="/api/v1/exams/students/me",
            token=student_token,
            timeout=self.timeout,
        )
        assigned_exams = self.api_data(list_exams_result, "e2e:student list exams")
        self.assert_true(isinstance(assigned_exams, list), "e2e:student list exams should return list")
        matched_exam = any(
            isinstance(item, dict) and str(item.get("examId")) == str(exam_id)
            for item in assigned_exams
        )
        self.assert_true(matched_exam, f"e2e: newly created exam not found in student list, examId={exam_id}")

        start_result = request_json(
            self.base_url,
            method="POST",
            path=f"/api/v1/exams/{exam_id}/start",
            token=student_token,
            timeout=self.timeout,
        )
        start_data = self.api_data(start_result, "e2e:start exam")
        self.assert_true(isinstance(start_data, dict), "e2e:start exam data is not object")
        session_id = start_data.get("sessionId")
        self.assert_true(isinstance(session_id, str) and session_id.strip(), "e2e:start exam sessionId is missing")

        paper_result = request_json(
            self.base_url,
            method="GET",
            path=f"/api/v1/sessions/{session_id}/paper",
            token=student_token,
            timeout=self.timeout,
        )
        session_paper = self.api_data(paper_result, "e2e:get session paper")
        self.assert_true(isinstance(session_paper, dict), "e2e:get session paper data is not object")
        questions = session_paper.get("questions")
        self.assert_true(isinstance(questions, list) and len(questions) > 0, "e2e:session paper questions are empty")

        selected_question = None
        for item in questions:
            if isinstance(item, dict) and str(item.get("questionId")) == str(question_id):
                selected_question = item
                break
        if selected_question is None:
            selected_question = questions[0]

        self.assert_true(isinstance(selected_question, dict), "e2e: selected question is invalid")
        selected_question_id = selected_question.get("questionId")
        self.assert_true(
            isinstance(selected_question_id, str) and selected_question_id.strip(),
            "e2e:selected question id is missing",
        )
        question_type = str(selected_question.get("type", "")).upper()
        self.assert_true(question_type == "SINGLE", f"e2e: expected SINGLE question, got {question_type}")

        save_answers_payload = {
            "answers": [
                {
                    "questionId": selected_question_id,
                    "answerContent": "A",
                    "markedForReview": False,
                }
            ]
        }
        save_answers_result = request_json(
            self.base_url,
            method="PUT",
            path=f"/api/v1/sessions/{session_id}/answers",
            token=student_token,
            payload=save_answers_payload,
            timeout=self.timeout,
        )
        self.api_data(save_answers_result, "e2e:save answers", allow_none=True)

        get_answers_result = request_json(
            self.base_url,
            method="GET",
            path=f"/api/v1/sessions/{session_id}/answers",
            token=student_token,
            timeout=self.timeout,
        )
        saved_answers = self.api_data(get_answers_result, "e2e:get saved answers")
        self.assert_true(isinstance(saved_answers, list), "e2e:get saved answers should return list")
        found_answer = any(
            isinstance(item, dict) and str(item.get("questionId")) == str(selected_question_id)
            for item in saved_answers
        )
        self.assert_true(found_answer, "e2e: saved answer was not found")

        anti_cheat_result = request_json(
            self.base_url,
            method="POST",
            path=f"/api/v1/sessions/{session_id}/anti-cheat/events",
            token=student_token,
            payload={"eventType": "SWITCH_SCREEN", "metadata": {"times": 1}},
            timeout=self.timeout,
        )
        self.api_data(anti_cheat_result, "e2e:report anti-cheat event")

        risk_result = request_json(
            self.base_url,
            method="GET",
            path=f"/api/v1/sessions/{session_id}/anti-cheat/risk",
            token=teacher_token,
            timeout=self.timeout,
        )
        risk_data = self.api_data(risk_result, "e2e:teacher get session risk")
        self.assert_true(isinstance(risk_data, dict), "e2e:teacher get session risk data is not object")

        submit_result = request_json(
            self.base_url,
            method="POST",
            path=f"/api/v1/sessions/{session_id}/submit",
            token=student_token,
            timeout=self.timeout,
        )
        submit_data = self.api_data(submit_result, "e2e:submit session")
        self.assert_true(isinstance(submit_data, dict), "e2e:submit session data is not object")
        self.assert_true(submit_data.get("status") == "SUBMITTED", f"e2e: expected SUBMITTED, got {submit_data}")

        restart_result = request_json(
            self.base_url,
            method="POST",
            path=f"/api/v1/exams/{exam_id}/start",
            token=student_token,
            timeout=self.timeout,
        )
        self.assert_conflict_or_forbidden_or_unauthorized(restart_result, "e2e:restart after submit")

    def _selected_cases(self) -> list[TestCase]:
        return [case for case in self.cases if self._is_case_selected(case)]

    def _is_case_selected(self, case: TestCase) -> bool:
        keys = {case.case_id, *case.tags}
        if self.include and not (keys & self.include):
            return False
        if self.exclude and (keys & self.exclude):
            return False
        return True

    def print_cases(self) -> None:
        for case in self.cases:
            print(f"{case.case_id} | tags={','.join(case.tags)} | {case.name}")

    def _write_report(self, started_at: float, finished_at: float, selected_total: int) -> None:
        if not self.report_json:
            return
        report_path = os.path.abspath(self.report_json)
        parent = os.path.dirname(report_path)
        if parent:
            os.makedirs(parent, exist_ok=True)

        passed = sum(1 for result in self.results if result.status == "passed")
        failed = sum(1 for result in self.results if result.status == "failed")
        report = {
            "baseUrl": self.base_url,
            "startedAt": datetime.fromtimestamp(started_at, tz=timezone.utc).isoformat(),
            "finishedAt": datetime.fromtimestamp(finished_at, tz=timezone.utc).isoformat(),
            "durationSeconds": round(finished_at - started_at, 3),
            "summary": {
                "selected": selected_total,
                "executed": len(self.results),
                "passed": passed,
                "failed": failed,
            },
            "cases": [
                {
                    "id": result.case_id,
                    "name": result.name,
                    "tags": list(result.tags),
                    "status": result.status,
                    "durationSeconds": round(result.duration_s, 3),
                    "error": result.error,
                }
                for result in self.results
            ],
        }
        with open(report_path, "w", encoding="utf-8") as file_obj:
            json.dump(report, file_obj, ensure_ascii=False, indent=2)
        print(f"JSON report written: {report_path}")

    def execute(self) -> bool:
        selected = self._selected_cases()
        print(f"Base URL: {self.base_url}")
        print(
            f"Selected cases: {len(selected)}/{len(self.cases)}"
            + (
                f" (include={sorted(self.include)}, exclude={sorted(self.exclude)})"
                if self.include or self.exclude
                else ""
            )
        )
        if not selected:
            print("No test cases selected. Use --list-cases to inspect available ids/tags.")
            return False

        started_at = time.time()
        for case in selected:
            self.run_case(case)
            if self.fail_fast and self.results and self.results[-1].status == "failed":
                print("Fail-fast enabled, stop after first failure.")
                break
        finished_at = time.time()

        passed = sum(1 for result in self.results if result.status == "passed")
        failed = sum(1 for result in self.results if result.status == "failed")
        executed = len(self.results)
        skipped = len(selected) - executed
        print(
            f"\nSummary: {passed} passed, {failed} failed, {executed} executed, "
            f"{skipped} skipped, {len(self.cases)} total defined"
        )

        self._write_report(started_at, finished_at, len(selected))
        return failed == 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Smart Exam Cloud API smoke test script")
    parser.add_argument(
        "--base-url",
        default=os.getenv("SMART_EXAM_BASE_URL", "http://localhost:9000"),
        help="Gateway base URL, default http://localhost:9000 or env SMART_EXAM_BASE_URL",
    )
    parser.add_argument("--timeout", type=float, default=8.0, help="HTTP timeout in seconds")
    parser.add_argument(
        "--include",
        default="",
        help="Run only case ids/tags (comma separated), e.g. auth,student.exams_new_path",
    )
    parser.add_argument(
        "--exclude",
        default="",
        help="Skip case ids/tags (comma separated), e.g. negative,admin",
    )
    parser.add_argument("--fail-fast", action="store_true", help="Stop after first failed case")
    parser.add_argument("--report-json", default="", help="Write execution report JSON to this path")
    parser.add_argument("--list-cases", action="store_true", help="List all case ids/tags and exit")
    parser.add_argument("--admin-user", default=os.getenv("SMART_EXAM_ADMIN_USER", "admin"))
    parser.add_argument("--admin-pass", default=os.getenv("SMART_EXAM_ADMIN_PASS", "123456"))
    parser.add_argument("--teacher-user", default=os.getenv("SMART_EXAM_TEACHER_USER", "teacher1"))
    parser.add_argument("--teacher-pass", default=os.getenv("SMART_EXAM_TEACHER_PASS", "123456"))
    parser.add_argument("--student-user", default=os.getenv("SMART_EXAM_STUDENT_USER", "stu1"))
    parser.add_argument("--student-pass", default=os.getenv("SMART_EXAM_STUDENT_PASS", "123456"))
    return parser


def main() -> int:
    args = build_parser().parse_args()
    runner = SmokeRunner(args)
    if args.list_cases:
        runner.print_cases()
        return 0
    return 0 if runner.execute() else 1


if __name__ == "__main__":
    sys.exit(main())
