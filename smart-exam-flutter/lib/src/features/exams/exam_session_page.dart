import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_exception.dart';
import '../../core/storage/answer_draft_storage.dart';
import '../app/smart_exam_controller.dart';
import 'models/assigned_exam.dart';
import 'models/exam_session_models.dart';

const _sessionTimeFormat = 'HH:mm:ss';
const _antiCheatReportCooldown = Duration(seconds: 12);

class ExamSessionPageResult {
  const ExamSessionPageResult({
    required this.submitted,
    this.deadlineExceeded = false,
  });

  final bool submitted;
  final bool deadlineExceeded;
}

class ExamSessionPage extends StatefulWidget {
  const ExamSessionPage({
    super.key,
    required this.controller,
    required this.exam,
    required this.sessionId,
  });

  final SmartExamController controller;
  final AssignedExam exam;
  final String sessionId;

  @override
  State<ExamSessionPage> createState() => _ExamSessionPageState();
}

class _ExamSessionPageState extends State<ExamSessionPage>
    with WidgetsBindingObserver {
  ExamPaperModel? _paper;
  Map<String, ExamPaperQuestionModel> _questionMap = const {};
  Map<String, AnswerDraft> _drafts = const {};
  Map<String, TextEditingController> _textControllers = {};
  final Map<String, DateTime> _lastAntiCheatReports = {};
  final List<_PendingAntiCheatEvent> _pendingAntiCheatEvents = [];
  final AnswerDraftStorage _draftStorage = AnswerDraftStorage();
  final Connectivity _connectivity = Connectivity();
  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;
  Timer? _clockTimer;
  Timer? _autoSaveTimer;
  Timer? _localDraftPersistTimer;

  bool _loading = true;
  bool _saving = false;
  bool _submitting = false;
  bool _hasUnsavedChanges = false;
  bool _deadlineSubmitAttempted = false;
  bool _networkOffline = false;
  bool _flushingAntiCheatEvents = false;
  int _currentIndex = 0;
  String? _loadError;
  String? _saveError;
  DateTime? _lastSavedAt;
  DateTime? _localDraftRestoredAt;
  DateTime? _serverAnswersUpdatedAt;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _startClock();
    _startNetworkMonitor();
    _loadSession();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _connectivitySubscription?.cancel();
    _clockTimer?.cancel();
    _autoSaveTimer?.cancel();
    _localDraftPersistTimer?.cancel();
    if (_hasUnsavedChanges) {
      unawaited(_persistLocalDraft());
    }
    _disposeTextControllers();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.hidden ||
        state == AppLifecycleState.paused) {
      _scheduleLocalDraftPersist(immediate: true);
      _reportAntiCheatEvent(
        'SWITCH_SCREEN',
        lifecycleState: state,
        metadata: const {'source': 'flutter.lifecycle.background'},
      );
      return;
    }

    if (state == AppLifecycleState.inactive) {
      _scheduleLocalDraftPersist(immediate: true);
      _reportAntiCheatEvent(
        'WINDOW_BLUR',
        lifecycleState: state,
        metadata: const {'source': 'flutter.lifecycle.inactive'},
      );
    }
  }

  List<ExamPaperQuestionModel> get _questions => _paper?.questions ?? const [];

  ExamPaperQuestionModel? get _currentQuestion {
    if (_questions.isEmpty) {
      return null;
    }
    final safeIndex = _currentIndex.clamp(0, _questions.length - 1);
    return _questions[safeIndex];
  }

  DateTime? get _deadline {
    if (widget.exam.endTime != null) {
      return widget.exam.endTime;
    }
    final paper = _paper;
    final sessionStart = widget.exam.sessionStartTime;
    if (paper != null && sessionStart != null && paper.timeLimitMinutes > 0) {
      return sessionStart.add(Duration(minutes: paper.timeLimitMinutes));
    }
    return null;
  }

  Duration? get _remainingDuration {
    final deadline = _deadline;
    if (deadline == null) {
      return null;
    }
    final diff = deadline.difference(_now);
    if (diff.isNegative) {
      return Duration.zero;
    }
    return diff;
  }

  int get _answeredCount =>
      _questions.where((question) => _isAnswered(question)).length;

  int get _markedCount => _questions
      .where((question) => _draftFor(question).markedForReview)
      .length;

  int get _unansweredCount => _questions.length - _answeredCount;

  bool get _isLastQuestion => _currentIndex >= _questions.length - 1;

  bool get _canReportAntiCheatEvent =>
      widget.sessionId.isNotEmpty && !_submitting && !_deadlineSubmitAttempted;

  void _reportAntiCheatEvent(
    String eventType, {
    AppLifecycleState? lifecycleState,
    Map<String, Object?> metadata = const {},
    bool queueIfOffline = false,
  }) {
    if (!_canReportAntiCheatEvent) {
      return;
    }

    final now = DateTime.now();
    final lastReportedAt = _lastAntiCheatReports[eventType];
    if (lastReportedAt != null &&
        now.difference(lastReportedAt) < _antiCheatReportCooldown) {
      return;
    }
    _lastAntiCheatReports[eventType] = now;

    final question = _currentQuestion;
    final payload = <String, Object?>{
      ...metadata,
      if (lifecycleState != null) 'lifecycleState': lifecycleState.name,
      'clientTime': now.toIso8601String(),
      'questionId': question?.questionId,
      'questionIndex': _questions.isEmpty ? 0 : _currentIndex + 1,
      'answeredCount': _answeredCount,
      'totalQuestionCount': _questions.length,
      'hasUnsavedChanges': _hasUnsavedChanges,
    };

    if (queueIfOffline && _networkOffline) {
      _pendingAntiCheatEvents.add(
        _PendingAntiCheatEvent(eventType: eventType, metadata: payload),
      );
      return;
    }

    unawaited(_sendAntiCheatEvent(eventType, payload));
  }

  Future<void> _sendAntiCheatEvent(
    String eventType,
    Map<String, Object?> metadata,
  ) async {
    try {
      await widget.controller.reportAntiCheatEvent(
        widget.sessionId,
        eventType: eventType,
        metadata: metadata,
      );
    } catch (_) {
      // Anti-cheat telemetry must not block answer editing or submission.
    }
  }

  void _startNetworkMonitor() {
    _connectivitySubscription = _connectivity.onConnectivityChanged.listen(
      _handleConnectivityChanged,
    );
    unawaited(_checkInitialConnectivity());
  }

  Future<void> _checkInitialConnectivity() async {
    try {
      final results = await _connectivity.checkConnectivity();
      if (!mounted) {
        return;
      }
      _handleConnectivityChanged(results, initial: true);
    } catch (_) {
      // Connectivity status is advisory; API errors still surface during save.
    }
  }

  void _handleConnectivityChanged(
    List<ConnectivityResult> results, {
    bool initial = false,
  }) {
    if (!mounted) {
      return;
    }

    final offline = _isOfflineConnectivity(results);
    final wasOffline = _networkOffline;

    if (offline == wasOffline && !initial) {
      return;
    }

    setState(() {
      _networkOffline = offline;
    });

    if (initial) {
      return;
    }

    if (offline) {
      _reportAntiCheatEvent(
        'NETWORK_DISCONNECT',
        metadata: {
          'source': 'flutter.connectivity',
          'connectivity': _connectivityNames(results),
        },
        queueIfOffline: true,
      );
      _showSnackBar('网络已断开，当前修改会保留在本页。');
      return;
    }

    if (wasOffline) {
      _showSnackBar('网络已恢复，可以继续保存答案。');
      unawaited(_flushPendingAntiCheatEvents());
      if (_hasUnsavedChanges && !_saving && !_submitting) {
        _scheduleAutoSave();
      }
    }
  }

  Future<void> _flushPendingAntiCheatEvents() async {
    if (_flushingAntiCheatEvents || _pendingAntiCheatEvents.isEmpty) {
      return;
    }

    _flushingAntiCheatEvents = true;
    final pending = List<_PendingAntiCheatEvent>.of(_pendingAntiCheatEvents);
    _pendingAntiCheatEvents.clear();

    for (var index = 0; index < pending.length; index++) {
      final event = pending[index];
      try {
        await widget.controller.reportAntiCheatEvent(
          widget.sessionId,
          eventType: event.eventType,
          metadata: {
            ...event.metadata,
            'reportedAfterReconnect': true,
            'reportedAt': DateTime.now().toIso8601String(),
          },
        );
      } catch (_) {
        _pendingAntiCheatEvents.insertAll(0, pending.skip(index));
        break;
      }
    }

    _flushingAntiCheatEvents = false;
  }

  void _startClock() {
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) {
        return;
      }

      final next = DateTime.now();
      final deadline = _deadline;
      final reachedDeadline =
          deadline != null &&
          !_deadlineSubmitAttempted &&
          !next.isBefore(deadline);

      setState(() {
        _now = next;
      });

      if (reachedDeadline) {
        _deadlineSubmitAttempted = true;
        _showSnackBar('考试时间已结束，正在提交答卷。');
        _submitExam(automatic: true);
      }
    });
  }

  Future<void> _loadSession({bool preservePosition = false}) async {
    final previousQuestionId = preservePosition
        ? _currentQuestion?.questionId
        : null;

    setState(() {
      _loading = true;
      _loadError = null;
    });

    try {
      final results = await Future.wait<Object?>([
        widget.controller.fetchSessionPaper(widget.sessionId),
        widget.controller.fetchSessionAnswers(widget.sessionId),
        _draftStorage.read(widget.sessionId),
      ]);
      final paper = results[0] as ExamPaperModel;
      final answers = results[1] as List<SessionAnswerModel>;
      final localDraft = results[2] as LocalAnswerDraftSnapshot?;

      if (!mounted) {
        return;
      }

      final localDraftResolution = _resolveLocalDraftRestore(
        localDraft: localDraft,
        answers: answers,
        paper: paper,
      );
      final restoreDecision = localDraftResolution.hasConflict
          ? await _confirmLocalDraftConflict(localDraftResolution)
          : _LocalDraftRestoreDecision.useLocal;

      if (!mounted) {
        return;
      }

      _applySessionData(
        paper,
        answers,
        localDraft: localDraft,
        localDraftResolution: localDraftResolution,
        restoreDecision: restoreDecision,
        preferredQuestionId: previousQuestionId,
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loading = false;
        _loadError = error.message;
      });
    }
  }

  void _applySessionData(
    ExamPaperModel paper,
    List<SessionAnswerModel> answers, {
    LocalAnswerDraftSnapshot? localDraft,
    required _LocalDraftResolution localDraftResolution,
    required _LocalDraftRestoreDecision restoreDecision,
    String? preferredQuestionId,
  }) {
    final questionMap = {
      for (final question in paper.questions) question.questionId: question,
    };

    final drafts = <String, AnswerDraft>{
      for (final question in paper.questions)
        question.questionId: AnswerDraft(
          questionId: question.questionId,
          answerContent: _defaultAnswerContent(question),
        ),
    };

    for (final answer in answers) {
      final question = questionMap[answer.questionId];
      if (question == null) {
        continue;
      }

      drafts[answer.questionId] = AnswerDraft(
        questionId: answer.questionId,
        answerContent: _normalizeAnswerContent(question, answer.answerContent),
        markedForReview: answer.markedForReview,
      );
    }

    final canRestoreLocalDraft =
        localDraftResolution.canRestore &&
        restoreDecision == _LocalDraftRestoreDecision.useLocal;
    final restoredLocalDraft = canRestoreLocalDraft ? localDraft : null;

    if (localDraft != null && restoredLocalDraft == null) {
      unawaited(_clearLocalDraft());
    }

    if (restoredLocalDraft != null) {
      for (final draft in restoredLocalDraft.drafts) {
        final question = questionMap[draft.questionId];
        if (question == null) {
          continue;
        }

        drafts[draft.questionId] = AnswerDraft(
          questionId: draft.questionId,
          answerContent: _normalizeAnswerContent(question, draft.answerContent),
          markedForReview: draft.markedForReview,
        );
      }
    }

    _disposeTextControllers();

    final textControllers = <String, TextEditingController>{};
    for (final question in paper.questions.where(
      (item) => item.usesTextInput,
    )) {
      final controller = TextEditingController(
        text: _textValueForAnswer(drafts[question.questionId]?.answerContent),
      );
      controller.addListener(() {
        _handleTextEdited(question.questionId, controller.text);
      });
      textControllers[question.questionId] = controller;
    }

    final nextIndex = _resolveQuestionIndex(
      paper.questions,
      preferredQuestionId ?? restoredLocalDraft?.currentQuestionId,
      _currentIndex,
    );

    setState(() {
      _paper = paper;
      _questionMap = questionMap;
      _drafts = drafts;
      _textControllers = textControllers;
      _currentIndex = nextIndex;
      _loading = false;
      _loadError = null;
      _saveError = null;
      _lastSavedAt = null;
      _serverAnswersUpdatedAt = localDraftResolution.serverAnswersUpdatedAt;
      _localDraftRestoredAt = restoredLocalDraft?.updatedAt;
      _hasUnsavedChanges = restoredLocalDraft != null;
    });
  }

  _LocalDraftResolution _resolveLocalDraftRestore({
    required LocalAnswerDraftSnapshot? localDraft,
    required List<SessionAnswerModel> answers,
    required ExamPaperModel paper,
  }) {
    final serverAnswersUpdatedAt = _latestAnswerUpdatedAt(answers);
    final canRestore =
        localDraft != null &&
        localDraft.drafts.isNotEmpty &&
        localDraft.sessionId == widget.sessionId &&
        (localDraft.examId.isEmpty || localDraft.examId == paper.examId) &&
        (localDraft.paperId.isEmpty || localDraft.paperId == paper.paperId);

    if (!canRestore) {
      return _LocalDraftResolution(
        canRestore: false,
        hasConflict: false,
        serverAnswersUpdatedAt: serverAnswersUpdatedAt,
      );
    }

    final baseline = localDraft.serverAnswersUpdatedAt;
    final hasConflict =
        serverAnswersUpdatedAt != null &&
        (baseline == null || serverAnswersUpdatedAt.isAfter(baseline));

    return _LocalDraftResolution(
      canRestore: true,
      hasConflict: hasConflict,
      serverAnswersUpdatedAt: serverAnswersUpdatedAt,
      localUpdatedAt: localDraft.updatedAt,
    );
  }

  Future<_LocalDraftRestoreDecision> _confirmLocalDraftConflict(
    _LocalDraftResolution resolution,
  ) async {
    final decision = await showDialog<_LocalDraftRestoreDecision>(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        final theme = Theme.of(context);

        return AlertDialog(
          title: const Text('发现答案版本冲突'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '本地草稿和服务端答案都有更新，请选择本次进入使用哪一份。',
                style: theme.textTheme.bodyLarge,
              ),
              const SizedBox(height: 10),
              Text(
                '本地草稿：${_formatOptionalDateTime(resolution.localUpdatedAt)}',
                style: theme.textTheme.bodyMedium,
              ),
              const SizedBox(height: 4),
              Text(
                '服务端答案：${_formatOptionalDateTime(resolution.serverAnswersUpdatedAt)}',
                style: theme.textTheme.bodyMedium,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop(_LocalDraftRestoreDecision.useServer);
              },
              child: const Text('使用服务端'),
            ),
            FilledButton(
              onPressed: () {
                Navigator.of(context).pop(_LocalDraftRestoreDecision.useLocal);
              },
              child: const Text('恢复本地草稿'),
            ),
          ],
        );
      },
    );

    return decision ?? _LocalDraftRestoreDecision.useServer;
  }

  void _disposeTextControllers() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    _textControllers = {};
  }

  int _resolveQuestionIndex(
    List<ExamPaperQuestionModel> questions,
    String? preferredQuestionId,
    int fallbackIndex,
  ) {
    if (questions.isEmpty) {
      return 0;
    }

    if (preferredQuestionId != null && preferredQuestionId.isNotEmpty) {
      for (var index = 0; index < questions.length; index++) {
        if (questions[index].questionId == preferredQuestionId) {
          return index;
        }
      }
    }

    return fallbackIndex.clamp(0, questions.length - 1);
  }

  void _handleTextEdited(String questionId, String value) {
    final question = _questionMap[questionId];
    if (question == null) {
      return;
    }

    final current = _textValueForAnswer(_draftFor(question).answerContent);
    if (current == value) {
      return;
    }

    _updateDraft(questionId, answerContent: value);
  }

  AnswerDraft _draftFor(ExamPaperQuestionModel question) {
    return _drafts[question.questionId] ??
        AnswerDraft(
          questionId: question.questionId,
          answerContent: _defaultAnswerContent(question),
        );
  }

  void _updateDraft(
    String questionId, {
    Object? answerContent = _missingValue,
    bool? markedForReview,
  }) {
    final question = _questionMap[questionId];
    final current =
        _drafts[questionId] ??
        AnswerDraft(
          questionId: questionId,
          answerContent: question == null
              ? ''
              : _defaultAnswerContent(question),
        );
    final nextAnswerContent = identical(answerContent, _missingValue)
        ? current.answerContent
        : answerContent;

    setState(() {
      _drafts = {..._drafts};
      _drafts[questionId] = AnswerDraft(
        questionId: questionId,
        answerContent: nextAnswerContent,
        markedForReview: markedForReview ?? current.markedForReview,
      );
      _hasUnsavedChanges = true;
      _saveError = null;
    });

    _scheduleLocalDraftPersist();
    _scheduleAutoSave();
  }

  void _scheduleLocalDraftPersist({bool immediate = false}) {
    if (!_hasUnsavedChanges || _paper == null) {
      return;
    }

    _localDraftPersistTimer?.cancel();
    if (immediate) {
      unawaited(_persistLocalDraft());
      return;
    }

    _localDraftPersistTimer = Timer(const Duration(milliseconds: 300), () {
      unawaited(_persistLocalDraft());
    });
  }

  Future<void> _persistLocalDraft() async {
    final paper = _paper;
    if (paper == null || !_hasUnsavedChanges) {
      return;
    }

    try {
      await _draftStorage.write(
        LocalAnswerDraftSnapshot(
          sessionId: widget.sessionId,
          examId: paper.examId,
          paperId: paper.paperId,
          currentQuestionId: _currentQuestion?.questionId ?? '',
          updatedAt: DateTime.now(),
          serverAnswersUpdatedAt: _serverAnswersUpdatedAt,
          drafts: _buildSavePayload(paper),
        ),
      );
      if (!_hasUnsavedChanges) {
        await _draftStorage.clear(widget.sessionId);
      }
    } catch (_) {
      // Local recovery is best-effort; network save remains the source of truth.
    }
  }

  Future<void> _clearLocalDraft() async {
    try {
      await _draftStorage.clear(widget.sessionId);
    } catch (_) {
      // Clearing local recovery data should not interrupt the exam flow.
    }
  }

  void _scheduleAutoSave() {
    _autoSaveTimer?.cancel();
    _autoSaveTimer = Timer(const Duration(milliseconds: 900), () {
      if (!mounted || _loading || _submitting || _saving) {
        return;
      }
      _saveAnswers(showFeedback: false);
    });
  }

  Future<bool> _saveAnswers({required bool showFeedback}) async {
    final paper = _paper;
    if (paper == null || _saving || _submitting) {
      return false;
    }

    _autoSaveTimer?.cancel();
    _localDraftPersistTimer?.cancel();
    FocusScope.of(context).unfocus();

    if (_networkOffline) {
      unawaited(_persistLocalDraft());
      if (showFeedback) {
        _showSnackBar('当前网络不可用，答案已保留在本页，恢复后请重试保存。');
      }
      return false;
    }

    if (!_hasUnsavedChanges) {
      if (showFeedback) {
        _showSnackBar('当前没有需要保存的修改。');
      }
      return true;
    }

    setState(() {
      _saving = true;
      _saveError = null;
    });

    try {
      await widget.controller.saveAnswers(
        widget.sessionId,
        _buildSavePayload(paper),
      );
      if (!mounted) {
        return true;
      }

      setState(() {
        _saving = false;
        _hasUnsavedChanges = false;
        _lastSavedAt = DateTime.now();
        _serverAnswersUpdatedAt = _lastSavedAt;
        _localDraftRestoredAt = null;
      });
      unawaited(_clearLocalDraft());

      if (showFeedback) {
        _showSnackBar('答案已保存。');
      }
      return true;
    } on ApiException catch (error) {
      if (!mounted) {
        return false;
      }

      setState(() {
        _saving = false;
        _saveError = error.message;
      });

      if (showFeedback) {
        _showSnackBar(error.message);
      }
      return false;
    }
  }

  List<AnswerDraft> _buildSavePayload(ExamPaperModel paper) {
    return paper.questions
        .map((question) {
          final draft = _draftFor(question);
          if (question.usesTextInput) {
            return draft.copyWith(
              answerContent: _textControllers[question.questionId]?.text ?? '',
            );
          }
          if (question.isMultipleChoice) {
            return draft.copyWith(
              answerContent: _sortedChoiceValues(question, draft.answerContent),
            );
          }
          return draft;
        })
        .toList(growable: false);
  }

  Future<void> _submitExam({required bool automatic}) async {
    final paper = _paper;
    if (paper == null || _submitting) {
      return;
    }

    if (!automatic) {
      final confirmed = await _confirmSubmit();
      if (!confirmed || !mounted) {
        return;
      }
    }

    final saved = await _saveAnswers(showFeedback: false);
    if (!saved || !mounted) {
      if (automatic) {
        _showSnackBar('自动交卷前保存失败，请立即手动交卷。');
      }
      return;
    }

    setState(() {
      _submitting = true;
    });

    try {
      final result = await widget.controller.submitSession(widget.sessionId);
      if (!mounted) {
        return;
      }

      _showSnackBar(automatic ? '答卷已自动提交。' : '交卷成功。');
      unawaited(_clearLocalDraft());
      Navigator.of(context).pop(
        ExamSessionPageResult(
          submitted: true,
          deadlineExceeded: result.deadlineExceeded,
        ),
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }

      setState(() {
        _submitting = false;
      });
      _showSnackBar(error.message);
    }
  }

  Future<bool> _confirmSubmit() async {
    final answerRate = _questions.isEmpty
        ? 0
        : (_answeredCount / _questions.length * 100).round();

    final decision = await showDialog<_SubmitDecision>(
      context: context,
      builder: (context) {
        final theme = Theme.of(context);

        return AlertDialog(
          title: const Text('确认交卷'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '已作答 $_answeredCount / ${_questions.length} 题，完成率 $answerRate%。',
                style: theme.textTheme.bodyLarge,
              ),
              const SizedBox(height: 8),
              if (_unansweredCount > 0)
                Text(
                  '还有 $_unansweredCount 题未作答，交卷后将无法继续修改。',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              if (_markedCount > 0) ...[
                const SizedBox(height: 8),
                Text(
                  '你还标记了 $_markedCount 题待复查。',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop(_SubmitDecision.cancel);
              },
              child: const Text('再检查一下'),
            ),
            FilledButton(
              onPressed: () {
                Navigator.of(context).pop(_SubmitDecision.submit);
              },
              child: const Text('确认交卷'),
            ),
          ],
        );
      },
    );

    return decision == _SubmitDecision.submit;
  }

  Future<bool> _handleBackNavigation() async {
    if (_submitting) {
      return false;
    }

    if (!_hasUnsavedChanges) {
      return true;
    }

    final action = await showDialog<_LeaveDecision>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('离开答题页'),
          content: const Text('当前有未保存的修改，离开前是否先保存答案？'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop(_LeaveDecision.cancel);
              },
              child: const Text('取消'),
            ),
            TextButton(
              onPressed: () {
                Navigator.of(context).pop(_LeaveDecision.discard);
              },
              child: const Text('直接离开'),
            ),
            FilledButton(
              onPressed: () {
                Navigator.of(context).pop(_LeaveDecision.save);
              },
              child: const Text('保存后离开'),
            ),
          ],
        );
      },
    );

    switch (action) {
      case _LeaveDecision.save:
        return _saveAnswers(showFeedback: false);
      case _LeaveDecision.discard:
        return true;
      case _LeaveDecision.cancel:
      case null:
        return false;
    }
  }

  void _jumpToQuestion(int index) {
    if (index < 0 || index >= _questions.length) {
      return;
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _currentIndex = index;
    });
  }

  void _moveToPrevious() {
    if (_currentIndex <= 0) {
      return;
    }
    _jumpToQuestion(_currentIndex - 1);
  }

  void _moveToNext() {
    if (_currentIndex >= _questions.length - 1) {
      return;
    }
    _jumpToQuestion(_currentIndex + 1);
  }

  void _toggleReviewMark(ExamPaperQuestionModel question) {
    _updateDraft(
      question.questionId,
      markedForReview: !_draftFor(question).markedForReview,
    );
  }

  void _selectSingleOption(ExamPaperQuestionModel question, String optionKey) {
    final current = _textValueForAnswer(_draftFor(question).answerContent);
    _updateDraft(
      question.questionId,
      answerContent: current == optionKey ? '' : optionKey,
    );
  }

  void _toggleMultiOption(ExamPaperQuestionModel question, String optionKey) {
    final values = _sortedChoiceValues(
      question,
      _draftFor(question).answerContent,
    ).toSet();

    if (values.contains(optionKey)) {
      values.remove(optionKey);
    } else {
      values.add(optionKey);
    }

    _updateDraft(
      question.questionId,
      answerContent: _sortChoiceValues(question, values),
    );
  }

  void _selectJudgeOption(ExamPaperQuestionModel question, bool value) {
    final current = _judgeValueForAnswer(_draftFor(question).answerContent);
    _updateDraft(
      question.questionId,
      answerContent: current == value ? '' : value,
    );
  }

  void _clearCurrentAnswer() {
    final question = _currentQuestion;
    if (question == null) {
      return;
    }

    if (question.usesTextInput) {
      _textControllers[question.questionId]?.clear();
      return;
    }

    _updateDraft(
      question.questionId,
      answerContent: _defaultAnswerContent(question),
    );
  }

  void _openQuestionSheet() {
    final currentQuestion = _currentQuestion;
    if (currentQuestion == null) {
      return;
    }

    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        final theme = Theme.of(context);

        return SafeArea(
          child: Container(
            margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 20),
            decoration: BoxDecoration(
              color: const Color(0xFFF8FBFD),
              borderRadius: BorderRadius.circular(28),
              border: Border.all(color: const Color(0xFFDCE6EE)),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        '题卡',
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    IconButton(
                      onPressed: () {
                        Navigator.of(context).pop();
                      },
                      icon: const Icon(Icons.close_rounded),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _LegendChip(
                      label: '已作答 $_answeredCount',
                      foreground: const Color(0xFF0E766B),
                      background: const Color(0xFFD9F7F3),
                    ),
                    _LegendChip(
                      label: '待复查 $_markedCount',
                      foreground: const Color(0xFF8A5A00),
                      background: const Color(0xFFFFE7BF),
                    ),
                    _LegendChip(
                      label: '未作答 $_unansweredCount',
                      foreground: theme.colorScheme.onSurfaceVariant,
                      background: const Color(0xFFE7EEF4),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                SizedBox(
                  height: 360,
                  child: GridView.builder(
                    itemCount: _questions.length,
                    gridDelegate:
                        const SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 5,
                          mainAxisSpacing: 12,
                          crossAxisSpacing: 12,
                          childAspectRatio: 1,
                        ),
                    itemBuilder: (context, index) {
                      final question = _questions[index];
                      final active = index == _currentIndex;
                      final answered = _isAnswered(question);
                      final marked = _draftFor(question).markedForReview;

                      final background = active
                          ? theme.colorScheme.primary
                          : marked
                          ? const Color(0xFFFFE7BF)
                          : answered
                          ? const Color(0xFFD9F7F3)
                          : Colors.white;
                      final foreground = active
                          ? Colors.white
                          : marked
                          ? const Color(0xFF8A5A00)
                          : answered
                          ? const Color(0xFF0E766B)
                          : theme.colorScheme.onSurface;

                      return InkWell(
                        borderRadius: BorderRadius.circular(18),
                        onTap: () {
                          Navigator.of(context).pop();
                          _jumpToQuestion(index);
                        },
                        child: Container(
                          decoration: BoxDecoration(
                            color: background,
                            borderRadius: BorderRadius.circular(18),
                            border: Border.all(
                              color: active
                                  ? theme.colorScheme.primary
                                  : const Color(0xFFDCE6EE),
                            ),
                          ),
                          alignment: Alignment.center,
                          child: Text(
                            '${index + 1}',
                            style: theme.textTheme.titleMedium?.copyWith(
                              color: foreground,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showSnackBar(String message) {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_submitting && !_hasUnsavedChanges,
      onPopInvokedWithResult: (didPop, result) async {
        if (didPop) {
          return;
        }
        final shouldLeave = await _handleBackNavigation();
        if (shouldLeave && mounted) {
          Navigator.of(this.context).pop();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: Stack(
          children: [
            const _SessionBackdrop(),
            SafeArea(
              child: Column(
                children: [
                  _buildTopBar(context),
                  Expanded(child: _buildBody(context)),
                  if (!_loading && _paper != null && _questions.isNotEmpty)
                    _buildBottomBar(context),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context) {
    final theme = Theme.of(context);
    final navigator = Navigator.of(context);
    final currentQuestion = _currentQuestion;
    final title = _paper?.paperName.trim().isNotEmpty == true
        ? _paper!.paperName
        : widget.exam.title;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
      child: Row(
        children: [
          IconButton.filledTonal(
            onPressed: () async {
              final shouldLeave = await _handleBackNavigation();
              if (shouldLeave && mounted) {
                navigator.pop();
              }
            },
            icon: const Icon(Icons.arrow_back_ios_new_rounded),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  currentQuestion == null
                      ? '正在同步考试内容'
                      : '第 ${_currentIndex + 1} / ${_questions.length} 题',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          IconButton.filledTonal(
            onPressed: currentQuestion == null ? null : _openQuestionSheet,
            icon: const Icon(Icons.apps_rounded),
          ),
        ],
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_loadError != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: _MessageCard(
            title: '加载考试失败',
            detail: _loadError!,
            actionLabel: '重新加载',
            onAction: () {
              _loadSession();
            },
          ),
        ),
      );
    }

    if (_paper == null || _questions.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: _MessageCard(
            title: '当前没有可作答的试题',
            detail: '请返回考试列表后重试。',
            actionLabel: '返回',
            onAction: () {
              Navigator.of(context).pop();
            },
          ),
        ),
      );
    }

    final currentQuestion = _currentQuestion!;

    return RefreshIndicator(
      onRefresh: () => _loadSession(preservePosition: true),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
        children: [
          _buildHeroPanel(context),
          const SizedBox(height: 18),
          _buildOverviewPanel(context),
          const SizedBox(height: 18),
          if (_networkOffline) ...[
            _buildOfflineNotice(context),
            const SizedBox(height: 18),
          ],
          AnimatedSwitcher(
            duration: const Duration(milliseconds: 280),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeInCubic,
            transitionBuilder: (child, animation) {
              final slide =
                  Tween<Offset>(
                    begin: const Offset(0.02, 0.02),
                    end: Offset.zero,
                  ).animate(
                    CurvedAnimation(
                      parent: animation,
                      curve: Curves.easeOutCubic,
                    ),
                  );

              return FadeTransition(
                opacity: animation,
                child: SlideTransition(position: slide, child: child),
              );
            },
            child: KeyedSubtree(
              key: ValueKey(currentQuestion.questionId),
              child: _buildQuestionCard(context, currentQuestion),
            ),
          ),
          const SizedBox(height: 16),
          _buildTipsCard(context),
          const SizedBox(height: 108),
        ],
      ),
    );
  }

  Widget _buildHeroPanel(BuildContext context) {
    final theme = Theme.of(context);
    final paper = _paper!;
    final progress = _questions.isEmpty
        ? 0.0
        : _answeredCount / _questions.length;
    final remainingDuration = _remainingDuration;

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0xFF102A43), Color(0xFF0B1F33), Color(0xFF145E65)],
        ),
        borderRadius: BorderRadius.circular(30),
        boxShadow: const [
          BoxShadow(
            color: Color(0x22000000),
            blurRadius: 28,
            offset: Offset(0, 18),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  '正在作答',
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              const Spacer(),
              Icon(
                Icons.timer_outlined,
                color: Colors.white.withValues(alpha: 0.75),
              ),
              const SizedBox(width: 8),
              Text(
                remainingDuration == null
                    ? '${paper.timeLimitMinutes} 分钟'
                    : _formatDuration(remainingDuration),
                style: GoogleFonts.spaceGrotesk(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            widget.exam.title,
            style: GoogleFonts.spaceGrotesk(
              color: Colors.white,
              fontSize: 30,
              fontWeight: FontWeight.w700,
              height: 1.05,
            ),
          ),
          if (paper.paperName.trim().isNotEmpty &&
              paper.paperName.trim() != widget.exam.title.trim()) ...[
            const SizedBox(height: 10),
            Text(
              paper.paperName,
              style: theme.textTheme.titleMedium?.copyWith(
                color: const Color(0xFFC8D6E5),
              ),
            ),
          ],
          const SizedBox(height: 18),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _HeroInfoChip(label: '题目 ${_questions.length}', value: '总题数'),
              _HeroInfoChip(label: '${paper.totalScore} 分', value: '试卷总分'),
              _HeroInfoChip(
                label: '${paper.timeLimitMinutes} 分钟',
                value: '作答时长',
              ),
            ],
          ),
          const SizedBox(height: 18),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              minHeight: 10,
              value: progress.clamp(0, 1),
              backgroundColor: Colors.white.withValues(alpha: 0.12),
              valueColor: const AlwaysStoppedAnimation<Color>(
                Color(0xFF8DEADF),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: Text(
                  '已作答 $_answeredCount / ${_questions.length} 题',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: const Color(0xFFC8D6E5),
                  ),
                ),
              ),
              Text(
                _buildSaveStatusLabel(),
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: _saveError == null
                      ? const Color(0xFFC8D6E5)
                      : const Color(0xFFFFD6A8),
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          if (_deadline != null) ...[
            const SizedBox(height: 8),
            Text(
              '截止 ${DateFormat('M月d日 HH:mm').format(_deadline!)}',
              style: theme.textTheme.bodySmall?.copyWith(
                color: const Color(0xFF9FB3C8),
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildOverviewPanel(BuildContext context) {
    final theme = Theme.of(context);
    final currentQuestion = _currentQuestion!;

    return _SurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '当前题目概览',
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '第 ${_currentIndex + 1} 题 · ${_questionTypeLabel(currentQuestion.type)}',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              OutlinedButton.icon(
                onPressed: _openQuestionSheet,
                icon: const Icon(Icons.grid_view_rounded),
                label: const Text('打开题卡'),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              _StatPill(
                label: '已作答',
                value: '$_answeredCount',
                background: const Color(0xFFD9F7F3),
                foreground: const Color(0xFF0E766B),
              ),
              _StatPill(
                label: '未作答',
                value: '$_unansweredCount',
                background: const Color(0xFFE7EEF4),
                foreground: const Color(0xFF486581),
              ),
              _StatPill(
                label: '待复查',
                value: '$_markedCount',
                background: const Color(0xFFFFE7BF),
                foreground: const Color(0xFF8A5A00),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildQuestionCard(
    BuildContext context,
    ExamPaperQuestionModel question,
  ) {
    final theme = Theme.of(context);
    final draft = _draftFor(question);
    final typeLabel = _questionTypeLabel(question.type);
    final answered = _isAnswered(question);

    return _SurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _LegendChip(
                      label: typeLabel,
                      foreground: const Color(0xFF305A86),
                      background: const Color(0xFFE4ECF8),
                    ),
                    _LegendChip(
                      label: '${question.score} 分',
                      foreground: const Color(0xFF5A3E00),
                      background: const Color(0xFFFFE7BF),
                    ),
                    _LegendChip(
                      label: answered ? '已作答' : '未作答',
                      foreground: answered
                          ? const Color(0xFF0E766B)
                          : theme.colorScheme.onSurfaceVariant,
                      background: answered
                          ? const Color(0xFFD9F7F3)
                          : const Color(0xFFE7EEF4),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 12),
              TextButton.icon(
                onPressed: () {
                  _toggleReviewMark(question);
                },
                icon: Icon(
                  draft.markedForReview
                      ? Icons.bookmark_rounded
                      : Icons.bookmark_border_rounded,
                ),
                label: Text(draft.markedForReview ? '已标记' : '标记复查'),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            '${_currentIndex + 1}. ${question.stem}',
            style: theme.textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.w800,
              height: 1.25,
            ),
          ),
          const SizedBox(height: 18),
          _buildAnswerEditor(context, question),
          const SizedBox(height: 18),
          Row(
            children: [
              TextButton.icon(
                onPressed: _clearCurrentAnswer,
                icon: const Icon(Icons.restart_alt_rounded),
                label: const Text('清空当前答案'),
              ),
              const Spacer(),
              Text(
                draft.markedForReview ? '已加入复查列表' : '作答后将自动保存',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildAnswerEditor(
    BuildContext context,
    ExamPaperQuestionModel question,
  ) {
    if (question.isSingleChoice) {
      final selected = _textValueForAnswer(_draftFor(question).answerContent);

      return Column(
        children: [
          for (final option in question.options) ...[
            _OptionTile(
              label: option.key,
              text: option.text,
              selected: selected == option.key,
              icon: selected == option.key
                  ? Icons.radio_button_checked_rounded
                  : Icons.radio_button_off_rounded,
              onTap: () {
                _selectSingleOption(question, option.key);
              },
            ),
            if (option != question.options.last) const SizedBox(height: 12),
          ],
        ],
      );
    }

    if (question.isMultipleChoice) {
      final selectedValues = _sortedChoiceValues(
        question,
        _draftFor(question).answerContent,
      ).toSet();

      return Column(
        children: [
          for (final option in question.options) ...[
            _OptionTile(
              label: option.key,
              text: option.text,
              selected: selectedValues.contains(option.key),
              icon: selectedValues.contains(option.key)
                  ? Icons.check_box_rounded
                  : Icons.check_box_outline_blank_rounded,
              onTap: () {
                _toggleMultiOption(question, option.key);
              },
            ),
            if (option != question.options.last) const SizedBox(height: 12),
          ],
        ],
      );
    }

    if (question.isJudge) {
      final selected = _judgeValueForAnswer(_draftFor(question).answerContent);

      return Row(
        children: [
          Expanded(
            child: _JudgeCard(
              label: '正确',
              selected: selected == true,
              onTap: () {
                _selectJudgeOption(question, true);
              },
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: _JudgeCard(
              label: '错误',
              selected: selected == false,
              onTap: () {
                _selectJudgeOption(question, false);
              },
            ),
          ),
        ],
      );
    }

    final controller = _textControllers[question.questionId];
    final minLines = question.isShort ? 6 : 4;
    final label = question.isShort ? '请输入作答内容' : '请输入填空答案';

    return TextField(
      controller: controller,
      minLines: minLines,
      maxLines: question.isShort ? 10 : 6,
      textInputAction: TextInputAction.newline,
      decoration: InputDecoration(
        labelText: label,
        alignLabelWithHint: true,
        hintText: question.isShort ? '支持自动保存，可先写要点后再补充细节。' : '填写后系统会自动保存。',
      ),
    );
  }

  Widget _buildTipsCard(BuildContext context) {
    final theme = Theme.of(context);

    return _SurfaceCard(
      dark: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionLabel(label: '作答提示'),
          const SizedBox(height: 14),
          Text(
            '系统会在你作答时自动保存，交卷前仍建议手动检查一次题卡。',
            style: GoogleFonts.spaceGrotesk(
              color: Colors.white,
              fontSize: 25,
              fontWeight: FontWeight.w700,
              height: 1.15,
            ),
          ),
          const SizedBox(height: 12),
          Text(
            '可以用“标记复查”先记下需要回看的题目。若网络短暂波动，答题内容会保留在当前页面，恢复连接后再保存即可。',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: const Color(0xFFC8D6E5),
            ),
          ),
          const SizedBox(height: 16),
          const Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _LegendChip(
                label: '自动保存',
                foreground: Colors.white,
                background: Color(0x1FFFFFFF),
              ),
              _LegendChip(
                label: '支持题卡跳转',
                foreground: Colors.white,
                background: Color(0x1FFFFFFF),
              ),
              _LegendChip(
                label: '可标记复查',
                foreground: Colors.white,
                background: Color(0x1FFFFFFF),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildOfflineNotice(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF4E8),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: const Color(0xFFFFD6A8)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.wifi_off_rounded, color: Color(0xFFB75C00)),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '网络已断开',
                  style: theme.textTheme.titleMedium?.copyWith(
                    color: const Color(0xFF8B4400),
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '当前答案会继续保留在本页。网络恢复后，系统会自动尝试同步未保存的修改。',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: const Color(0xFF8B4400),
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomBar(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 20),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.95),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(28)),
        border: Border.all(color: const Color(0xFFDCE6EE)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  _buildSaveStatusLabel(),
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: _saveStatusColor(theme),
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              Text(
                '剩余 ${_remainingDuration == null ? "--" : _formatDuration(_remainingDuration!)}',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: _currentIndex == 0 || _saving || _submitting
                      ? null
                      : _moveToPrevious,
                  icon: const Icon(Icons.west_rounded),
                  label: const Text('上一题'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton.icon(
                  onPressed: _saving || _submitting
                      ? null
                      : () {
                          _saveAnswers(showFeedback: true);
                        },
                  icon: Icon(
                    _saving ? Icons.cloud_upload_outlined : Icons.save_rounded,
                  ),
                  label: Text(_saving ? '保存中' : '保存'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: (_isLastQuestion
                    ? FilledButton.icon(
                        onPressed: _submitting
                            ? null
                            : () {
                                _submitExam(automatic: false);
                              },
                        icon: const Icon(Icons.task_alt_rounded),
                        label: Text(_submitting ? '交卷中' : '交卷'),
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFFF59E0B),
                        ),
                      )
                    : OutlinedButton.icon(
                        onPressed: _saving || _submitting ? null : _moveToNext,
                        icon: const Icon(Icons.east_rounded),
                        label: const Text('下一题'),
                      )),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _buildSaveStatusLabel() {
    if (_saving) {
      return '正在同步答案...';
    }
    if (_submitting) {
      return '正在提交答卷...';
    }
    if (_networkOffline && _hasUnsavedChanges) {
      return '网络不可用，修改已保留在本页';
    }
    if (_networkOffline) {
      return '网络不可用，恢复后可继续同步';
    }
    if (_saveError != null) {
      return '保存失败：$_saveError';
    }
    if (_localDraftRestoredAt != null && _hasUnsavedChanges) {
      return '已恢复本地草稿，等待同步';
    }
    if (_hasUnsavedChanges) {
      return '有未保存的修改';
    }
    if (_lastSavedAt != null) {
      return '已于 ${DateFormat(_sessionTimeFormat).format(_lastSavedAt!)} 保存';
    }
    return '已同步最近一次作答记录';
  }

  Color _saveStatusColor(ThemeData theme) {
    if (_networkOffline || _saveError != null) {
      return const Color(0xFFB75C00);
    }
    return theme.colorScheme.onSurfaceVariant;
  }

  bool _isAnswered(ExamPaperQuestionModel question) {
    final content = _draftFor(question).answerContent;

    if (question.isMultipleChoice) {
      return _sortedChoiceValues(question, content).isNotEmpty;
    }
    if (question.isJudge) {
      return _judgeValueForAnswer(content) != null;
    }
    return _textValueForAnswer(content).trim().isNotEmpty;
  }

  Object _defaultAnswerContent(ExamPaperQuestionModel question) {
    if (question.isMultipleChoice) {
      return const <String>[];
    }
    return '';
  }

  Object _normalizeAnswerContent(
    ExamPaperQuestionModel question,
    Object? answerContent,
  ) {
    if (question.isMultipleChoice) {
      return _sortedChoiceValues(question, answerContent);
    }
    if (question.isJudge) {
      return _judgeValueForAnswer(answerContent) ?? '';
    }
    return _textValueForAnswer(answerContent);
  }

  String _textValueForAnswer(Object? value) {
    if (value == null) {
      return '';
    }
    if (value is String) {
      return value;
    }
    if (value is Iterable) {
      return value.isEmpty ? '' : '${value.first}';
    }
    final text = '$value';
    if (text == 'null') {
      return '';
    }
    return text;
  }

  bool? _judgeValueForAnswer(Object? value) {
    if (value is bool) {
      return value;
    }
    final raw = '$value'.trim().toLowerCase();
    switch (raw) {
      case 'true':
      case '1':
      case 'yes':
      case 'y':
        return true;
      case 'false':
      case '0':
      case 'no':
      case 'n':
        return false;
      default:
        return null;
    }
  }

  List<String> _sortedChoiceValues(
    ExamPaperQuestionModel question,
    Object? value,
  ) {
    final values = <String>{};

    if (value is Iterable) {
      for (final item in value) {
        final token = '$item'.trim().toUpperCase();
        if (token.isNotEmpty && token != 'NULL') {
          values.add(token);
        }
      }
    } else {
      final raw = '$value'.trim();
      if (raw.isNotEmpty && raw != 'null') {
        for (final item in raw.split(RegExp(r'[,，\s]+'))) {
          final token = item.trim().toUpperCase();
          if (token.isNotEmpty) {
            values.add(token);
          }
        }
      }
    }

    return _sortChoiceValues(question, values);
  }

  List<String> _sortChoiceValues(
    ExamPaperQuestionModel question,
    Iterable<String> values,
  ) {
    final selected = values.toSet();
    final ordered = <String>[];

    for (final option in question.options) {
      final key = option.key.trim().toUpperCase();
      if (selected.remove(key)) {
        ordered.add(key);
      }
    }

    if (selected.isNotEmpty) {
      final rest = selected.toList()..sort();
      ordered.addAll(rest);
    }

    return ordered;
  }
}

class _SessionBackdrop extends StatelessWidget {
  const _SessionBackdrop();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFF6FBFD), Color(0xFFEAF4F7), Color(0xFFE4ECF3)],
        ),
      ),
      child: Stack(
        children: const [
          Positioned(
            top: -60,
            right: -40,
            child: _GlowOrb(size: 220, color: Color(0x4419B7A6)),
          ),
          Positioned(
            top: 180,
            left: -90,
            child: _GlowOrb(size: 260, color: Color(0x22102A43)),
          ),
          Positioned(
            bottom: -40,
            right: -30,
            child: _GlowOrb(size: 180, color: Color(0x22F59E0B)),
          ),
        ],
      ),
    );
  }
}

class _GlowOrb extends StatelessWidget {
  const _GlowOrb({required this.size, required this.color});

  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(colors: [color, color.withValues(alpha: 0)]),
        ),
      ),
    );
  }
}

class _SurfaceCard extends StatelessWidget {
  const _SurfaceCard({required this.child, this.dark = false});

  final Widget child;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: dark
            ? const Color(0xFF102A43)
            : Colors.white.withValues(alpha: 0.78),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(
          color: dark
              ? Colors.white.withValues(alpha: 0.08)
              : const Color(0xFFDCE6EE),
        ),
        boxShadow: dark
            ? null
            : const [
                BoxShadow(
                  color: Color(0x12000000),
                  blurRadius: 24,
                  offset: Offset(0, 10),
                ),
              ],
      ),
      child: child,
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: Colors.white,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _HeroInfoChip extends StatelessWidget {
  const _HeroInfoChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            value,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: const Color(0xFFC8D6E5),
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _StatPill extends StatelessWidget {
  const _StatPill({
    required this.label,
    required this.value,
    required this.background,
    required this.foreground,
  });

  final String label;
  final String value;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            value,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: foreground,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: foreground,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _LegendChip extends StatelessWidget {
  const _LegendChip({
    required this.label,
    required this.foreground,
    required this.background,
  });

  final String label;
  final Color foreground;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: foreground,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _OptionTile extends StatelessWidget {
  const _OptionTile({
    required this.label,
    required this.text,
    required this.selected,
    required this.icon,
    required this.onTap,
  });

  final String label;
  final String text;
  final bool selected;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return InkWell(
      borderRadius: BorderRadius.circular(22),
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: selected ? const Color(0xFFD9F7F3) : const Color(0xFFF7FAFC),
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: selected ? const Color(0xFF19B7A6) : const Color(0xFFDCE6EE),
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: selected ? const Color(0xFF19B7A6) : Colors.white,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: selected
                      ? const Color(0xFF19B7A6)
                      : const Color(0xFFDCE6EE),
                ),
              ),
              alignment: Alignment.center,
              child: Text(
                label,
                style: theme.textTheme.titleSmall?.copyWith(
                  color: selected ? Colors.white : theme.colorScheme.onSurface,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Text(
                text,
                style: theme.textTheme.bodyLarge?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            const SizedBox(width: 10),
            Icon(
              icon,
              color: selected
                  ? const Color(0xFF0E766B)
                  : theme.colorScheme.onSurfaceVariant,
            ),
          ],
        ),
      ),
    );
  }
}

class _JudgeCard extends StatelessWidget {
  const _JudgeCard({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return InkWell(
      borderRadius: BorderRadius.circular(22),
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 18),
        decoration: BoxDecoration(
          color: selected ? const Color(0xFFD9F7F3) : Colors.white,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: selected ? const Color(0xFF19B7A6) : const Color(0xFFDCE6EE),
            width: selected ? 1.5 : 1,
          ),
        ),
        child: Column(
          children: [
            Icon(
              selected ? Icons.task_alt_rounded : Icons.radio_button_unchecked,
              size: 28,
              color: selected
                  ? const Color(0xFF0E766B)
                  : theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 10),
            Text(
              label,
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({
    required this.title,
    required this.detail,
    required this.actionLabel,
    required this.onAction,
  });

  final String title;
  final String detail;
  final String actionLabel;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return _SurfaceCard(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.sticky_note_2_outlined,
            size: 48,
            color: theme.colorScheme.primary,
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: theme.textTheme.headlineSmall?.copyWith(
              fontWeight: FontWeight.w800,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 10),
          Text(
            detail,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 18),
          FilledButton(onPressed: onAction, child: Text(actionLabel)),
        ],
      ),
    );
  }
}

enum _SubmitDecision { cancel, submit }

enum _LeaveDecision { cancel, discard, save }

enum _LocalDraftRestoreDecision { useLocal, useServer }

class _LocalDraftResolution {
  const _LocalDraftResolution({
    required this.canRestore,
    required this.hasConflict,
    required this.serverAnswersUpdatedAt,
    this.localUpdatedAt,
  });

  final bool canRestore;
  final bool hasConflict;
  final DateTime? serverAnswersUpdatedAt;
  final DateTime? localUpdatedAt;
}

class _PendingAntiCheatEvent {
  const _PendingAntiCheatEvent({
    required this.eventType,
    required this.metadata,
  });

  final String eventType;
  final Map<String, Object?> metadata;
}

const _missingValue = Object();

bool _isOfflineConnectivity(List<ConnectivityResult> results) {
  return results.isEmpty ||
      results.every((result) => result == ConnectivityResult.none);
}

List<String> _connectivityNames(List<ConnectivityResult> results) {
  return results.map((result) => result.name).toList(growable: false);
}

DateTime? _latestAnswerUpdatedAt(List<SessionAnswerModel> answers) {
  DateTime? latest;
  for (final answer in answers) {
    final updatedAt = answer.updatedAt;
    if (updatedAt == null) {
      continue;
    }
    if (latest == null || updatedAt.isAfter(latest)) {
      latest = updatedAt;
    }
  }
  return latest;
}

String _formatOptionalDateTime(DateTime? value) {
  if (value == null) {
    return '未知';
  }
  return DateFormat('M月d日 HH:mm:ss').format(value);
}

String _questionTypeLabel(String type) {
  switch (type.toUpperCase()) {
    case 'SINGLE':
      return '单选题';
    case 'MULTI':
      return '多选题';
    case 'JUDGE':
      return '判断题';
    case 'FILL':
      return '填空题';
    case 'SHORT':
      return '简答题';
    default:
      return type;
  }
}

String _formatDuration(Duration duration) {
  final safe = duration.isNegative ? Duration.zero : duration;
  final hours = safe.inHours;
  final minutes = safe.inMinutes.remainder(60);
  final seconds = safe.inSeconds.remainder(60);

  if (hours > 0) {
    return '${hours.toString().padLeft(2, '0')}:'
        '${minutes.toString().padLeft(2, '0')}:'
        '${seconds.toString().padLeft(2, '0')}';
  }

  return '${safe.inMinutes.toString().padLeft(2, '0')}:'
      '${seconds.toString().padLeft(2, '0')}';
}
