import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_exception.dart';
import '../app/smart_exam_controller.dart';
import 'models/assigned_exam.dart';
import 'models/student_result_models.dart';

class ExamResultPage extends StatefulWidget {
  const ExamResultPage({
    super.key,
    required this.controller,
    required this.sessionId,
    this.exam,
  });

  final SmartExamController controller;
  final String sessionId;
  final AssignedExam? exam;

  @override
  State<ExamResultPage> createState() => _ExamResultPageState();
}

class _ExamResultPageState extends State<ExamResultPage> {
  StudentSessionResult? _result;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadResult();
  }

  Future<void> _loadResult({bool showFeedback = false}) async {
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final result = await widget.controller.fetchStudentSessionResult(
        widget.sessionId,
      );

      if (!mounted) {
        return;
      }

      setState(() {
        _result = result;
        _loading = false;
      });

      if (showFeedback) {
        _showSnackBar('成绩状态已刷新。');
      }
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }

      setState(() {
        _loading = false;
        _error = error.message;
      });

      if (showFeedback) {
        _showSnackBar(error.message);
      }
    }
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFF8FBFD), Color(0xFFEFF6F8), Color(0xFFE8F0F4)],
          ),
        ),
        child: SafeArea(
          child: RefreshIndicator(
            onRefresh: () => _loadResult(showFeedback: true),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
              children: [
                _ResultTopBar(
                  title: widget.exam?.title ?? '成绩与解析',
                  onBack: () {
                    Navigator.of(context).maybePop();
                  },
                  onRefresh: _loading
                      ? null
                      : () {
                          _loadResult(showFeedback: true);
                        },
                ),
                const SizedBox(height: 18),
                _ResultHero(exam: widget.exam, sessionId: widget.sessionId),
                const SizedBox(height: 18),
                if (_loading)
                  const _MessagePanel(
                    icon: Icons.hourglass_top_rounded,
                    title: '正在加载成绩',
                    detail: '正在同步评阅结果与题目解析状态。',
                  )
                else if (_error != null)
                  _MessagePanel(
                    icon: Icons.error_outline_rounded,
                    title: '成绩加载失败',
                    detail: _error!,
                    actionLabel: '重试',
                    onAction: () {
                      _loadResult(showFeedback: true);
                    },
                  )
                else if (_result != null && !_result!.ready)
                  _WaitingPanel(result: _result!)
                else if (_result != null) ...[
                  _SummaryPanel(result: _result!),
                  if (!_result!.detailReleased) ...[
                    const SizedBox(height: 14),
                    _LockedDetailNotice(message: _result!.detailMessage),
                  ],
                  const SizedBox(height: 18),
                  _QuestionResultsSection(result: _result!),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ResultTopBar extends StatelessWidget {
  const _ResultTopBar({
    required this.title,
    required this.onBack,
    required this.onRefresh,
  });

  final String title;
  final VoidCallback onBack;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Row(
      children: [
        IconButton.filledTonal(
          onPressed: onBack,
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '成绩查看',
                style: theme.textTheme.labelLarge?.copyWith(
                  color: theme.colorScheme.primary,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleLarge,
              ),
            ],
          ),
        ),
        IconButton.filledTonal(
          onPressed: onRefresh,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    );
  }
}

class _ResultHero extends StatelessWidget {
  const _ResultHero({required this.exam, required this.sessionId});

  final AssignedExam? exam;
  final String sessionId;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return _SurfacePanel(
      dark: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const _Pill(label: 'Result View', dark: true),
              const Spacer(),
              Icon(
                Icons.verified_rounded,
                color: Colors.white.withValues(alpha: 0.78),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            exam?.title ?? '考试成绩',
            style: theme.textTheme.headlineMedium?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            exam == null ? '查看当前会话的评阅进度、总分与题目解析。' : _formatExamWindow(exam!),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: const Color(0xFFC8D6E5),
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _MetaChip(
                icon: Icons.tag_rounded,
                label: '会话 $sessionId',
                dark: true,
              ),
              if (exam?.sessionSubmitTime != null)
                _MetaChip(
                  icon: Icons.task_alt_rounded,
                  label:
                      '交卷 ${DateFormat('M月d日 HH:mm').format(exam!.sessionSubmitTime!)}',
                  dark: true,
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _WaitingPanel extends StatelessWidget {
  const _WaitingPanel({required this.result});

  final StudentSessionResult result;

  @override
  Widget build(BuildContext context) {
    return _SurfacePanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _Pill(label: '评阅中'),
          const SizedBox(height: 16),
          Text('成绩尚未就绪', style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 8),
          Text(
            result.message.isNotEmpty ? result.message : '主观题完成评分后即可查看完整成绩。',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 18),
          _InfoLine(
            icon: Icons.rule_folder_outlined,
            label: '任务状态',
            value: _taskStatusLabel(result.taskStatus),
          ),
          const Divider(height: 28),
          _InfoLine(
            icon: Icons.access_time_rounded,
            label: '交卷时间',
            value: _formatDateTime(result.submittedAt),
          ),
        ],
      ),
    );
  }
}

class _SummaryPanel extends StatelessWidget {
  const _SummaryPanel({required this.result});

  final StudentSessionResult result;

  @override
  Widget build(BuildContext context) {
    final summary = result.summary;

    return _SurfacePanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const _Pill(label: '成绩总览'),
              const Spacer(),
              Text(
                _taskStatusLabel(result.taskStatus),
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: _ScoreBox(
                  label: '客观题',
                  value: _formatScore(summary.objectiveScore),
                  tone: const Color(0xFF0E766B),
                  background: const Color(0xFFD9F7F3),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ScoreBox(
                  label: '主观题',
                  value: _formatScore(summary.subjectiveScore),
                  tone: const Color(0xFF305A86),
                  background: const Color(0xFFE4ECF8),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ScoreBox(
                  label: '总分',
                  value: _formatScore(summary.totalScore),
                  tone: const Color(0xFF9A5800),
                  background: const Color(0xFFFFE8CC),
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          _InfoLine(
            icon: Icons.publish_rounded,
            label: '发布时间',
            value: _formatDateTime(summary.publishedAt),
          ),
          const Divider(height: 28),
          _InfoLine(
            icon: Icons.visibility_rounded,
            label: '解析开放',
            value: result.detailReleased ? '已开放' : '暂未开放',
          ),
        ],
      ),
    );
  }
}

class _LockedDetailNotice extends StatelessWidget {
  const _LockedDetailNotice({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF4E8),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFFFFD6A8)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.lock_clock_rounded, color: Color(0xFFB75C00)),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message.isNotEmpty ? message : '标准答案与解析将在考试结束后或老师发布后开放。',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: const Color(0xFF8B4400),
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _QuestionResultsSection extends StatelessWidget {
  const _QuestionResultsSection({required this.result});

  final StudentSessionResult result;

  @override
  Widget build(BuildContext context) {
    if (result.questions.isEmpty) {
      return const _MessagePanel(
        icon: Icons.inbox_outlined,
        title: '暂无题目成绩',
        detail: '当前会话还没有返回题目级得分明细。',
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 2),
          child: Text('题目明细', style: Theme.of(context).textTheme.titleLarge),
        ),
        const SizedBox(height: 12),
        for (var index = 0; index < result.questions.length; index++) ...[
          _QuestionResultCard(
            question: result.questions[index],
            detailReleased: result.detailReleased,
          ),
          if (index != result.questions.length - 1) const SizedBox(height: 14),
        ],
      ],
    );
  }
}

class _QuestionResultCard extends StatelessWidget {
  const _QuestionResultCard({
    required this.question,
    required this.detailReleased,
  });

  final QuestionResult question;
  final bool detailReleased;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tone = _questionTone(question);

    return _SurfacePanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _Pill(label: '第 ${question.orderNo} 题'),
                    _Pill(label: _questionTypeLabel(question.type)),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              _ScoreBadge(
                label:
                    '${_formatScore(question.gotScore)} / ${_formatScore(question.maxScore)}',
                foreground: tone.foreground,
                background: tone.background,
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            question.stem,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w800,
            ),
          ),
          if (question.options.isNotEmpty) ...[
            const SizedBox(height: 14),
            for (final option in question.options) ...[
              _OptionLine(option: option),
              const SizedBox(height: 8),
            ],
          ],
          const Divider(height: 28),
          _AnswerLine(label: '我的答案', value: _formatAnswer(question.myAnswer)),
          const SizedBox(height: 10),
          _AnswerLine(
            label: '标准答案',
            value: detailReleased
                ? _formatAnswer(question.standardAnswer)
                : '待开放',
          ),
          const SizedBox(height: 10),
          _AnswerLine(
            label: '解析',
            value: detailReleased
                ? (question.analysis?.trim().isNotEmpty == true
                      ? question.analysis!.trim()
                      : '暂无解析')
                : '待开放',
          ),
        ],
      ),
    );
  }
}

