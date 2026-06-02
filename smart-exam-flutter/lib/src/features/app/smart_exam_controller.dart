import 'package:flutter/foundation.dart';

import '../../config/app_config.dart';
import '../../core/network/api_client.dart';
import '../../core/network/api_exception.dart';
import '../../core/storage/session_storage.dart';
import '../auth/models/auth_session.dart';
import '../exams/models/assigned_exam.dart';
import '../exams/models/exam_session_models.dart';
import '../exams/models/student_result_models.dart';

class SmartExamController extends ChangeNotifier {
  SmartExamController({required AppConfig config})
    : _storage = SessionStorage(),
      _config = config {
    _client = ApiClient(
      baseUrl: config.apiBaseUrl,
      tokenResolver: () => _session?.token,
    );
  }

  final SessionStorage _storage;
  final AppConfig _config;
  late final ApiClient _client;

  AuthSession? _session;
  UserEnvelope? _me;
  List<AssignedExam> _assignedExams = const [];
  bool _bootstrapping = true;
  bool _authenticating = false;
  bool _refreshing = false;
  String? _authError;
  String? _overviewError;
  String? _startingExamId;

  AppConfig get config => _config;
  AuthSession? get session => _session;
  UserEnvelope? get me => _me;
  List<AssignedExam> get assignedExams => _assignedExams;
  bool get isBootstrapping => _bootstrapping;
  bool get isAuthenticating => _authenticating;
  bool get isRefreshing => _refreshing;
  String? get authError => _authError;
  String? get overviewError => _overviewError;
  bool get isAuthenticated => _session != null;
  String? get startingExamId => _startingExamId;

  String get displayName {
    final realName = _me?.profile.realName.trim() ?? '';
    if (realName.isNotEmpty) {
      return realName;
    }
    final username = _session?.user.username.trim() ?? '';
    return username.isEmpty ? '学生' : username;
  }

  String get username => _me?.profile.username ?? _session?.user.username ?? '';

  String get roleLabel =>
      _me?.role.isNotEmpty == true ? _me!.role : _session?.user.role ?? '';

  int get runningExamCount =>
      _assignedExams.where((exam) => exam.isRunning).length;

  int get upcomingExamCount =>
      _assignedExams.where((exam) => exam.isNotStarted).length;

  int get completedExamCount =>
      _assignedExams.where((exam) => exam.isFinished).length;

  int get submittedCount =>
      _assignedExams.where((exam) => exam.hasSubmittedSession).length;

  AssignedExam? get featuredExam {
    if (_assignedExams.isEmpty) {
      return null;
    }
    final running = _assignedExams.where((exam) => exam.isRunning).toList();
    if (running.isNotEmpty) {
      running.sort(_compareExams);
      return running.first;
    }
    final upcoming = _assignedExams.where((exam) => exam.isNotStarted).toList();
    if (upcoming.isNotEmpty) {
      upcoming.sort(_compareExams);
      return upcoming.first;
    }
    final finished = [..._assignedExams]..sort(_compareExams);
    return finished.first;
  }

  Future<void> bootstrap() async {
    _bootstrapping = true;
    notifyListeners();

    final storedSession = await _storage.read();
    if (storedSession == null) {
      _bootstrapping = false;
      notifyListeners();
      return;
    }

    _session = storedSession;
    await refreshOverview(silent: true);
  }

  void clearAuthError() {
    if (_authError == null) {
      return;
    }
    _authError = null;
    notifyListeners();
  }

  Future<bool> login({
    required String username,
    required String password,
  }) async {
    _authenticating = true;
    _authError = null;
    notifyListeners();

    try {
      final data =
          await _client.post(
                '/auth/login',
                body: {'username': username.trim(), 'password': password},
              )
              as Map<String, dynamic>;

      final session = AuthSession.fromJson(data);
      _session = session;
      await _storage.write(session);
      await refreshOverview(silent: true);
      return true;
    } on ApiException catch (error) {
      _authError = error.message;
      await _clearSession();
      _bootstrapping = false;
      notifyListeners();
      return false;
    } finally {
      _authenticating = false;
      notifyListeners();
    }
  }

  Future<void> logout() async {
    _authError = null;
    _overviewError = null;
    await _clearSession();
    _bootstrapping = false;
    notifyListeners();
  }

