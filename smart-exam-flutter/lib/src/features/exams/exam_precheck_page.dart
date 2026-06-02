import 'dart:async';

import 'package:battery_plus/battery_plus.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'models/assigned_exam.dart';

class ExamPrecheckPage extends StatefulWidget {
  const ExamPrecheckPage({super.key, required this.exam});

  final AssignedExam exam;

  @override
  State<ExamPrecheckPage> createState() => _ExamPrecheckPageState();
}

class _ExamPrecheckPageState extends State<ExamPrecheckPage> {
  final Connectivity _connectivity = Connectivity();
  final Battery _battery = Battery();
  StreamSubscription<List<ConnectivityResult>>? _connectivitySubscription;
  StreamSubscription<BatteryState>? _batterySubscription;

  bool _checkingNetwork = true;
  bool _checkingBattery = true;
  bool _networkOnline = false;
  bool _confirmedRules = false;
  List<ConnectivityResult> _connectivityResults = const [];
  int? _batteryLevel;
  BatteryState? _batteryState;
  bool? _batterySaveMode;

  @override
  void initState() {
    super.initState();
    _connectivitySubscription = _connectivity.onConnectivityChanged.listen(
      _applyConnectivity,
    );
    _batterySubscription = _battery.onBatteryStateChanged.listen(
      _applyBatteryState,
    );
    _checkConnectivity();
    _checkBattery();
  }

  @override
  void dispose() {
    _connectivitySubscription?.cancel();
    _batterySubscription?.cancel();
    super.dispose();
  }

  bool get _canEnter =>
      widget.exam.canStartOrResume && _networkOnline && _confirmedRules;

