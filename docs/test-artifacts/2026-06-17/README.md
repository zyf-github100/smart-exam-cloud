# 2026-06-17 Test Execution Archive

## Scope

- Backend targeted JUnit reruns for `question-service`, `analysis-service`, and `admin-service`
- Web Vitest rerun, including new auth-expiry and route-guard exception-flow cases
- Miniapp static check and lightweight flow tests
- Python business regression script validation and fixture-based JSON report generation
- Mobile Flutter widget tests executed locally after the workstation was provisioned with Flutter SDK

## Visible automated assets after this update

- Python: 3 files
  - `scripts/deploy/smoke-check.py`
  - `scripts/deploy/business_regression.py`
  - `scripts/deploy/test_business_regression.py`
- Backend JUnit: 8 files / 22 `@Test`
- Web Vitest: 3 files / 8 cases
- Miniapp lightweight tests: 1 file / 5 cases
- Flutter widget tests: 2 files / 7 cases
- Python unittest: 1 case

## Executed commands

- `mvn test -Dtest=QuestionDomainServiceTest`
- `mvn test -Dtest=ReportDomainServiceTest`
- `mvn test -Dtest=AdminServiceTest`
- `python -m unittest scripts/deploy/test_business_regression.py`
- `npm run test:ci`
- `node scripts/ci/check-miniapp.js`
- `node scripts/ci/test-miniapp.js`
- `flutter test`
- Fixture run for `scripts/deploy/business_regression.py`, with JSON output written to `python-business-regression.json`

## Result summary

- `QuestionDomainServiceTest`: passed, 3 tests
- `ReportDomainServiceTest`: passed, 3 tests
- `AdminServiceTest`: passed, 3 tests
- Python business regression unittest: passed, 1 test
- Web Vitest: passed, 3 files / 8 cases
- Miniapp static check: passed, 11 JS files / 8 JSON files / 4 pages
- Miniapp lightweight tests: passed, 1 file / 5 cases
- Flutter widget tests: passed, 2 files / 7 cases

## Notes

- Maven logs contain ByteBuddy/JDK dynamic-agent warnings in PowerShell output, but each log includes `BUILD SUCCESS`.
- `python-business-regression.json` is a local fixture execution record used to verify the regression script and archive its step-by-step output shape.
- `mobile-flutter-blocked.txt` is retained only as an earlier environment note; the current valid Flutter execution record is `mobile-flutter-test.log`.