class _SurfacePanel extends StatelessWidget {
  const _SurfacePanel({required this.child, this.dark = false});

  final Widget child;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: dark
            ? const Color(0xFF102A43)
            : Colors.white.withValues(alpha: 0.82),
        borderRadius: BorderRadius.circular(26),
        border: Border.all(
          color: dark
              ? Colors.white.withValues(alpha: 0.08)
              : const Color(0xFFDCE6EE),
        ),
        boxShadow: dark
            ? null
            : const [
                BoxShadow(
                  color: Color(0x10000000),
                  blurRadius: 22,
                  offset: Offset(0, 10),
                ),
              ],
      ),
      child: child,
    );
  }
}

class _MessagePanel extends StatelessWidget {
  const _MessagePanel({
    required this.icon,
    required this.title,
    required this.detail,
    this.actionLabel,
    this.onAction,
  });

  final IconData icon;
  final String title;
  final String detail;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return _SurfacePanel(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48, color: theme.colorScheme.primary),
          const SizedBox(height: 16),
          Text(title, style: theme.textTheme.titleLarge),
          const SizedBox(height: 8),
          Text(
            detail,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 18),
            FilledButton(onPressed: onAction, child: Text(actionLabel!)),
          ],
        ],
      ),
    );
  }
}

class _ScoreBox extends StatelessWidget {
  const _ScoreBox({
    required this.label,
    required this.value,
    required this.tone,
    required this.background,
  });