  Future<void> _checkConnectivity() async {
    setState(() {
      _checkingNetwork = true;
    });

    try {
      final results = await _connectivity.checkConnectivity();
      if (!mounted) {
        return;
      }
      _applyConnectivity(results);
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _checkingNetwork = false;
        _networkOnline = false;
        _connectivityResults = const [];
      });
    }
  }

  Future<void> _checkBattery() async {
    setState(() {
      _checkingBattery = true;
    });

    try {
      final results = await Future.wait<Object>([
        _battery.batteryLevel,
        _battery.batteryState,
        _battery.isInBatterySaveMode,
      ]);
      if (!mounted) {
        return;
      }
      setState(() {
        _checkingBattery = false;
        _batteryLevel = results[0] as int;
        _batteryState = results[1] as BatteryState;
        _batterySaveMode = results[2] as bool;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _checkingBattery = false;
        _batteryLevel = null;
        _batteryState = null;
        _batterySaveMode = null;
      });
    }
  }

  void _applyConnectivity(List<ConnectivityResult> results) {
    final online =
        results.isNotEmpty &&
        results.any((result) => result != ConnectivityResult.none);

    setState(() {
      _checkingNetwork = false;
      _networkOnline = online;
      _connectivityResults = results;
    });
  }

  void _applyBatteryState(BatteryState state) {
    if (!mounted) {
      return;
    }
    setState(() {
      _batteryState = state;
    });
    unawaited(_checkBattery());
  }

  void _enterExam() {
    if (!_canEnter) {
      return;
    }
    Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: Colors.transparent,
      body: DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFF8FBFD), Color(0xFFEFF6F8), Color(0xFFE7EEF4)],
          ),
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
            children: [
              Row(
                children: [
                  IconButton.filledTonal(
                    onPressed: () {
                      Navigator.of(context).maybePop();
                    },
                    icon: const Icon(Icons.arrow_back_rounded),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '考前检查',
                          style: theme.textTheme.labelLarge?.copyWith(
                            color: theme.colorScheme.primary,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          widget.exam.hasSession ? '继续作答准备' : '进入考试准备',
                          style: theme.textTheme.titleLarge,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              _HeroPanel(exam: widget.exam),
              const SizedBox(height: 18),
              _CheckPanel(
                title: '网络连接',
                detail: _checkingNetwork
                    ? '正在检测当前网络状态'
                    : _networkOnline
                    ? '当前网络可用：${_connectivityLabel(_connectivityResults)}'
                    : '未检测到可用网络，请连接网络后重试。',
                icon: _networkOnline
                    ? Icons.wifi_rounded
                    : Icons.wifi_off_rounded,
                passed: _networkOnline,
                loading: _checkingNetwork,
                actionLabel: _networkOnline ? null : '重新检测',
                onAction: _checkingNetwork ? null : _checkConnectivity,
              ),
              const SizedBox(height: 12),
              _CheckPanel(
                title: '考试时间',
                detail: widget.exam.canStartOrResume
                    ? '当前考试正在进行，可以进入答题。'
                    : _examBlockedMessage(widget.exam),
                icon: Icons.schedule_rounded,
                passed: widget.exam.canStartOrResume,
              ),
              const SizedBox(height: 12),
              _CheckPanel(
                title: '电量状态',
                detail: _batteryDetail(),
                icon: _batteryIcon(),
                passed: !_hasLowBatteryRisk,
                loading: _checkingBattery,
                actionLabel: _checkingBattery ? null : '重新检测',
                onAction: _checkingBattery ? null : _checkBattery,
              ),
              const SizedBox(height: 12),
              _CheckPanel(
                title: '屏幕安全',
                detail: _screenSecurityDetail(),
                icon: Icons.privacy_tip_outlined,
                passed: true,
              ),
              const SizedBox(height: 12),
              _CheckPanel(
                title: '防作弊提醒',
                detail: '考试期间请勿切换应用、断开网络或离开答题页。系统会记录切屏、失焦和网络断开事件。',
                icon: Icons.verified_user_outlined,
                passed: true,
              ),
              const SizedBox(height: 18),
              _RulesPanel(
                confirmed: _confirmedRules,
                onChanged: (value) {
                  setState(() {
                    _confirmedRules = value;
                  });
                },
              ),
              const SizedBox(height: 18),
              FilledButton.icon(
                onPressed: _canEnter ? _enterExam : null,
                icon: const Icon(Icons.login_rounded),
                label: Text(widget.exam.hasSession ? '继续作答' : '开始考试'),
              ),
              const SizedBox(height: 10),
              Text(
                _canEnter ? '检查通过后将进入答题页。' : '完成所有检查并确认规则后才能进入。',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  bool get _hasLowBatteryRisk {
    final level = _batteryLevel;
    if (_checkingBattery || level == null) {
      return false;
    }
    return level <= 20 &&
        _batteryState != BatteryState.charging &&
        _batteryState != BatteryState.full;
  }

  String _batteryDetail() {
    if (_checkingBattery) {
      return '正在检测电量状态';
    }

    final level = _batteryLevel;
    if (level == null) {
      return '无法读取电量状态。可继续进入，但建议确认设备电量充足。';
    }

    final state = _batteryStateLabel(_batteryState);
    final saveMode = _batterySaveMode == true ? '，省电模式已开启' : '';
    if (_hasLowBatteryRisk) {
      return '当前电量 $level%，$state$saveMode。建议连接电源后再进入考试。';
    }
    return '当前电量 $level%，$state$saveMode。';
  }

  IconData _batteryIcon() {
    if (_checkingBattery) {
      return Icons.battery_unknown_rounded;
    }
    if (_hasLowBatteryRisk) {
      return Icons.battery_alert_rounded;
    }
    return Icons.battery_charging_full_rounded;
  }

  String _screenSecurityDetail() {
    if (kIsWeb) {
      return 'Web 端无法启用系统级防截屏，本端会依赖前台状态和网络检测记录风险。';
    }

    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return 'Android 已启用防截屏保护，系统会阻止截图和录屏预览。';
      case TargetPlatform.iOS:
        return 'iOS 将记录切后台和失焦行为。进入考试前请关闭录屏、投屏和分屏。';
      case TargetPlatform.macOS:
      case TargetPlatform.windows:
      case TargetPlatform.linux:
      case TargetPlatform.fuchsia:
        return '当前平台提供前台状态、网络和电量检测，暂不提供系统级防截屏。';
    }
  }
}

class _HeroPanel extends StatelessWidget {
  const _HeroPanel({required this.exam});

  final AssignedExam exam;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        color: const Color(0xFF102A43),
        borderRadius: BorderRadius.circular(26),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              _Pill(label: exam.hasSession ? '已创建会话' : '待进入', dark: true),
              const Spacer(),
              Icon(
                Icons.assignment_turned_in_outlined,
                color: Colors.white.withValues(alpha: 0.72),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Text(
            exam.title,
            style: theme.textTheme.headlineSmall?.copyWith(
              color: Colors.white,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            _formatWindow(exam),
            style: theme.textTheme.bodyMedium?.copyWith(
              color: const Color(0xFFC8D6E5),
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _Pill(label: '防作弊等级 ${exam.antiCheatLevel}', dark: true),
              _Pill(label: _sessionLabel(exam), dark: true),
            ],
          ),
        ],
      ),
    );
  }
}

class _CheckPanel extends StatelessWidget {
  const _CheckPanel({
    required this.title,
    required this.detail,
    required this.icon,
    required this.passed,
    this.loading = false,
    this.actionLabel,
    this.onAction,
  });

  final String title;
  final String detail;
  final IconData icon;
  final bool passed;
  final bool loading;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final foreground = passed
        ? const Color(0xFF0E766B)
        : const Color(0xFF9A5800);
    final background = passed
        ? const Color(0xFFD9F7F3)
        : const Color(0xFFFFF4E8);

    return _SurfacePanel(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: background,
              borderRadius: BorderRadius.circular(16),
            ),
            child: loading
                ? Padding(
                    padding: const EdgeInsets.all(12),
                    child: CircularProgressIndicator(
                      strokeWidth: 2.4,
                      color: foreground,
                    ),
                  )
                : Icon(icon, color: foreground),
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
                if (actionLabel != null && onAction != null) ...[
                  const SizedBox(height: 12),
                  OutlinedButton(
                    onPressed: onAction,
                    child: Text(actionLabel!),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RulesPanel extends StatelessWidget {
  const _RulesPanel({required this.confirmed, required this.onChanged});

  final bool confirmed;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return _SurfacePanel(
      child: InkWell(
        borderRadius: BorderRadius.circular(22),
        onTap: () {
          onChanged(!confirmed);
        },
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Checkbox(
              value: confirmed,
              onChanged: (value) {
                onChanged(value == true);
              },
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(top: 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('我已确认考试规则', style: theme.textTheme.titleMedium),
                    const SizedBox(height: 4),
                    Text(
                      '进入后请保持当前页面和网络连接稳定。提交前请确认答案已保存，交卷后不能继续修改。',
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SurfacePanel extends StatelessWidget {
  const _SurfacePanel({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.84),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: const Color(0xFFDCE6EE)),
        boxShadow: const [
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

String _formatWindow(AssignedExam exam) {
  final start = exam.startTime;
  final end = exam.endTime;
  if (start == null || end == null) {
    return '考试时间待定';
  }
  return '${DateFormat('M月d日 HH:mm').format(start)} - ${DateFormat('HH:mm').format(end)}';
}

String _connectivityLabel(List<ConnectivityResult> results) {
  final names = results
      .where((result) => result != ConnectivityResult.none)
      .map(_connectivityName)
      .toSet()
      .toList(growable: false);
  if (names.isEmpty) {
    return '未知';
  }
  return names.join('、');
}

String _connectivityName(ConnectivityResult result) {
  switch (result) {
    case ConnectivityResult.wifi:
      return 'Wi-Fi';
    case ConnectivityResult.mobile:
      return '移动网络';
    case ConnectivityResult.ethernet:
      return '有线网络';
    case ConnectivityResult.vpn:
      return 'VPN';
    case ConnectivityResult.bluetooth:
      return '蓝牙网络';
    case ConnectivityResult.satellite:
      return '卫星网络';
    case ConnectivityResult.other:
      return '其他网络';
    case ConnectivityResult.none:
      return '无网络';
  }
}

String _batteryStateLabel(BatteryState? state) {
  switch (state) {
    case BatteryState.charging:
      return '正在充电';
    case BatteryState.discharging:
      return '未连接电源';
    case BatteryState.full:
      return '电量已满';
    case BatteryState.connectedNotCharging:
      return '已连接电源';
    case BatteryState.unknown:
    case null:
      return '状态未知';
  }
}

String _examBlockedMessage(AssignedExam exam) {
  if (exam.isSubmitted) {
    return '本场考试已交卷，不能再次进入答题。';
  }
  if (exam.isNotStarted) {
    return '本场考试尚未开始，请在开考后进入。';
  }
  if (exam.isFinished) {
    return '本场考试已结束，不能再进入答题。';
  }
  return '当前考试状态暂不允许进入。';
}

String _sessionLabel(AssignedExam exam) {
  if (exam.isSubmitted) {
    return '已交卷';
  }
  if (exam.hasSession) {
    return '继续作答';
  }
  return '首次进入';
}
