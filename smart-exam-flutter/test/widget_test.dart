import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:smart_exam_flutter/src/app.dart';

void main() {
  testWidgets('renders login screen when no local session exists', (
    WidgetTester tester,
  ) async {
    SharedPreferences.setMockInitialValues({});

    await tester.pumpWidget(const SmartExamApp());
    await tester.pumpAndSettle();

    expect(find.text('智能考试'), findsOneWidget);
    expect(find.text('登录'), findsOneWidget);
    expect(find.text('用户名'), findsOneWidget);
  });
}
