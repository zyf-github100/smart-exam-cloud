import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';

import '../../core/network/api_exception.dart';
import '../app/smart_exam_controller.dart';
import '../exams/exam_precheck_page.dart';
import '../exams/exam_result_page.dart';
import '../exams/exam_session_page.dart';
import '../exams/models/assigned_exam.dart';

const _monthDayFormat = 'M月d日';
const _timeFormat = 'HH:mm';

class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.controller});

  final SmartExamController controller;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage>
    with SingleTickerProviderStateMixin {
  static const _tabs = [
    _NavTab(
      label: '首页',
      icon: Icons.grid_view_rounded,
      activeIcon: Icons.grid_view,
    ),
    _NavTab(
      label: '考试',
      icon: Icons.sticky_note_2_outlined,
      activeIcon: Icons.sticky_note_2_rounded,
    ),
    _NavTab(
      label: '记录',
      icon: Icons.show_chart_outlined,
      activeIcon: Icons.show_chart,
    ),
    _NavTab(
      label: '我的',
      icon: Icons.person_outline_rounded,
      activeIcon: Icons.person_rounded,
    ),
  ];

  late final AnimationController _animationController;
  int _currentIndex = 0;
  _ExamFilter _examFilter = _ExamFilter.all;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1100),
    )..forward();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  void _selectTab(int index) {
    if (_currentIndex == index) {
      return;
    }
    setState(() {
      _currentIndex = index;
    });
    _animationController
      ..reset()
      ..forward();
  }

  void _setExamFilter(_ExamFilter filter) {
    if (_examFilter == filter) {
      return;
    }
    setState(() {
      _examFilter = filter;
    });
  }

  Future<void> _refreshOverview() async {
    await widget.controller.refreshOverview();
  }

  Future<void> _startExam(AssignedExam exam) async {
    try {
      final approved = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (context) => ExamPrecheckPage(exam: exam)),
      );

      if (!mounted || approved != true) {
        return;
      }

      var sessionId = exam.sessionId;
      if (sessionId.isEmpty) {
        final result = await widget.controller.startExam(exam.examId);
        sessionId = result.sessionId;
      }

      if (sessionId.isEmpty) {
        throw const ApiException(message: '未获取到考试会话，请稍后重试。');
      }

      if (!mounted) {
        return;
      }

      final pageResult = await Navigator.of(context)
          .push<ExamSessionPageResult>(
            MaterialPageRoute(
              builder: (context) => ExamSessionPage(
                controller: widget.controller,
                exam: exam,
                sessionId: sessionId,
              ),
            ),
          );

      if (!mounted) {
        return;
      }

      await widget.controller.refreshOverview(silent: true);

      if (!mounted || pageResult?.submitted != true) {
        return;
      }

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            pageResult!.deadlineExceeded ? '答卷已提交，系统判定为超时交卷。' : '答卷已成功提交。',
          ),
        ),
      );
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _viewResult(AssignedExam exam) async {
    final sessionId = exam.sessionId;
    if (sessionId.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('当前考试暂无成绩可查看。')));
      return;
    }

    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (context) => ExamResultPage(
          controller: widget.controller,
          exam: exam,
          sessionId: sessionId,
        ),
      ),
    );

    if (!mounted) {
      return;
    }
    await widget.controller.refreshOverview(silent: true);
  }

  Future<void> _logout() async {
    await widget.controller.logout();
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final pages = [
      _DashboardView(
        animation: _animationController.view,
        controller: controller,
        onRefresh: _refreshOverview,
        onStartExam: _startExam,
      ),
      _ExamsView(
        animation: _animationController.view,
        controller: controller,
        filter: _examFilter,
        onFilterChanged: _setExamFilter,
        onRefresh: _refreshOverview,
        onStartExam: _startExam,
        onViewResult: _viewResult,
      ),
      _ResultsView(
        animation: _animationController.view,
        controller: controller,
        onRefresh: _refreshOverview,
        onViewResult: _viewResult,
      ),
      _ProfileView(
        animation: _animationController.view,
        controller: controller,
        onRefresh: _refreshOverview,
        onLogout: _logout,
      ),
    ];

    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        return Scaffold(
          backgroundColor: Colors.transparent,
          body: Stack(
            children: [
              const _Backdrop(),
              SafeArea(
                child: Column(
                  children: [
                    if (controller.isRefreshing)
                      const LinearProgressIndicator(
                        minHeight: 2,
                        backgroundColor: Colors.transparent,
                      ),
                    Expanded(
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 420),
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
                            child: SlideTransition(
                              position: slide,
                              child: child,
                            ),
                          );
                        },
                        child: KeyedSubtree(
                          key: ValueKey(_currentIndex),
                          child: pages[_currentIndex],
                        ),
                      ),
                    ),
                    _FloatingNavBar(
                      tabs: _tabs,
                      currentIndex: _currentIndex,
                      onSelect: _selectTab,
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _DashboardView extends StatelessWidget {
  const _DashboardView({
    required this.animation,
    required this.controller,
    required this.onRefresh,
    required this.onStartExam,
  });

  final Animation<double> animation;
  final SmartExamController controller;
  final Future<void> Function() onRefresh;
  final Future<void> Function(AssignedExam exam) onStartExam;

  @override
  Widget build(BuildContext context) {
    final featuredExam = controller.featuredExam;
    final displayName = controller.displayName;
    final userInitials = _resolveInitials(displayName);
    final todayExams = controller.assignedExams.take(3).toList(growable: false);

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
        children: [
          _Reveal(
            animation: animation,
            begin: 0.00,
            end: 0.18,
            child: _PageHeader(
              eyebrow: '智能考试',
              title: _formatHeaderDate(DateTime.now()),
              subtitle: featuredExam == null
                  ? '当前暂无待参加考试，请稍后再查看。'
                  : _buildHeaderSubtitle(featuredExam),
              trailing: _UserBadge(
                initials: userInitials,
                name: displayName,
                detail: controller.username,
              ),
            ),
          ),
          const SizedBox(height: 20),
          if (controller.overviewError != null) ...[
            _Reveal(
              animation: animation,
              begin: 0.04,
              end: 0.26,
              child: _InlineNotice(message: controller.overviewError!),
            ),
            const SizedBox(height: 16),
          ],
          _Reveal(
            animation: animation,
            begin: 0.08,
            end: 0.38,
            child: _HeroPanel(
              exam: featuredExam,
              onAction: featuredExam != null && featuredExam.canStartOrResume
                  ? () => onStartExam(featuredExam)
                  : onRefresh,
            ),
          ),
          const SizedBox(height: 24),
          _Reveal(
            animation: animation,
            begin: 0.22,
            end: 0.48,
            child: const _SectionHeader(
              eyebrow: '今日安排',
              title: '待参加考试',
              subtitle: '按时间顺序查看当前账号的考试安排。',
            ),
          ),
          const SizedBox(height: 14),
          _Reveal(
            animation: animation,
            begin: 0.28,
            end: 0.58,
            child: todayExams.isEmpty
                ? const _EmptyState(
                    title: '暂无考试安排',
                    subtitle: '老师发布考试后，会在这里自动显示。',
                  )
                : _ScheduleSheet(
                    items: todayExams,
                    startingExamId: controller.startingExamId,
                    onStartExam: onStartExam,
                  ),
          ),
          const SizedBox(height: 22),
          _Reveal(
            animation: animation,
            begin: 0.38,
            end: 0.68,
            child: const _SectionHeader(
              eyebrow: '状态概览',
              title: '当前进度',
              subtitle: '查看考试进行情况与交卷状态。',
            ),
          ),
          const SizedBox(height: 14),
          _Reveal(
            animation: animation,
            begin: 0.46,
            end: 0.88,
            child: _MomentumSheet(controller: controller),
          ),
        ],
      ),
    );
  }

  static String _buildHeaderSubtitle(AssignedExam exam) {
    final status = _statusLabel(exam);
    final window = _formatTimeWindow(exam);
    return '$status  |  $window';
  }
}

class _ExamsView extends StatelessWidget {
  const _ExamsView({
    required this.animation,
    required this.controller,
    required this.filter,
    required this.onFilterChanged,
    required this.onRefresh,
    required this.onStartExam,
    required this.onViewResult,
  });

  final Animation<double> animation;
  final SmartExamController controller;
  final _ExamFilter filter;
  final ValueChanged<_ExamFilter> onFilterChanged;
  final Future<void> Function() onRefresh;
  final Future<void> Function(AssignedExam exam) onStartExam;
  final Future<void> Function(AssignedExam exam) onViewResult;

  @override
  Widget build(BuildContext context) {
    final filteredExams = controller.assignedExams
        .where(filter.matches)
        .toList();

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
        children: [
          _Reveal(
            animation: animation,
            begin: 0.00,
            end: 0.18,
            child: const _PageHeader(
              eyebrow: '考试列表',
              title: '我的考试',
              subtitle: '查看待开始、进行中和已结束的考试。',
            ),
          ),
          const SizedBox(height: 20),
          _Reveal(
            animation: animation,
            begin: 0.10,
            end: 0.42,
            child: _Surface(
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: _ExamFilter.values
                        .map(
                          (item) => _FilterChip(
                            label: item.label,
                            active: filter == item,
                            onTap: () => onFilterChanged(item),
                          ),
                        )
                        .toList(growable: false),
                  ),
                  const SizedBox(height: 18),
                  if (filteredExams.isEmpty)
                    const _EmptyState(
                      title: '当前分类暂无考试',
                      subtitle: '可以切换分类查看，或稍后刷新重试。',
                      compact: true,
                    )
                  else
                    Column(
                      children: [
                        for (
                          var index = 0;
                          index < filteredExams.length;
                          index++
                        ) ...[
                          _ExamCard(
                            exam: filteredExams[index],
                            busy:
                                controller.startingExamId ==
                                filteredExams[index].examId,
                            onStartExam: filteredExams[index].canStartOrResume
                                ? () => onStartExam(filteredExams[index])
                                : null,
                            onViewResult:
                                filteredExams[index].hasSubmittedSession
                                ? () => onViewResult(filteredExams[index])
                                : null,
                          ),
                          if (index != filteredExams.length - 1)
                            const SizedBox(height: 14),
                        ],
                      ],
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),
          _Reveal(
            animation: animation,
            begin: 0.24,
            end: 0.70,
            child: _Surface(
              dark: true,
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _CapsuleLabel(label: '考试提醒'),
                  const SizedBox(height: 14),
                  Text(
                    '进入考试前，请先确认设备与网络状态。',
                    style: GoogleFonts.spaceGrotesk(
                      color: Colors.white,
                      fontSize: 26,
                      fontWeight: FontWeight.w700,
                      height: 1.1,
                    ),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    '考试开始后请保持网络稳定，尽量不要切换应用。已交卷的考试仍可查看记录，但不能再次进入答题。',
                    style: TextStyle(
                      color: Color(0xFFB7C8D8),
                      fontSize: 14,
                      height: 1.5,
                    ),
                  ),
                  const SizedBox(height: 18),
                  const Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      _DetailChip(label: '保持网络稳定'),
                      _DetailChip(label: '按时进入考场'),
                      _DetailChip(label: '避免频繁切屏'),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ResultsView extends StatelessWidget {
  const _ResultsView({
    required this.animation,
    required this.controller,
    required this.onRefresh,
    required this.onViewResult,
  });

  final Animation<double> animation;
  final SmartExamController controller;
  final Future<void> Function() onRefresh;
  final Future<void> Function(AssignedExam exam) onViewResult;

  @override
  Widget build(BuildContext context) {
    final recentItems = controller.assignedExams
        .where((exam) => exam.hasSession || exam.isFinished)
        .take(3)
        .toList(growable: false);

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
        children: [
          _Reveal(
            animation: animation,
            begin: 0.00,
            end: 0.18,
            child: const _PageHeader(
              eyebrow: '考试记录',
              title: '交卷情况',
              subtitle: '查看最近考试的进入与交卷状态。',
            ),
          ),
          const SizedBox(height: 20),
          _Reveal(
            animation: animation,
            begin: 0.10,
            end: 0.42,
            child: _ResultHero(controller: controller),
          ),
          const SizedBox(height: 18),
          _Reveal(
            animation: animation,
            begin: 0.24,
            end: 0.64,
            child: _Surface(
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _CapsuleLabel(label: '最近动态', dark: false),
                  const SizedBox(height: 14),
                  if (recentItems.isEmpty)
                    const _EmptyState(
                      title: '暂无考试记录',
                      subtitle: '开始考试或交卷后，这里会显示最近动态。',
                      compact: true,
                    )
                  else
                    Column(
                      children: [
                        for (
                          var index = 0;
                          index < recentItems.length;
                          index++
                        ) ...[
                          _ExamLine(
                            title: recentItems[index].title,
                            time: recentItems[index].sessionStatus.isNotEmpty
                                ? _sessionStatusLabel(
                                    recentItems[index].sessionStatus,
                                  )
                                : _examStatusLabel(recentItems[index].status),
                            detail: _recentDetail(recentItems[index]),
                            tone: _toneForExam(recentItems[index]),
                            compact: true,
                            trailing: recentItems[index].hasSubmittedSession
                                ? _MiniActionButton(
                                    label: '成绩',
                                    onTap: () =>
                                        onViewResult(recentItems[index]),
                                  )
                                : null,
                          ),
                          if (index != recentItems.length - 1) const Divider(),
                        ],
                      ],
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  static String _recentDetail(AssignedExam exam) {
    final submittedAt = exam.sessionSubmitTime;
    if (submittedAt != null) {
      return '已交卷 ${DateFormat('$_monthDayFormat $_timeFormat').format(submittedAt)}';
    }
    if (exam.sessionStartTime != null) {
      return '已进入 ${DateFormat('$_monthDayFormat $_timeFormat').format(exam.sessionStartTime!)}';
    }
    return _formatTimeWindow(exam);
  }
}

class _ProfileView extends StatelessWidget {
  const _ProfileView({
    required this.animation,
    required this.controller,
    required this.onRefresh,
    required this.onLogout,
  });

  final Animation<double> animation;
  final SmartExamController controller;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    final me = controller.me;
    final displayName = controller.displayName;

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
        children: [
          _Reveal(
            animation: animation,
            begin: 0.00,
            end: 0.18,
            child: const _PageHeader(
              eyebrow: '个人中心',
              title: '账号信息',
              subtitle: '查看个人资料与当前考试状态。',
            ),
          ),
          const SizedBox(height: 20),
          _Reveal(
            animation: animation,
            begin: 0.10,
            end: 0.42,
            child: _Surface(
              dark: true,
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 28,
                        backgroundColor: const Color(0x3319B7A6),
                        child: Text(
                          _resolveInitials(displayName),
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              displayName,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 22,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '${controller.username}  |  ${_roleLabel(controller.roleLabel)}',
                              style: const TextStyle(
                                color: Color(0xFFB7C8D8),
                                fontSize: 13,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      _DetailChip(label: '进行中 ${controller.runningExamCount}'),
                      _DetailChip(label: '待开始 ${controller.upcomingExamCount}'),
                      _DetailChip(label: '已交卷 ${controller.submittedCount}'),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),
          _Reveal(
            animation: animation,
            begin: 0.24,
            end: 0.64,
            child: _Surface(
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _SettingLine(
                    icon: Icons.badge_outlined,
                    title: '用户编号',
                    detail: me?.id ?? controller.session?.user.id ?? '-',
                  ),
                  const Divider(),
                  _SettingLine(
                    icon: Icons.person_outline_rounded,
                    title: '当前身份',
                    detail: _roleLabel(controller.roleLabel),
                  ),
                  const Divider(),
                  _SettingLine(
                    icon: Icons.verified_user_outlined,
                    title: '账户状态',
                    detail: _accountStatusLabel(me?.profile.status),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),
          _Reveal(
            animation: animation,
            begin: 0.36,
            end: 0.82,
            child: _Surface(
              padding: const EdgeInsets.all(22),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _CapsuleLabel(label: '操作', dark: false),
                  const SizedBox(height: 14),
                  Text(
                    '退出当前账号',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    '退出后将返回登录页，需要重新输入账号密码。',
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 18),
                  FilledButton(
                    onPressed: () {
                      onLogout();
                    },
                    child: const Text('退出登录'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroPanel extends StatelessWidget {
  const _HeroPanel({required this.exam, required this.onAction});

  final AssignedExam? exam;
  final VoidCallback onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final exam = this.exam;
    final dialData = _buildDialData(exam);

    return _Surface(
      dark: true,
      padding: const EdgeInsets.all(24),
      child: Stack(
        children: [
          Positioned(
            top: -30,
            right: -10,
            child: _GlowOrb(size: 160, color: const Color(0x3319B7A6)),
          ),
          Positioned(
            bottom: -50,
            left: -30,
            child: _GlowOrb(size: 200, color: const Color(0x22FFFFFF)),
          ),
          LayoutBuilder(
            builder: (context, constraints) {
              final vertical = constraints.maxWidth < 380;

              final info = Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      _CapsuleLabel(
                        label: exam == null ? '暂无考试' : _heroCapsuleLabel(exam),
                      ),
                      const Spacer(),
                      _DetailChip(
                        label: exam == null ? '空闲中' : _statusLabel(exam),
                        dark: true,
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),
                  Text(
                    exam?.title ?? '等待下一场考试',
                    style: GoogleFonts.spaceGrotesk(
                      color: Colors.white,
                      fontSize: 34,
                      fontWeight: FontWeight.w700,
                      height: 0.98,
                    ),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    exam == null
                        ? '当前还没有可参加的考试，新的考试发布后会自动出现在这里。'
                        : _heroDescription(exam),
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: const Color(0xFFB7C8D8),
                    ),
                  ),
                  const SizedBox(height: 18),
                  Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: [
                      _HeroMeta(
                        icon: Icons.schedule_rounded,
                        label: exam == null ? '下拉可刷新' : _formatTimeWindow(exam),
                      ),
                      _HeroMeta(
                        icon: Icons.rule_folder_outlined,
                        label: exam == null
                            ? '学生端'
                            : '防作弊等级 ${exam.antiCheatLevel}',
                      ),
                      _HeroMeta(
                        icon: Icons.badge_outlined,
                        label: exam == null
                            ? '等待分配'
                            : exam.hasSession
                            ? '已创建考试会话'
                            : '尚未进入考试',
                      ),
                    ],
                  ),
                  const SizedBox(height: 22),
                  FilledButton(
                    onPressed: onAction,
                    child: Text(
                      exam == null
                          ? '刷新考试列表'
                          : exam.canStartOrResume
                          ? (exam.hasSession ? '继续作答' : '进入考试')
                          : '刷新状态',
                    ),
                  ),
                ],
              );

              final dial = _CountdownDial(
                progress: dialData.progress,
                time: dialData.timeText,
                label: dialData.label,
              );

              if (vertical) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    info,
                    const SizedBox(height: 24),
                    Align(alignment: Alignment.centerRight, child: dial),
                  ],
                );
              }

              return Row(
                children: [
                  Expanded(child: info),
                  const SizedBox(width: 18),
                  dial,
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _MomentumSheet extends StatelessWidget {
  const _MomentumSheet({required this.controller});

  final SmartExamController controller;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final total = controller.assignedExams.length;
    final completion = total == 0 ? 0.0 : controller.submittedCount / total;

    return _Surface(
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _CapsuleLabel(label: '完成度', dark: false),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  '${(completion * 100).round()}%',
                  style: theme.textTheme.displayMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: 18),
              Expanded(
                flex: 2,
                child: Text(
                  total == 0
                      ? '当前还没有分配到考试，可以稍后再次查看。'
                      : '共 $total 场考试，已交卷 ${controller.submittedCount} 场，进行中考试仍可从考试页进入。',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          _BarStrip(
            values: [
              _safeRatio(controller.runningExamCount, total),
              _safeRatio(controller.upcomingExamCount, total),
              _safeRatio(controller.completedExamCount, total),
              completion,
            ],
            labels: const ['进行中', '待开始', '已结束', '已交卷'],
          ),
          const SizedBox(height: 20),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _DetailChip(label: '进行中 ${controller.runningExamCount}'),
              _DetailChip(label: '待开始 ${controller.upcomingExamCount}'),
              _DetailChip(label: '已结束 ${controller.completedExamCount}'),
            ],
          ),
          const SizedBox(height: 20),
          Divider(color: theme.colorScheme.outline.withValues(alpha: 0.6)),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(child: Text('接下来', style: theme.textTheme.titleMedium)),
              OutlinedButton(onPressed: () {}, child: const Text('查看考试')),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            controller.featuredExam == null
                ? '当前没有待处理考试，保持关注后续安排即可。'
                : '${controller.featuredExam!.title} 是你当前最需要关注的一场考试。',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _ResultHero extends StatelessWidget {
  const _ResultHero({required this.controller});

  final SmartExamController controller;

  @override
  Widget build(BuildContext context) {
    final total = controller.assignedExams.length;
    final submitted = controller.submittedCount;

    return _Surface(
      dark: true,
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const _CapsuleLabel(label: '记录概览'),
              const Spacer(),
              _DetailChip(
                label: '已结束 ${controller.completedExamCount} 场',
                dark: true,
              ),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                '$submitted',
                style: GoogleFonts.spaceGrotesk(
                  color: Colors.white,
                  fontSize: 72,
                  fontWeight: FontWeight.w700,
                  height: 0.92,
                ),
              ),
              const SizedBox(width: 12),
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  '/ $total',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: const Color(0xFFB7C8D8),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '已交卷',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(color: Colors.white),
          ),
          const SizedBox(height: 18),
          _BarStrip(
            values: [
              _safeRatio(controller.runningExamCount, total),
              _safeRatio(controller.completedExamCount, total),
              _safeRatio(controller.submittedCount, total),
              _safeRatio(controller.upcomingExamCount, total),
            ],
            labels: const ['进行中', '已结束', '已交卷', '待开始'],
            dark: true,
          ),
        ],
      ),
    );
  }
}

class _ScheduleSheet extends StatelessWidget {
  const _ScheduleSheet({
    required this.items,
    required this.startingExamId,
    required this.onStartExam,
  });

  final List<AssignedExam> items;
  final String? startingExamId;
  final Future<void> Function(AssignedExam exam) onStartExam;

  @override
  Widget build(BuildContext context) {
    return _Surface(
      padding: const EdgeInsets.all(22),
      child: Column(
        children: [
          for (var index = 0; index < items.length; index++) ...[
            _ExamLine(
              title: items[index].title,
              time: _timeLabel(items[index]),
              detail:
                  '${_formatTimeWindow(items[index])}  |  ${_statusLabel(items[index])}',
              tone: _toneForExam(items[index]),
              trailing: items[index].canStartOrResume
                  ? _MiniActionButton(
                      label: startingExamId == items[index].examId
                          ? '...'
                          : '进入',
                      onTap: () => onStartExam(items[index]),
                    )
                  : null,
            ),
            if (index != items.length - 1) const Divider(),
          ],
        ],
      ),
    );
  }
}

class _Backdrop extends StatelessWidget {
  const _Backdrop();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFF8FBFD), Color(0xFFF0F5F8), Color(0xFFEAF1F5)],
        ),
      ),
      child: Stack(
        children: const [
          Positioned(
            top: -100,
            right: -70,
            child: _GlowOrb(size: 280, color: Color(0x2219B7A6)),
          ),
          Positioned(
            top: 220,
            left: -80,
            child: _GlowOrb(size: 220, color: Color(0x22F59E0B)),
          ),
          Positioned(
            bottom: 140,
            right: -90,
            child: _GlowOrb(size: 260, color: Color(0x16102A43)),
          ),
        ],
      ),
    );
  }
}

class _PageHeader extends StatelessWidget {
  const _PageHeader({
    required this.eyebrow,
    required this.title,
    required this.subtitle,
    this.trailing,
  });

  final String eyebrow;
  final String title;
  final String subtitle;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                eyebrow,
                style: theme.textTheme.labelLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.4,
                ),
              ),
              const SizedBox(height: 10),
              Text(
                title,
                style: theme.textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                subtitle,
                style: theme.textTheme.bodyLarge?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        if (trailing != null) ...[const SizedBox(width: 14), trailing!],
      ],
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({
    required this.eyebrow,
    required this.title,
    required this.subtitle,
  });

  final String eyebrow;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          eyebrow,
          style: theme.textTheme.labelLarge?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            fontWeight: FontWeight.w800,
            letterSpacing: 1.4,
          ),
        ),
        const SizedBox(height: 8),
        Text(title, style: theme.textTheme.titleLarge),
        const SizedBox(height: 6),
        Text(
          subtitle,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _Surface extends StatelessWidget {
  const _Surface({
    required this.child,
    this.padding = const EdgeInsets.all(20),
    this.dark = false,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(30),
        gradient: dark
            ? const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFF102A43), Color(0xFF16344F)],
              )
            : LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Colors.white.withValues(alpha: 0.94),
                  const Color(0xFFF8FBFD).withValues(alpha: 0.88),
                ],
              ),
        border: Border.all(
          color: dark
              ? Colors.white.withValues(alpha: 0.08)
              : const Color(0xFFDCE6EE),
        ),
        boxShadow: dark
            ? const [
                BoxShadow(
                  color: Color(0x33102A43),
                  blurRadius: 36,
                  offset: Offset(0, 18),
                ),
              ]
            : const [
                BoxShadow(
                  color: Color(0x14102A43),
                  blurRadius: 28,
                  offset: Offset(0, 14),
                ),
              ],
      ),
      child: Padding(padding: padding, child: child),
    );
  }
}

class _FloatingNavBar extends StatelessWidget {
  const _FloatingNavBar({
    required this.tabs,
    required this.currentIndex,
    required this.onSelect,
  });

  final List<_NavTab> tabs;
  final int currentIndex;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 18),
      child: Container(
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: const Color(0xFF0E1C2C),
          borderRadius: BorderRadius.circular(26),
          border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33102A43),
              blurRadius: 30,
              offset: Offset(0, 14),
            ),
          ],
        ),
        child: Row(
          children: [
            for (var i = 0; i < tabs.length; i++)
              Expanded(
                child: _NavButton(
                  tab: tabs[i],
                  selected: currentIndex == i,
                  onTap: () => onSelect(i),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _NavButton extends StatelessWidget {
  const _NavButton({
    required this.tab,
    required this.selected,
    required this.onTap,
  });

  final _NavTab tab;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
      margin: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        color: selected ? const Color(0xFF1B344D) : Colors.transparent,
        borderRadius: BorderRadius.circular(18),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                selected ? tab.activeIcon : tab.icon,
                color: selected
                    ? const Color(0xFF8DEADF)
                    : const Color(0xFF95A8B9),
              ),
              const SizedBox(height: 6),
              Text(
                tab.label,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: selected ? Colors.white : const Color(0xFF95A8B9),
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Reveal extends StatelessWidget {
  const _Reveal({
    required this.animation,
    required this.begin,
    required this.end,
    required this.child,
  });

  final Animation<double> animation;
  final double begin;
  final double end;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final curved = CurvedAnimation(
      parent: animation,
      curve: Interval(begin, end, curve: Curves.easeOutCubic),
    );

    return AnimatedBuilder(
      animation: curved,
      child: child,
      builder: (context, child) {
        final value = curved.value;
        return Opacity(
          opacity: value,
          child: Transform.translate(
            offset: Offset(0, (1 - value) * 24),
            child: child,
          ),
        );
      },
    );
  }
}

class _CountdownDial extends StatelessWidget {
  const _CountdownDial({
    required this.progress,
    required this.time,
    required this.label,
  });

  final double progress;
  final String time;
  final String label;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return SizedBox(
      width: 132,
      height: 132,
      child: Stack(
        alignment: Alignment.center,
        children: [
          SizedBox(
            width: 132,
            height: 132,
            child: CircularProgressIndicator(
              value: 1,
              strokeWidth: 8,
              color: Colors.white.withValues(alpha: 0.12),
            ),
          ),
          TweenAnimationBuilder<double>(
            tween: Tween(begin: 0, end: progress),
            duration: const Duration(milliseconds: 1100),
            curve: Curves.easeOutCubic,
            builder: (context, value, child) {
              return Transform.rotate(
                angle: -math.pi / 2,
                child: SizedBox(
                  width: 132,
                  height: 132,
                  child: CircularProgressIndicator(
                    value: value,
                    strokeWidth: 8,
                    strokeCap: StrokeCap.round,
                    color: const Color(0xFF8DEADF),
                    backgroundColor: Colors.transparent,
                  ),
                ),
              );
            },
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                time,
                style: theme.textTheme.headlineMedium?.copyWith(
                  color: Colors.white,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                label,
                textAlign: TextAlign.center,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: const Color(0xFFB7C8D8),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _BarStrip extends StatelessWidget {
  const _BarStrip({
    required this.values,
    this.labels = const [],
    this.dark = false,
  });

  final List<double> values;
  final List<String> labels;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final color = dark ? const Color(0xFF8DEADF) : const Color(0xFF19B7A6);
    final baseColor = dark
        ? Colors.white.withValues(alpha: 0.12)
        : const Color(0xFFE4EDF4);
    final labelColor = dark
        ? const Color(0xFFB7C8D8)
        : Theme.of(context).colorScheme.onSurfaceVariant;

    return SizedBox(
      height: labels.isEmpty ? 68 : 92,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (var i = 0; i < values.length; i++) ...[
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Expanded(
                    child: Align(
                      alignment: Alignment.bottomCenter,
                      child: TweenAnimationBuilder<double>(
                        tween: Tween(begin: 0, end: values[i]),
                        duration: Duration(milliseconds: 700 + (i * 120)),
                        curve: Curves.easeOutCubic,
                        builder: (context, value, child) {
                          return Container(
                            width: 24,
                            height: 58 * value.clamp(0, 1),
                            decoration: BoxDecoration(
                              color: color,
                              borderRadius: BorderRadius.circular(999),
                            ),
                          );
                        },
                      ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  Container(
                    width: 24,
                    height: 2,
                    decoration: BoxDecoration(
                      color: baseColor,
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                  if (labels.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      labels[i],
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: labelColor,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ],
              ),
            ),
            if (i != values.length - 1) const SizedBox(width: 10),
          ],
        ],
      ),
    );
  }
}

class _ExamLine extends StatelessWidget {
  const _ExamLine({
    required this.title,
    required this.time,
    required this.detail,
    required this.tone,
    this.compact = false,
    this.trailing,
  });

  final String title;
  final String time;
  final String detail;
  final _LineTone tone;
  final bool compact;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: EdgeInsets.symmetric(vertical: compact ? 12 : 14),
      child: Row(
        children: [
          Container(
            width: compact ? 72 : 82,
            padding: EdgeInsets.symmetric(
              horizontal: 10,
              vertical: compact ? 10 : 12,
            ),
            decoration: BoxDecoration(
              color: tone.background,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Text(
              time,
              textAlign: TextAlign.center,
              style: theme.textTheme.titleMedium?.copyWith(
                color: tone.foreground,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: theme.textTheme.titleMedium),
                const SizedBox(height: 4),
                Text(
                  detail,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          if (trailing != null) ...[
            const SizedBox(width: 12),
            trailing!,
          ] else ...[
            const SizedBox(width: 12),
            Icon(
              Icons.arrow_outward_rounded,
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ],
        ],
      ),
    );
  }
}

class _HeroMeta extends StatelessWidget {
  const _HeroMeta({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: const Color(0xFF8DEADF)),
          const SizedBox(width: 8),
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({
    required this.label,
    required this.active,
    required this.onTap,
  });

  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return InkWell(
      borderRadius: BorderRadius.circular(999),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: active
              ? theme.colorScheme.primaryContainer
              : const Color(0xFFF1F5F8),
          borderRadius: BorderRadius.circular(999),
        ),
        child: Text(
          label,
          style: theme.textTheme.labelLarge?.copyWith(
            color: active
                ? theme.colorScheme.primary
                : theme.colorScheme.onSurfaceVariant,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}

class _CapsuleLabel extends StatelessWidget {
  const _CapsuleLabel({required this.label, this.dark = true});

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
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: dark
              ? Colors.white
              : Theme.of(context).colorScheme.onSurfaceVariant,
          fontWeight: FontWeight.w800,
          letterSpacing: 1.1,
        ),
      ),
    );
  }
}

class _DetailChip extends StatelessWidget {
  const _DetailChip({required this.label, this.dark = false});

  final String label;
  final bool dark;

  @override
  Widget build(BuildContext context) {
    final background = dark
        ? Colors.white.withValues(alpha: 0.08)
        : const Color(0xFFF1F5F8);
    final foreground = dark
        ? Colors.white
        : Theme.of(context).colorScheme.onSurface;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: foreground,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

class _SettingLine extends StatelessWidget {
  const _SettingLine({
    required this.icon,
    required this.title,
    required this.detail,
  });

  final IconData icon;
  final String title;
  final String detail;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: theme.colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: theme.colorScheme.primary),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: theme.textTheme.titleMedium),
                const SizedBox(height: 4),
                Text(
                  detail,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _UserBadge extends StatelessWidget {
  const _UserBadge({
    required this.initials,
    required this.name,
    required this.detail,
  });

  final String initials;
  final String name;
  final String detail;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFDCE6EE)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: const Color(0xFF102A43),
            child: Text(
              initials,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                name,
                style: Theme.of(
                  context,
                ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
              ),
              Text(
                detail,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ],
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

class _ExamCard extends StatelessWidget {
  const _ExamCard({
    required this.exam,
    required this.busy,
    required this.onStartExam,
    required this.onViewResult,
  });

  final AssignedExam exam;
  final bool busy;
  final VoidCallback? onStartExam;
  final VoidCallback? onViewResult;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.7),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFDCE6EE)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(exam.title, style: theme.textTheme.titleLarge),
              ),
              const SizedBox(width: 10),
              _StatusDotLabel(exam: exam),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            _formatTimeWindow(exam),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '防作弊等级 ${exam.antiCheatLevel}  |  ${exam.hasSession ? '已创建会话' : '尚未进入'}',
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          if (exam.sessionStatus.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              '答题状态：${_sessionStatusLabel(exam.sessionStatus)}',
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _DetailChip(label: _examStatusLabel(exam.status)),
                    if (exam.sessionId.isNotEmpty)
                      const _DetailChip(label: '已创建会话'),
                  ],
                ),
              ),
              const SizedBox(width: 14),
              if (onStartExam != null)
                FilledButton(
                  onPressed: busy ? null : onStartExam,
                  child: Text(
                    busy
                        ? '进入中...'
                        : exam.hasSession
                        ? '继续'
                        : '进入',
                  ),
                )
              else if (onViewResult != null)
                OutlinedButton(
                  onPressed: onViewResult,
                  child: const Text('查看成绩'),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatusDotLabel extends StatelessWidget {
  const _StatusDotLabel({required this.exam});

  final AssignedExam exam;

  @override
  Widget build(BuildContext context) {
    final tone = _toneForExam(exam);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: tone.background,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: tone.foreground,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            _statusLabel(exam),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(
              color: tone.foreground,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _InlineNotice extends StatelessWidget {
  const _InlineNotice({required this.message});

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
          const Icon(Icons.info_outline_rounded, color: Color(0xFFB75C00)),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: const Color(0xFF8B4400),
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({
    required this.title,
    required this.subtitle,
    this.compact = false,
  });

  final String title;
  final String subtitle;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return _Surface(
      padding: EdgeInsets.all(compact ? 20 : 22),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.inbox_outlined,
            size: compact ? 40 : 52,
            color: Theme.of(context).colorScheme.primary,
          ),
          SizedBox(height: compact ? 12 : 16),
          Text(
            title,
            style: Theme.of(context).textTheme.titleLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}

class _MiniActionButton extends StatelessWidget {
  const _MiniActionButton({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return TextButton(
      onPressed: onTap,
      style: TextButton.styleFrom(
        foregroundColor: Theme.of(context).colorScheme.primary,
        textStyle: Theme.of(
          context,
        ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800),
      ),
      child: Text(label),
    );
  }
}

class _NavTab {
  const _NavTab({
    required this.label,
    required this.icon,
    required this.activeIcon,
  });

  final String label;
  final IconData icon;
  final IconData activeIcon;
}

enum _ExamFilter {
  all('全部'),
  running('进行中'),
  upcoming('未开始'),
  completed('已结束');

  const _ExamFilter(this.label);

  final String label;

  bool matches(AssignedExam exam) {
    switch (this) {
      case _ExamFilter.all:
        return true;
      case _ExamFilter.running:
        return exam.isRunning;
      case _ExamFilter.upcoming:
        return exam.isNotStarted;
      case _ExamFilter.completed:
        return exam.isFinished || exam.hasSubmittedSession;
    }
  }
}

class _LineTone {
  const _LineTone({required this.background, required this.foreground});

  final Color background;
  final Color foreground;

  static const focus = _LineTone(
    background: Color(0xFFD9F7F3),
    foreground: Color(0xFF0E766B),
  );
  static const calm = _LineTone(
    background: Color(0xFFE4ECF8),
    foreground: Color(0xFF305A86),
  );
  static const warn = _LineTone(
    background: Color(0xFFFFE8CC),
    foreground: Color(0xFF9A5800),
  );
}

class _DialData {
  const _DialData({
    required this.progress,
    required this.timeText,
    required this.label,
  });

  final double progress;
  final String timeText;
  final String label;
}

_LineTone _toneForExam(AssignedExam exam) {
  if (exam.isRunning) {
    return _LineTone.focus;
  }
  if (exam.isNotStarted) {
    return _LineTone.calm;
  }
  return _LineTone.warn;
}

String _statusLabel(AssignedExam exam) {
  if (exam.isSubmitted) {
    return '已交卷';
  }
  if (exam.isRunning && exam.hasSession) {
    return '答题中';
  }
  if (exam.isRunning) {
    return '进行中';
  }
  if (exam.isNotStarted) {
    return '未开始';
  }
  return '已结束';
}

String _heroCapsuleLabel(AssignedExam exam) {
  if (exam.isRunning) {
    return '当前考试';
  }
  if (exam.isNotStarted) {
    return '下一场考试';
  }
  return '最近考试';
}

String _heroDescription(AssignedExam exam) {
  if (exam.isRunning && exam.hasSession) {
    return '考试已开始，且你已经进入过本场考试。可继续作答，并保持答案同步。';
  }
  if (exam.isRunning) {
    return '当前已到开考时间，点击后即可进入考试并开始作答。';
  }
  if (exam.isNotStarted) {
    return '本场考试已安排，但尚未到开考时间，请按时进入。';
  }
  return '本场考试已结束，你仍可以在这里查看状态记录。';
}

String _timeLabel(AssignedExam exam) {
  final time = exam.startTime;
  if (time == null) {
    return '--:--';
  }
  return DateFormat(_timeFormat).format(time);
}

String _formatTimeWindow(AssignedExam exam) {
  final start = exam.startTime;
  final end = exam.endTime;
  if (start == null || end == null) {
    return '考试时间待定';
  }
  return '${DateFormat('$_monthDayFormat $_timeFormat').format(start)} - ${DateFormat(_timeFormat).format(end)}';
}

String _resolveInitials(String value) {
  final tokens = value
      .split(RegExp(r'\s+'))
      .where((item) => item.trim().isNotEmpty)
      .take(2)
      .toList(growable: false);
  if (tokens.isEmpty) {
    return '考';
  }
  return tokens.map((item) => item.substring(0, 1).toUpperCase()).join();
}

double _safeRatio(int value, int total) {
  if (total <= 0) {
    return 0;
  }
  return (value / total).clamp(0, 1);
}

String _formatHeaderDate(DateTime date) {
  const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
  return '${DateFormat(_monthDayFormat).format(date)} ${weekdays[date.weekday - 1]}';
}

String _roleLabel(String raw) {
  switch (raw.toUpperCase()) {
    case 'ADMIN':
      return '管理员';
    case 'TEACHER':
      return '教师';
    case 'STUDENT':
      return '学生';
    default:
      return raw.isEmpty ? '未设置' : raw;
  }
}

String _accountStatusLabel(String? raw) {
  final value = (raw ?? '').trim().toUpperCase();
  switch (value) {
    case '':
      return '正常';
    case '1':
    case 'ACTIVE':
    case 'ENABLED':
      return '正常';
    case '0':
    case 'DISABLED':
    case 'INACTIVE':
      return '已停用';
    default:
      return raw!;
  }
}

String _examStatusLabel(String raw) {
  switch (raw.toUpperCase()) {
    case 'RUNNING':
      return '进行中';
    case 'NOT_STARTED':
      return '未开始';
    case 'FINISHED':
      return '已结束';
    default:
      return raw;
  }
}

String _sessionStatusLabel(String raw) {
  switch (raw.toUpperCase()) {
    case 'IN_PROGRESS':
      return '答题中';
    case 'SUBMITTED':
      return '已交卷';
    case 'FORCE_SUBMITTED':
      return '已强制交卷';
    default:
      return raw;
  }
}

_DialData _buildDialData(AssignedExam? exam) {
  if (exam == null) {
    return const _DialData(progress: 0.18, timeText: '--', label: '等待\n考试安排');
  }

  final now = DateTime.now();
  if (exam.isRunning && exam.endTime != null && exam.startTime != null) {
    final totalSeconds = exam.endTime!
        .difference(exam.startTime!)
        .inSeconds
        .abs();
    final leftSeconds = exam.endTime!
        .difference(now)
        .inSeconds
        .clamp(0, math.max(1, totalSeconds))
        .toInt();
    final elapsed = totalSeconds - leftSeconds;
    final progress = totalSeconds <= 0 ? 0.0 : elapsed / totalSeconds;

    return _DialData(
      progress: progress.clamp(0.05, 1.0),
      timeText: _shortDuration(Duration(seconds: leftSeconds)),
      label: '距结束',
    );
  }

  if (exam.isNotStarted && exam.startTime != null) {
    final seconds = exam.startTime!
        .difference(now)
        .inSeconds
        .clamp(0, 86400)
        .toInt();
    final progress = 1 - (seconds / 86400);

    return _DialData(
      progress: progress.clamp(0.08, 0.92),
      timeText: _shortDuration(Duration(seconds: seconds)),
      label: '距开考',
    );
  }

  return const _DialData(progress: 1, timeText: '完成', label: '考试结束');
}

String _shortDuration(Duration duration) {
  if (duration.inHours > 0) {
    return '${duration.inHours}小时';
  }
  final minutes = duration.inMinutes;
  if (minutes > 0) {
    return '$minutes分';
  }
  return '${duration.inSeconds}秒';
}
