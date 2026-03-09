# Automation Tests

## Script
- `api_smoke_test.py`: Smart Exam Cloud API smoke/regression script (Python stdlib only).

## Quick Run
```bash
python automation-tests/api_smoke_test.py
```

## E2E Flow Run
```bash
# Teacher creates question/paper/exam, student starts session, saves answer, submits
python automation-tests/api_smoke_test.py --include e2e --fail-fast
```

## Common Options
```bash
# Only run auth + student related cases
python automation-tests/api_smoke_test.py --include auth,student

# Skip negative test cases
python automation-tests/api_smoke_test.py --exclude negative

# Stop at first failure
python automation-tests/api_smoke_test.py --fail-fast

# Output JSON report for CI artifacts
python automation-tests/api_smoke_test.py --report-json automation-tests/latest-report.json

# List all case ids and tags
python automation-tests/api_smoke_test.py --list-cases
```

## Environment Variables
- `SMART_EXAM_BASE_URL` (default: `http://localhost:9000`)
- `SMART_EXAM_ADMIN_USER` / `SMART_EXAM_ADMIN_PASS`
- `SMART_EXAM_TEACHER_USER` / `SMART_EXAM_TEACHER_PASS`
- `SMART_EXAM_STUDENT_USER` / `SMART_EXAM_STUDENT_PASS`

## Exit Code
- `0`: all selected cases passed
- `1`: one or more selected cases failed
