import 'dart:async';

import 'package:battery_plus_platform_interface/battery_plus_platform_interface.dart';
import 'package:connectivity_plus_platform_interface/connectivity_plus_platform_interface.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:smart_exam_flutter/src/config/app_config.dart';
import 'package:smart_exam_flutter/src/core/storage/answer_draft_storage.dart';
import 'package:smart_exam_flutter/src/features/app/smart_exam_controller.dart';
import 'package:smart_exam_flutter/src/features/exams/exam_precheck_page.dart';
import 'package:smart_exam_flutter/src/features/exams/exam_result_page.dart';
import 'package:smart_exam_flutter/src/features/exams/exam_session_page.dart';
import 'package:smart_exam_flutter/src/features/exams/models/assigned_exam.dart';
import 'package:smart_exam_flutter/src/features/exams/models/exam_session_models.dart';
import 'package:smart_exam_flutter/src/features/exams/models/student_result_models.dart';

void main() {
  testWidgets('precheck page requires network and rule confirmation', (
    tester,
  ) async {
    final fakeConnectivity = _installConnectivity([ConnectivityResult.wifi]);
    final fakeBattery = _installBattery(
      level: 86,
      state: BatteryState.charging,
      saveMode: false,
    );
    addTearDown(fakeConnectivity.dispose);
    addTearDown(fakeBattery.dispose);

    bool? approved;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () async {
                    approved = await Navigator.of(context).push<bool>(
                      MaterialPageRoute(
                        builder: (_) => ExamPrecheckPage(exam: _runningExam()),
                      ),
                    );
                  },
                  child: const Text('打开检查页'),
                ),
              ),
            );
          },
        ),
      ),
    );

    await tester.tap(find.text('打开检查页'));
    await tester.pumpAndSettle();

    expect(find.text('考前检查'), findsOneWidget);
    expect(find.textContaining('当前网络可用'), findsOneWidget);
    expect(find.textContaining('当前电量 86%'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -600));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(Checkbox));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, '开始考试'));
    await tester.pumpAndSettle();

    expect(approved, isTrue);
  });

  testWidgets('exam session prompts when local draft conflicts with server', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final fakeConnectivity = _installConnectivity([ConnectivityResult.wifi]);
    addTearDown(fakeConnectivity.dispose);

    final serverUpdatedAt = DateTime(2026, 4, 24, 10, 10);
    await AnswerDraftStorage().write(
      LocalAnswerDraftSnapshot(
        sessionId: 'session-1',
        examId: 'exam-1',
        paperId: 'paper-1',
        currentQuestionId: 'q1',
        updatedAt: DateTime(2026, 4, 24, 10, 20),
        serverAnswersUpdatedAt: DateTime(2026, 4, 24, 10, 0),
        drafts: const [AnswerDraft(questionId: 'q1', answerContent: 'B')],
      ),
    );

    final controller = _FakeSmartExamController(
      paper: _paper(),
      answers: [
        SessionAnswerModel(
          questionId: 'q1',
          answerContent: 'A',
          markedForReview: false,
          updatedAt: serverUpdatedAt,
        ),
      ],
    );

    await tester.pumpWidget(
      MaterialApp(
        home: ExamSessionPage(
          controller: controller,
          exam: _runningExam(sessionId: 'session-1'),
          sessionId: 'session-1',
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.text('发现答案版本冲突'), findsOneWidget);

    await tester.tap(find.text('恢复本地草稿'));
    await tester.pumpAndSettle();

    expect(find.text('已恢复本地草稿，等待同步'), findsWidgets);
  });

  testWidgets('result page renders ready summary and question details', (
    tester,
  ) async {
    final controller = _FakeSmartExamController(
      result: StudentSessionResult(
        sessionId: 'session-1',
        examId: 'exam-1',
        sessionStatus: 'SUBMITTED',
        submittedAt: DateTime(2026, 4, 24, 10, 30),
        detailReleased: true,
        detailMessage: '',
        ready: true,
        taskStatus: 'DONE',
        message: '',
        summary: ResultSummary(
          objectiveScore: 8,
          subjectiveScore: 0,
          totalScore: 8,
          publishedAt: DateTime(2026, 4, 24, 10, 35),
        ),
        questions: const [
          QuestionResult(
            questionId: 'q1',
            orderNo: 1,
            type: 'SINGLE',
            stem: 'Java 中哪个关键字用于继承？',
            analysis: 'Java 使用 extends 表示继承。',
            options: [
              QuestionResultOption(key: 'A', text: 'extends'),
              QuestionResultOption(key: 'B', text: 'implements'),
            ],
            standardAnswer: 'A',
            myAnswer: 'A',
            maxScore: 10,
            gotScore: 8,
            objective: true,
            correct: false,
          ),
        ],
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: ExamResultPage(
          controller: controller,
          exam: _runningExam(sessionId: 'session-1'),
          sessionId: 'session-1',
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('成绩总览'), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, -600));
    await tester.pumpAndSettle();

    expect(find.text('题目明细'), findsOneWidget);
    expect(find.text('Java 中哪个关键字用于继承？'), findsOneWidget);
    expect(find.text('解析'), findsOneWidget);
  });
}

_FakeConnectivityPlatform _installConnectivity(
  List<ConnectivityResult> initialResults,
) {
  final platform = _FakeConnectivityPlatform(initialResults);
  ConnectivityPlatform.instance = platform;
  return platform;
}

AssignedExam _runningExam({String sessionId = ''}) {
  return AssignedExam(
    examId: 'exam-1',
    paperId: 'paper-1',
    title: 'Java 基础测试',
    startTime: DateTime(2026, 4, 24, 9),
    endTime: DateTime(2026, 4, 24, 12),
    antiCheatLevel: 2,
    status: 'RUNNING',
    sessionId: sessionId,
    sessionStatus: sessionId.isEmpty ? '' : 'IN_PROGRESS',
    sessionStartTime: sessionId.isEmpty ? null : DateTime(2026, 4, 24, 9, 30),
    sessionSubmitTime: null,
  );
}

ExamPaperModel _paper() {
  return const ExamPaperModel(
    sessionId: 'session-1',
    examId: 'exam-1',
    paperId: 'paper-1',
    paperName: 'Java 基础试卷',
    totalScore: 10,
    timeLimitMinutes: 60,
    questions: [
      ExamPaperQuestionModel(
        questionId: 'q1',
        type: 'SINGLE',
        stem: 'Java 中哪个关键字用于继承？',
        score: 10,
        orderNo: 1,
        options: [
          ExamPaperOptionModel(key: 'A', text: 'extends'),
          ExamPaperOptionModel(key: 'B', text: 'implements'),
        ],
      ),
    ],
  );
}

class _FakeSmartExamController extends SmartExamController {
  _FakeSmartExamController({
    ExamPaperModel? paper,
    List<SessionAnswerModel>? answers,
    StudentSessionResult? result,
  }) : paper = paper ?? _paper(),
       answers = answers ?? const [],
       result = result ?? _notReadyResult(),
       super(
         config: const AppConfig(
           appName: 'test',
           apiBaseUrl: 'http://localhost.test/api/v1',
         ),
       );

  final ExamPaperModel paper;
  final List<SessionAnswerModel> answers;
  final StudentSessionResult result;
  List<AnswerDraft> savedAnswers = const [];

  @override
  Future<ExamPaperModel> fetchSessionPaper(String sessionId) async => paper;

  @override
  Future<List<SessionAnswerModel>> fetchSessionAnswers(
    String sessionId,
  ) async => answers;

  @override
  Future<void> saveAnswers(String sessionId, List<AnswerDraft> answers) async {
    savedAnswers = answers;
  }

  @override
  Future<void> reportAntiCheatEvent(
    String sessionId, {
    required String eventType,
    Map<String, Object?> metadata = const {},
  }) async {}

  @override
  Future<StudentSessionResult> fetchStudentSessionResult(
    String sessionId,
  ) async => result;
}

StudentSessionResult _notReadyResult() {
  return const StudentSessionResult(
    sessionId: 'session-1',
    examId: 'exam-1',
    sessionStatus: 'SUBMITTED',
    submittedAt: null,
    detailReleased: false,
    detailMessage: '',
    ready: false,
    taskStatus: 'PENDING',
    message: '成绩正在评阅中',
    summary: ResultSummary(
      objectiveScore: null,
      subjectiveScore: null,
      totalScore: null,
      publishedAt: null,
    ),
    questions: [],
  );
}

class _FakeConnectivityPlatform extends ConnectivityPlatform {
  _FakeConnectivityPlatform(this.results);

  final _controller = StreamController<List<ConnectivityResult>>.broadcast();
  List<ConnectivityResult> results;

  @override
  Future<List<ConnectivityResult>> checkConnectivity() async => results;

  @override
  Stream<List<ConnectivityResult>> get onConnectivityChanged =>
      _controller.stream;

  void dispose() {
    _controller.close();
  }
}

_FakeBatteryPlatform _installBattery({
  required int level,
  required BatteryState state,
  required bool saveMode,
}) {
  final platform = _FakeBatteryPlatform(
    level: level,
    state: state,
    saveMode: saveMode,
  );
  BatteryPlatform.instance = platform;
  return platform;
}

class _FakeBatteryPlatform extends BatteryPlatform {
  _FakeBatteryPlatform({
    required this.level,
    required this.state,
    required this.saveMode,
  });

  final _controller = StreamController<BatteryState>.broadcast();
  int level;
  BatteryState state;
  bool saveMode;

  @override
  Future<int> get batteryLevel async => level;

  @override
  Future<BatteryState> get batteryState async => state;

  @override
  Future<bool> get isInBatterySaveMode async => saveMode;

  @override
  Stream<BatteryState> get onBatteryStateChanged => _controller.stream;

  void dispose() {
    _controller.close();
  }
}
