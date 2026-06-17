import importlib.util
import json
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


def _load_module():
    module_path = Path(__file__).with_name("business_regression.py")
    spec = importlib.util.spec_from_file_location("business_regression", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class _RegressionHandler(BaseHTTPRequestHandler):
    def _read_payload(self):
        length = int(self.headers.get("Content-Length") or "0")
        if length <= 0:
            return None
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def _json(self, payload, status=200):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return

    def do_GET(self):
        if self.path == "/actuator/health":
            self._json({"status": "UP"})
            return
        if self.path == "/api/v1/users/me":
            token = self.headers.get("Authorization", "")
            role = "TEACHER" if "teacher-token" in token else "STUDENT"
            self._json({"code": 0, "message": "OK", "data": {"username": role.lower(), "role": role}})
            return
        if self.path.startswith("/api/v1/questions"):
            self._json({"code": 0, "message": "OK", "data": {"records": []}})
            return
        if self.path.startswith("/api/v1/papers"):
            self._json({"code": 0, "message": "OK", "data": {"records": []}})
            return
        if self.path == "/api/v1/exams/teachers/me":
            self._json({"code": 0, "message": "OK", "data": [{"examId": "exam-1"}]})
            return
        if self.path == "/api/v1/exams/students/me":
            self._json({"code": 0, "message": "OK", "data": [{"examId": "exam-1"}]})
            return
        if self.path.startswith("/api/v1/reports/exams/exam-1/score-distribution"):
            self._json({"code": 0, "message": "OK", "data": {"buckets": []}})
            return
        if self.path.startswith("/api/v1/reports/exams/exam-1/question-accuracy-top"):
            self._json({"code": 0, "message": "OK", "data": []})
            return
        if self.path.startswith("/api/v1/reports/exams/exam-1/score-sheet"):
            self._json({"code": 0, "message": "OK", "data": {"records": []}})
            return
        if self.path == "/api/v1/sessions/session-1/paper":
            self._json({"code": 0, "message": "OK", "data": {"paperId": "paper-1"}})
            return
        if self.path == "/api/v1/sessions/session-1/answers":
            self._json({"code": 0, "message": "OK", "data": []})
            return
        if self.path == "/api/v1/grading/sessions/session-1/result":
            self._json({"code": 0, "message": "OK", "data": {"ready": False}})
            return
        self._json({"code": 404, "message": f"Unhandled GET {self.path}"}, status=404)

    def do_POST(self):
        if self.path == "/api/v1/auth/login":
            payload = self._read_payload() or {}
            username = payload.get("username")
            if username == "teacher001":
                self._json({"code": 0, "message": "OK", "data": {"token": "teacher-token"}})
                return
            if username == "student001":
                self._json({"code": 0, "message": "OK", "data": {"token": "student-token"}})
                return
            self._json({"code": 40101, "message": "invalid credentials"}, status=401)
            return
        if self.path == "/api/v1/exams/exam-1/start":
            self._json({"code": 0, "message": "OK", "data": {"sessionId": "session-1"}})
            return
        if self.path == "/api/v1/sessions/session-1/submit":
            self._json({"code": 0, "message": "OK", "data": {"submitted": True}})
            return
        self._json({"code": 404, "message": f"Unhandled POST {self.path}"}, status=404)

    def do_PUT(self):
        if self.path == "/api/v1/sessions/session-1/answers":
            self._read_payload()
            self._json({"code": 0, "message": "OK", "data": {"saved": True}})
            return
        self._json({"code": 404, "message": f"Unhandled PUT {self.path}"}, status=404)


class BusinessRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = _load_module()
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), _RegressionHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.gateway = f"http://127.0.0.1:{cls.server.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def test_business_regression_generates_report(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            payload_path = Path(tmpdir) / "answers.json"
            report_path = Path(tmpdir) / "business-regression.json"
            payload_path.write_text(json.dumps({"answers": []}), encoding="utf-8")

            code = self.module.main(
                [
                    "--gateway",
                    self.gateway,
                    "--teacher-username",
                    "teacher001",
                    "--teacher-password",
                    "123456",
                    "--student-username",
                    "student001",
                    "--student-password",
                    "123456",
                    "--allow-mutation",
                    "--save-answers-json",
                    str(payload_path),
                    "--submit-session",
                    "--report-file",
                    str(report_path),
                ]
            )

            self.assertEqual(code, 0)
            report = json.loads(report_path.read_text(encoding="utf-8"))
            self.assertEqual(report["summary"]["failed"], 0)
            self.assertEqual(report["resolvedContext"]["teacherExamId"], "exam-1")
            self.assertEqual(report["resolvedContext"]["studentExamId"], "exam-1")
            self.assertEqual(report["resolvedContext"]["sessionId"], "session-1")
            self.assertGreaterEqual(report["summary"]["total"], 13)


if __name__ == "__main__":
    unittest.main()
