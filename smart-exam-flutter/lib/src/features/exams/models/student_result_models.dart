class StudentSessionResult {
  const StudentSessionResult({
    required this.sessionId,
    required this.examId,
    required this.sessionStatus,
    required this.submittedAt,
    required this.detailReleased,
    required this.detailMessage,
    required this.ready,
    required this.taskStatus,
    required this.message,
    required this.summary,
    required this.questions,
  });

  final String sessionId;
  final String examId;
  final String sessionStatus;
  final DateTime? submittedAt;
  final bool detailReleased;
  final String detailMessage;
  final bool ready;
  final String taskStatus;
  final String message;
  final ResultSummary summary;
  final List<QuestionResult> questions;

  factory StudentSessionResult.fromJson(Map<String, dynamic> json) {
    return StudentSessionResult(
      sessionId: '${json['sessionId'] ?? ''}',
      examId: '${json['examId'] ?? ''}',
      sessionStatus: '${json['sessionStatus'] ?? ''}',
      submittedAt: _parseDateTime(json['submittedAt']),
      detailReleased: json['detailReleased'] == true,
      detailMessage: '${json['detailMessage'] ?? ''}',
      ready: json['ready'] == true,
      taskStatus: '${json['taskStatus'] ?? ''}',
      message: '${json['message'] ?? ''}',
      summary: ResultSummary.fromJson(
        json['summary'] is Map<String, dynamic>
            ? json['summary'] as Map<String, dynamic>
            : const <String, dynamic>{},
      ),
      questions:
          (json['questions'] as List<dynamic>? ?? const [])
              .whereType<Map<String, dynamic>>()
              .map(QuestionResult.fromJson)
              .toList(growable: false)
            ..sort((left, right) => left.orderNo.compareTo(right.orderNo)),
    );
  }
}

class ResultSummary {
  const ResultSummary({
    required this.objectiveScore,
    required this.subjectiveScore,
    required this.totalScore,
    required this.publishedAt,
  });

  final double? objectiveScore;
  final double? subjectiveScore;
  final double? totalScore;
  final DateTime? publishedAt;

  factory ResultSummary.fromJson(Map<String, dynamic> json) {
    return ResultSummary(
      objectiveScore: _parseDouble(json['objectiveScore']),
      subjectiveScore: _parseDouble(json['subjectiveScore']),
      totalScore: _parseDouble(json['totalScore']),
      publishedAt: _parseDateTime(json['publishedAt']),
    );
  }
}

class QuestionResult {
  const QuestionResult({
    required this.questionId,
    required this.orderNo,
    required this.type,
    required this.stem,
    required this.analysis,
    required this.options,
    required this.standardAnswer,
    required this.myAnswer,
    required this.maxScore,
    required this.gotScore,
    required this.objective,
    required this.correct,
  });

  final String questionId;
  final int orderNo;
  final String type;
  final String stem;
  final String? analysis;
  final List<QuestionResultOption> options;
  final Object? standardAnswer;
  final Object? myAnswer;
  final double? maxScore;
  final double? gotScore;
  final bool objective;
  final bool correct;

  factory QuestionResult.fromJson(Map<String, dynamic> json) {
    return QuestionResult(
      questionId: '${json['questionId'] ?? ''}',
      orderNo: _parseInt(json['orderNo']),
      type: '${json['type'] ?? ''}',
      stem: '${json['stem'] ?? ''}',
      analysis: json['analysis'] == null ? null : '${json['analysis']}',
      options: (json['options'] as List<dynamic>? ?? const [])
          .whereType<Map<String, dynamic>>()
          .map(QuestionResultOption.fromJson)
          .toList(growable: false),
      standardAnswer: json['standardAnswer'],
      myAnswer: json['myAnswer'],
      maxScore: _parseDouble(json['maxScore']),
      gotScore: _parseDouble(json['gotScore']),
      objective: json['objective'] == true,
      correct: json['correct'] == true,
    );
  }
}

class QuestionResultOption {
  const QuestionResultOption({required this.key, required this.text});

  final String key;
  final String text;

  factory QuestionResultOption.fromJson(Map<String, dynamic> json) {
    return QuestionResultOption(
      key: '${json['key'] ?? ''}',
      text: '${json['text'] ?? ''}',
    );
  }
}

DateTime? _parseDateTime(Object? value) {
  final raw = '$value'.trim();
  if (raw.isEmpty || raw == 'null') {
    return null;
  }
  return DateTime.tryParse(raw) ??
      DateTime.tryParse(raw.replaceFirst(' ', 'T'));
}

double? _parseDouble(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is num) {
    return value.toDouble();
  }
  final raw = '$value'.trim();
  if (raw.isEmpty || raw == 'null') {
    return null;
  }
  return double.tryParse(raw);
}

int _parseInt(Object? value) {
  if (value is int) {
    return value;
  }
  return int.tryParse('$value') ?? 0;
}