  final String label;
  final String value;
  final Color tone;
  final Color background;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: 92),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: tone,
              fontWeight: FontWeight.w800,
            ),
          ),
          Text(
            value,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
              color: tone,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _InfoLine extends StatelessWidget {
  const _InfoLine({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Row(
      children: [
        Icon(icon, color: theme.colorScheme.primary),
        const SizedBox(width: 10),
        Text(
          label,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            fontWeight: FontWeight.w700,
          ),
        ),
        const Spacer(),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ],
    );
  }
}

class _AnswerLine extends StatelessWidget {
  const _AnswerLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: theme.textTheme.labelLarge?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 4),
        Text(value, style: theme.textTheme.bodyLarge),
      ],
    );
  }
}

class _OptionLine extends StatelessWidget {
  const _OptionLine({required this.option});

  final QuestionResultOption option;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFF7FAFC),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFDCE6EE)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            option.key,
            style: theme.textTheme.titleSmall?.copyWith(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(child: Text(option.text, style: theme.textTheme.bodyMedium)),
        ],
      ),
    );
  }
}

class _Pill extends StatelessWidget {
  const _Pill({required this.label, this.dark = false});

  final String label;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: dark
            ? Colors.white.withValues(alpha: 0.08)
            : const Color(0xFFF1F5F8),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: dark
              ? Colors.white.withValues(alpha: 0.08)
              : const Color(0xFFDCE6EE),
        ),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: dark
              ? Colors.white
              : Theme.of(context).colorScheme.onSurfaceVariant,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({
    required this.icon,
    required this.label,
    required this.dark,
  });

  final IconData icon;
  final String label;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final foreground = dark
        ? Colors.white
        : Theme.of(context).colorScheme.onSurface;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: dark
            ? Colors.white.withValues(alpha: 0.08)
            : const Color(0xFFF1F5F8),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: foreground),
          const SizedBox(width: 8),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 260),
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: foreground,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ScoreBadge extends StatelessWidget {
  const _ScoreBadge({
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
        '$label 分',
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: foreground,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _QuestionTone {
  const _QuestionTone({required this.foreground, required this.background});

  final Color foreground;
  final Color background;
}

_QuestionTone _questionTone(QuestionResult question) {
  if (!question.objective) {
    return const _QuestionTone(
      foreground: Color(0xFF305A86),
      background: Color(0xFFE4ECF8),
    );
  }
  if (question.correct) {
    return const _QuestionTone(
      foreground: Color(0xFF0E766B),
      background: Color(0xFFD9F7F3),
    );
  }
  return const _QuestionTone(
    foreground: Color(0xFF9A3412),
    background: Color(0xFFFFE3D5),
  );
}

String _formatAnswer(Object? value) {
  if (value == null) {
    return '未作答';
  }
  if (value is Iterable) {
    final values = value
        .map((item) => '$item'.trim())
        .where((item) => item.isNotEmpty && item != 'null')
        .toList(growable: false);
    return values.isEmpty ? '未作答' : values.join(', ');
  }
  if (value is bool) {
    return value ? '正确' : '错误';
  }
  if (value is Map) {
    return jsonEncode(value);
  }
  final text = '$value'.trim();
  if (text.isEmpty || text == 'null') {
    return '未作答';
  }
  return text;
}

String _formatScore(double? value) {
  if (value == null) {
    return '-';
  }
  if (value == value.roundToDouble()) {
    return value.round().toString();
  }
  return value
      .toStringAsFixed(2)
      .replaceFirst(RegExp(r'0+$'), '')
      .replaceFirst(RegExp(r'\.$'), '');
}

String _formatDateTime(DateTime? value) {
  if (value == null) {
    return '-';
  }
  return DateFormat('M月d日 HH:mm').format(value);
}

String _formatExamWindow(AssignedExam exam) {
  final start = exam.startTime;
  final end = exam.endTime;
  if (start == null || end == null) {
    return '考试时间待定';
  }
  return '${DateFormat('M月d日 HH:mm').format(start)} - ${DateFormat('HH:mm').format(end)}';
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
      return type.isEmpty ? '-' : type;
  }
}

String _taskStatusLabel(String raw) {
  switch (raw.toUpperCase()) {
    case 'PENDING':
      return '待评阅';
    case 'AUTO_DONE':
      return '自动判分完成';
    case 'MANUAL_REQUIRED':
      return '待人工评分';
    case 'DONE':
      return '已完成';
    default:
      return raw.isEmpty ? '-' : raw;
  }
}
