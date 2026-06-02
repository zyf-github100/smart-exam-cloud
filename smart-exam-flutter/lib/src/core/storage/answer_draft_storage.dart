import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../../features/exams/models/exam_session_models.dart';

class AnswerDraftStorage {
  static const _prefix = 'smart_exam_flutter_answer_draft_';

  Future<LocalAnswerDraftSnapshot?> read(String sessionId) async {
    final preferences = await SharedPreferences.getInstance();
    final key = _keyFor(sessionId);
    final raw = preferences.getString(key);
    if (raw == null || raw.isEmpty) {
      return null;
    }

    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      return LocalAnswerDraftSnapshot.fromJson(decoded);
    } catch (_) {
      await preferences.remove(key);
      return null;
    }
  }

  Future<void> write(LocalAnswerDraftSnapshot snapshot) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(
      _keyFor(snapshot.sessionId),
      jsonEncode(snapshot.toJson()),
    );
  }

  Future<void> clear(String sessionId) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.remove(_keyFor(sessionId));
  }

  String _keyFor(String sessionId) => '$_prefix$sessionId';
}

class LocalAnswerDraftSnapshot {
  const LocalAnswerDraftSnapshot({
    required this.sessionId,
    required this.examId,
    required this.paperId,
    required this.currentQuestionId,
    required this.updatedAt,
    required this.serverAnswersUpdatedAt,
    required this.drafts,
  });

  final String sessionId;
  final String examId;
  final String paperId;
  final String currentQuestionId;
  final DateTime updatedAt;
  final DateTime? serverAnswersUpdatedAt;
  final List<AnswerDraft> drafts;

  factory LocalAnswerDraftSnapshot.fromJson(Map<String, dynamic> json) {
    final rawDrafts = json['drafts'] as List<dynamic>? ?? const [];

    return LocalAnswerDraftSnapshot(
      sessionId: '${json['sessionId'] ?? ''}',
      examId: '${json['examId'] ?? ''}',
      paperId: '${json['paperId'] ?? ''}',
      currentQuestionId: '${json['currentQuestionId'] ?? ''}',
      updatedAt: _parseDateTime(json['updatedAt']) ?? DateTime.now(),
      serverAnswersUpdatedAt: _parseDateTime(json['serverAnswersUpdatedAt']),
      drafts: rawDrafts
          .whereType<Map<String, dynamic>>()
          .map(
            (item) => AnswerDraft(
              questionId: '${item['questionId'] ?? ''}',
              answerContent: item['answerContent'],
              markedForReview: item['markedForReview'] == true,
            ),
          )
          .where((item) => item.questionId.isNotEmpty)
          .toList(growable: false),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'sessionId': sessionId,
      'examId': examId,
      'paperId': paperId,
      'currentQuestionId': currentQuestionId,
      'updatedAt': updatedAt.toIso8601String(),
      'serverAnswersUpdatedAt': serverAnswersUpdatedAt?.toIso8601String(),
      'drafts': drafts.map((item) => item.toJson()).toList(growable: false),
    };
  }
}

DateTime? _parseDateTime(Object? value) {
  final raw = '$value'.trim();
  if (raw.isEmpty || raw == 'null') {
    return null;
  }
  return DateTime.tryParse(raw);
}
