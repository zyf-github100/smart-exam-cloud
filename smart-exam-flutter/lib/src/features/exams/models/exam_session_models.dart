class ExamPaperModel {
  const ExamPaperModel({
    required this.sessionId,
    required this.examId,
    required this.paperId,
    required this.paperName,
    required this.totalScore,
    required this.timeLimitMinutes,
    required this.questions,
  });

  final String sessionId;
  final String examId;
  final String paperId;
  final String paperName;
  final int totalScore;
  final int timeLimitMinutes;
  final List<ExamPaperQuestionModel> questions;

  factory ExamPaperModel.fromJson(Map<String, dynamic> json) {
    final questions =
        (json['questions'] as List<dynamic>? ?? const [])
            .map(
              (item) =>
                  ExamPaperQuestionModel.fromJson(item as Map<String, dynamic>),
            )
            .toList(growable: false)
          ..sort((left, right) => left.orderNo.compareTo(right.orderNo));

    return ExamPaperModel(
      sessionId: '${json['sessionId'] ?? ''}',
      examId: '${json['examId'] ?? ''}',
      paperId: '${json['paperId'] ?? ''}',
      paperName: '${json['paperName'] ?? ''}',
      totalScore: _parseInt(json['totalScore']),
      timeLimitMinutes: _parseInt(json['timeLimitMinutes']),
      questions: questions,
    );
  }
}

class ExamPaperQuestionModel {
  const ExamPaperQuestionModel({
    required this.questionId,
    required this.type,
    required this.stem,
    required this.score,
    required this.orderNo,
    required this.options,
  });

  final String questionId;
  final String type;
  final String stem;
  final int score;
  final int orderNo;
  final List<ExamPaperOptionModel> options;

  factory ExamPaperQuestionModel.fromJson(Map<String, dynamic> json) {
    return ExamPaperQuestionModel(
      questionId: '${json['questionId'] ?? ''}',
      type: '${json['type'] ?? ''}',
      stem: '${json['stem'] ?? ''}',
      score: _parseInt(json['score']),
      orderNo: _parseInt(json['orderNo']),
      options: (json['options'] as List<dynamic>? ?? const [])
          .map(
            (item) =>
                ExamPaperOptionModel.fromJson(item as Map<String, dynamic>),
          )
          .toList(growable: false),
    );
  }

  bool get isSingleChoice => type.toUpperCase() == 'SINGLE';

  bool get isMultipleChoice => type.toUpperCase() == 'MULTI';

  bool get isJudge => type.toUpperCase() == 'JUDGE';

  bool get isFill => type.toUpperCase() == 'FILL';

  bool get isShort => type.toUpperCase() == 'SHORT';

  bool get usesTextInput => isFill || isShort;
}

class ExamPaperOptionModel {
  const ExamPaperOptionModel({required this.key, required this.text});

  final String key;
  final String text;

  factory ExamPaperOptionModel.fromJson(Map<String, dynamic> json) {
    return ExamPaperOptionModel(
      key: '${json['key'] ?? ''}',
      text: '${json['text'] ?? ''}',
    );
  }
}

class SessionAnswerModel {
  const SessionAnswerModel({
    required this.questionId,
    required this.answerContent,
    required this.markedForReview,
    required this.updatedAt,
  });

  final String questionId;
  final Object? answerContent;
  final bool markedForReview;
  final DateTime? updatedAt;

  factory SessionAnswerModel.fromJson(Map<String, dynamic> json) {
    return SessionAnswerModel(
      questionId: '${json['questionId'] ?? ''}',
      answerContent: json['answerContent'],
      markedForReview: json['markedForReview'] == true,
      updatedAt: _parseDateTime(json['updatedAt']),
    );
  }
}

class SubmitSessionResult {
  const SubmitSessionResult({
    required this.sessionId,
    required this.status,
    required this.submittedAt,
    required this.deadlineExceeded,
  });

  final String sessionId;
  final String status;
  final DateTime? submittedAt;
  final bool deadlineExceeded;

  factory SubmitSessionResult.fromJson(Map<String, dynamic> json) {
    return SubmitSessionResult(
      sessionId: '${json['sessionId'] ?? ''}',
      status: '${json['status'] ?? ''}',
      submittedAt: _parseDateTime(json['submittedAt']),
      deadlineExceeded: json['deadlineExceeded'] == true,
    );
  }
}

class AnswerDraft {
  const AnswerDraft({
    required this.questionId,
    this.answerContent,
    this.markedForReview = false,
  });

  final String questionId;
  final Object? answerContent;
  final bool markedForReview;

  AnswerDraft copyWith({
    Object? answerContent = _noValue,
    bool? markedForReview,
  }) {
    return AnswerDraft(
      questionId: questionId,
      answerContent: identical(answerContent, _noValue)
          ? this.answerContent
          : answerContent,
      markedForReview: markedForReview ?? this.markedForReview,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'questionId': questionId,
      'answerContent': answerContent,
      'markedForReview': markedForReview,
    };
  }
}

const _noValue = Object();

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