  Future<void> refreshOverview({bool silent = false}) async {
    if (_session == null) {
      _bootstrapping = false;
      notifyListeners();
      return;
    }

    if (!silent) {
      _refreshing = true;
      notifyListeners();
    }

    _overviewError = null;

    try {
      final meData = await _client.get('/users/me') as Map<String, dynamic>;
      final examsData =
          await _client.get('/exams/students/me') as List<dynamic>;

      _me = UserEnvelope.fromJson(meData);
      _assignedExams =
          examsData
              .map(
                (item) => AssignedExam.fromJson(item as Map<String, dynamic>),
              )
              .toList()
            ..sort(_compareExams);
    } on ApiException catch (error) {
      if (error.isUnauthorized) {
        await _clearSession();
      } else {
        _overviewError = error.message;
      }
    } finally {
      _bootstrapping = false;
      _refreshing = false;
      notifyListeners();
    }
  }

  Future<StartExamResult> startExam(String examId) async {
    _startingExamId = examId;
    notifyListeners();

    try {
      final data =
          await _client.post('/exams/$examId/start') as Map<String, dynamic>;
      final result = StartExamResult.fromJson(data);
      await refreshOverview(silent: true);
      return result;
    } finally {
      _startingExamId = null;
      notifyListeners();
    }
  }

  Future<ExamPaperModel> fetchSessionPaper(String sessionId) async {
    try {
      final data =
          await _client.get('/sessions/$sessionId/paper')
              as Map<String, dynamic>;
      return ExamPaperModel.fromJson(data);
    } on ApiException catch (error) {
      await _handleUnauthorized(error);
      rethrow;
    }
  }

  Future<List<SessionAnswerModel>> fetchSessionAnswers(String sessionId) async {
    try {
      final data =
          await _client.get('/sessions/$sessionId/answers') as List<dynamic>;
      return data
          .map(
            (item) => SessionAnswerModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(growable: false);
    } on ApiException catch (error) {
      await _handleUnauthorized(error);
      rethrow;
    }
  }

  Future<void> saveAnswers(String sessionId, List<AnswerDraft> answers) async {
    try {
      await _client.put(
        '/sessions/$sessionId/answers',
        body: {
          'answers': answers
              .map((item) => item.toJson())
              .toList(growable: false),
        },
      );
    } on ApiException catch (error) {
      await _handleUnauthorized(error);
      rethrow;
    }
  }

  Future<SubmitSessionResult> submitSession(String sessionId) async {
    try {
      final data =
          await _client.post('/sessions/$sessionId/submit')
              as Map<String, dynamic>;
      return SubmitSessionResult.fromJson(data);
    } on ApiException catch (error) {
      await _handleUnauthorized(error);
      rethrow;
    }
  }

  Future<StudentSessionResult> fetchStudentSessionResult(
    String sessionId,
  ) async {
    try {
      final data =
          await _client.get('/grading/sessions/$sessionId/result')
              as Map<String, dynamic>;
      return StudentSessionResult.fromJson(data);
    } on ApiException catch (error) {
      await _handleUnauthorized(error);
      rethrow;
    }
  }

  Future<void> reportAntiCheatEvent(
    String sessionId, {
    required String eventType,
    Map<String, Object?> metadata = const {},
  }) async {
    try {
      await _client.post(
        '/sessions/$sessionId/anti-cheat/events',
        body: {'eventType': eventType, 'metadata': metadata},
      );
    } on ApiException catch (error) {
      await _handleUnauthorized(error);
      rethrow;
    }
  }

  Future<void> _clearSession() async {
    _session = null;
    _me = null;
    _assignedExams = const [];
    _startingExamId = null;
    _refreshing = false;
    await _storage.clear();
  }

  static int _compareExams(AssignedExam left, AssignedExam right) {
    final groupCompare = left.sortGroup.compareTo(right.sortGroup);
    if (groupCompare != 0) {
      return groupCompare;
    }

    final leftTime = left.startTime?.millisecondsSinceEpoch ?? 0;
    final rightTime = right.startTime?.millisecondsSinceEpoch ?? 0;

    if (left.isFinished && right.isFinished) {
      return rightTime.compareTo(leftTime);
    }
    return leftTime.compareTo(rightTime);
  }

  Future<void> _handleUnauthorized(ApiException error) async {
    if (!error.isUnauthorized) {
      return;
    }
    await _clearSession();
    _bootstrapping = false;
    notifyListeners();
  }
}
