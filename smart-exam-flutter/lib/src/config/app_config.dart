class AppConfig {
  const AppConfig({required this.appName, required this.apiBaseUrl});

  final String appName;
  final String apiBaseUrl;

  static const current = AppConfig(
    appName: '智能考试',
    apiBaseUrl: String.fromEnvironment(
      'API_BASE_URL',
      defaultValue: 'http://10.0.2.2:9000/api/v1',
    ),
  );
}
