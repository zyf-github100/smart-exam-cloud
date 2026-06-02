class ApiException implements Exception {
  const ApiException({required this.message, this.code, this.statusCode});

  final String message;
  final int? code;
  final int? statusCode;

  bool get isUnauthorized => code == 40100 || statusCode == 401;

  @override
  String toString() => message;
}
