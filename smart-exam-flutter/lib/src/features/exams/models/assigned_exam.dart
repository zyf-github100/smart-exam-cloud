class AssignedExam {
  const AssignedExam({
    required this.examId,
    required this.paperId,
    required this.title,
    required this.startTime,
    required this.endTime,
    required this.antiCheatLevel,
    required this.status,
    required this.sessionId,
    required this.sessionStatus,
    required this.sessionStartTime,
    required this.sessionSubmitTime,
  });

  final String examId;
  final String paperId;
  final String title;
  final DateTime? startTime;
  final DateTime? endTime;
  final int antiCheatLevel;
  final String status;
  final String sessionId;
  final String sessionStatus;
  final DateTime? sessionStartTime;
  final DateTime? sessionSubmitTime;

  factory AssignedExam.fromJson(Map<String, dynamic> json) {
    return AssignedExam(
      examId: '${json['examId'] ?? ''}',
      paperId: '${json['paperId'] ?? ''}',
      title: '${json['title'] ?? ''}',
      startTime: _parseDateTime(json['startTime']),
      endTime: _parseDateTime(json['endTime']),
      antiCheatLevel: _parseInt(json['antiCheatLevel']),
      status: '${json['status'] ?? ''}',
      sessionId: '${json['sessionId'] ?? ''}',
      sessionStatus: '${json['sessionStatus'] ?? ''}',
      sessionStartTime: _parseDateTime(json['sessionStartTime']),
      sessionSubmitTime: _parseDateTime(json['sessionSubmitTime']),
    );
  }

  bool get hasSession => sessionId.isNotEmpty;

  bool get isRunning => status.toUpperCase() == 'RUNNING';

  bool get isFinished => status.toUpperCase() == 'FINISHED';

  bool get isNotStarted => status.toUpperCase() == 'NOT_STARTED';

  bool get isSubmitted {
    final normalized = sessionStatus.toUpperCase();
    return normalized == 'SUBMITTED' || normalized == 'FORCE_SUBMITTED';
  }

  bool get canStartOrResume => isRunning && !isSubmitted;

  bool get hasSubmittedSession => hasSession && isSubmitted;

  int get sortGroup {
    if (isRunning) return 0;
    if (isNotStarted) return 1;
    return 2;
  }
}

class StartExamResult {
  const StartExamResult({
    required this.sessionId,
    required this.serverTime,
    required this.timeLimitSeconds,
  });

  final String sessionId;
  final DateTime? serverTime;
  final int timeLimitSeconds;

  factory StartExamResult.fromJson(Map<String, dynamic> json) {
    return StartExamResult(
      sessionId: '${json['sessionId'] ?? ''}',
      serverTime: _parseDateTime(json['serverTime']),
      timeLimitSeconds: _parseInt(json['timeLimitSeconds']),
    );
  }
}

DateTime? _parseDateTime(Object? value) {
  final raw = '$value'.trim();
  if (raw.isEmpty || raw == 'null') {
    return null;
  }
  return DateTime.tryParse(raw);
}

int _parseInt(Object? value) {
  if (value is int) {
    return value;
  }
  return int.tryParse('$value') ?? 0;
}
