import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../app/smart_exam_controller.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key, required this.controller});

  final SmartExamController controller;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _formKey = GlobalKey<FormState>();

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }

    FocusScope.of(context).unfocus();
    await widget.controller.login(
      username: _usernameController.text,
      password: _passwordController.text,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final isLoading = widget.controller.isAuthenticating;

        return Scaffold(
          body: Stack(
            children: [
              const _LoginBackdrop(),
              SafeArea(
                child: Center(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(20),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 460),
                      child: Container(
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.78),
                          borderRadius: BorderRadius.circular(34),
                          border: Border.all(color: const Color(0xFFDCE6EE)),
                          boxShadow: const [
                            BoxShadow(
                              color: Color(0x16102A43),
                              blurRadius: 30,
                              offset: Offset(0, 18),
                            ),
                          ],
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                  vertical: 8,
                                ),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF102A43),
                                  borderRadius: BorderRadius.circular(999),
                                ),
                                child: Text(
                                  '智能考试',
                                  style: theme.textTheme.labelLarge?.copyWith(
                                    color: Colors.white,
                                    fontWeight: FontWeight.w800,
                                    letterSpacing: 1.2,
                                  ),
                                ),
                              ),
                              const SizedBox(height: 24),
                              Text(
                                '安全、专注、\n随时进入考试。',
                                style: GoogleFonts.spaceGrotesk(
                                  fontSize: 34,
                                  fontWeight: FontWeight.w700,
                                  height: 0.98,
                                  color: theme.colorScheme.onSurface,
                                ),
                              ),
                              const SizedBox(height: 12),
                              Text(
                                '使用账号密码登录后，即可查看待参加考试、进入答题并同步交卷状态。',
                                style: theme.textTheme.bodyLarge?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                              const SizedBox(height: 24),
                              const Wrap(
                                spacing: 10,
                                runSpacing: 10,
                                children: [
                                  _MiniPill(
                                    icon: Icons.verified_user_outlined,
                                    label: '身份校验',
                                  ),
                                  _MiniPill(
                                    icon: Icons.notifications_active_outlined,
                                    label: '考试提醒',
                                  ),
                                  _MiniPill(
                                    icon: Icons.sync_outlined,
                                    label: '进度同步',
                                  ),
                                ],
                              ),
                              const SizedBox(height: 28),
                              if (widget.controller.authError != null) ...[
                                _ErrorBanner(
                                  message: widget.controller.authError!,
                                ),
                                const SizedBox(height: 18),
                              ],
                              Form(
                                key: _formKey,
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    TextFormField(
                                      controller: _usernameController,
                                      enabled: !isLoading,
                                      textInputAction: TextInputAction.next,
                                      onChanged: (_) =>
                                          widget.controller.clearAuthError(),
                                      decoration: const InputDecoration(
                                        labelText: '用户名',
                                        hintText: '请输入用户名',
                                      ),
                                      validator: (value) {
                                        if ((value ?? '').trim().isEmpty) {
                                          return '请输入用户名';
                                        }
                                        return null;
                                      },
                                    ),
                                    const SizedBox(height: 16),
                                    TextFormField(
                                      controller: _passwordController,
                                      enabled: !isLoading,
                                      obscureText: true,
                                      onChanged: (_) =>
                                          widget.controller.clearAuthError(),
                                      onFieldSubmitted: (_) => _submit(),
                                      decoration: const InputDecoration(
                                        labelText: '密码',
                                        hintText: '请输入密码',
                                      ),
                                      validator: (value) {
                                        if ((value ?? '').isEmpty) {
                                          return '请输入密码';
                                        }
                                        return null;
                                      },
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 20),
                              FilledButton(
                                onPressed: isLoading ? null : _submit,
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 2,
                                  ),
                                  child: Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      if (isLoading) ...[
                                        const SizedBox(
                                          width: 18,
                                          height: 18,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2.2,
                                            color: Colors.white,
                                          ),
                                        ),
                                        const SizedBox(width: 12),
                                      ],
                                      Text(isLoading ? '登录中...' : '登录'),
                                    ],
                                  ),
                                ),
                              ),
                              const SizedBox(height: 22),
                              Text(
                                '如无法登录，请确认账号密码正确，或联系管理员处理。',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _LoginBackdrop extends StatelessWidget {
  const _LoginBackdrop();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFFF6FBFE), Color(0xFFE9F4F7), Color(0xFFE7EEF4)],
        ),
      ),
      child: Stack(
        children: const [
          Positioned(
            top: -120,
            right: -80,
            child: _GlowOrb(size: 320, color: Color(0x3019B7A6)),
          ),
          Positioned(
            top: 160,
            left: -80,
            child: _GlowOrb(size: 240, color: Color(0x22F59E0B)),
          ),
          Positioned(
            bottom: -40,
            right: 20,
            child: _GlowOrb(size: 260, color: Color(0x22102A43)),
          ),
        ],
      ),
    );
  }
}

class _MiniPill extends StatelessWidget {
  const _MiniPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFF1F5F8),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 250),
            child: Text(
              label,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(
                context,
              ).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFFFF1F1),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: const Color(0xFFFFD4D4)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.error_outline_rounded, color: Color(0xFFCB3A31)),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: const Color(0xFF8F231D),
                fontWeight: FontWeight.w600,
              ),
            ),
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
