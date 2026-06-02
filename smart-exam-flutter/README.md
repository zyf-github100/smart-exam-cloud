# smart-exam-flutter

Flutter mobile app for `smart-exam-cloud`.

## Purpose

This directory is separate from the existing `smart-exam-miniapp` module.
It is intended for the native mobile app implementation using Flutter.

## Run

```bash
cd smart-exam-flutter
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:9000/api/v1
```

## Current scope

- Login page with local session restore
- Student profile fetch from `/users/me`
- Assigned exam list from `/exams/students/me`
- Start or resume session through `/exams/{examId}/start`
- Pre-exam network/status/battery/screen-safety/rules check before entering the answer page
- Paper fetch, answer restore, auto-save, and final submission
- Local answer draft recovery with conflict prompts for unsynced edits
- Student result and question analysis view from `/grading/sessions/{sessionId}/result`
- Lifecycle anti-cheat event reporting through `/sessions/{sessionId}/anti-cheat/events`
- Network disconnect detection, offline answer prompts, and delayed anti-cheat event flush
- Android screenshot and screen-recording preview blocking through `FLAG_SECURE`
- Visual dashboard, exams, results, and profile tabs

## Demo account

If you imported the default seed data, you can sign in with:

- Username: `student001`
- Password: `123456`

## API base URL

The app reads the backend gateway address from `API_BASE_URL`.

- Android emulator: `http://10.0.2.2:9000/api/v1`
- iOS simulator: `http://localhost:9000/api/v1`
- Real device: `http://<your-lan-ip>:9000/api/v1`

## Next steps

1. Add richer device capability checks such as battery and screen security state.
2. Add richer result states for manual grading and release timing.
3. Add mobile UI integration tests for the exam session flow.
4. Add iOS-specific screen recording and screen capture risk detection.
