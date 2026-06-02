import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'api_exception.dart';

class ApiClient {
  ApiClient({
    required String baseUrl,
    required String? Function() tokenResolver,
  }) : _baseUrl = _normalizeBaseUrl(baseUrl),
       _tokenResolver = tokenResolver;

  final String _baseUrl;
  final String? Function() _tokenResolver;

  Future<dynamic> get(String path) {
    return _send(method: 'GET', path: path);
  }

  Future<dynamic> post(String path, {Object? body}) {
    return _send(method: 'POST', path: path, body: body);
  }

  Future<dynamic> put(String path, {Object? body}) {
    return _send(method: 'PUT', path: path, body: body);
  }

  Future<dynamic> _send({
    required String method,
    required String path,
    Object? body,
  }) async {
    final token = _tokenResolver();
    final headers = <String, String>{
      'Accept': 'application/json',
      if (body != null) 'Content-Type': 'application/json',
      if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
    };

    final uri = Uri.parse('$_baseUrl${path.startsWith('/') ? path : '/$path'}');

    late final http.Response response;
    try {
      switch (method) {
        case 'GET':
          response = await http
              .get(uri, headers: headers)
              .timeout(const Duration(seconds: 15));
        case 'POST':
          response = await http
              .post(
                uri,
                headers: headers,
                body: body == null ? null : jsonEncode(body),
              )
              .timeout(const Duration(seconds: 15));
        case 'PUT':
          response = await http
              .put(
                uri,
                headers: headers,
                body: body == null ? null : jsonEncode(body),
              )
              .timeout(const Duration(seconds: 15));
        default:
          throw const ApiException(message: 'Unsupported request method');
      }
    } on TimeoutException {
      throw const ApiException(message: 'Request timed out');
    } on http.ClientException catch (error) {
      throw ApiException(message: error.message);
    }

    final rawBody = utf8.decode(response.bodyBytes);
    final decoded = rawBody.isEmpty ? null : jsonDecode(rawBody);

    if (decoded is Map<String, dynamic> && decoded.containsKey('code')) {
      final code = decoded['code'] is int
          ? decoded['code'] as int
          : int.tryParse('${decoded['code']}');
      final message = '${decoded['message'] ?? 'Request failed'}';

      if (code != 0) {
        throw ApiException(
          message: message,
          code: code,
          statusCode: response.statusCode,
        );
      }

      return decoded['data'];
    }

    if (response.statusCode >= 400) {
      throw ApiException(
        message: 'HTTP ${response.statusCode}',
        statusCode: response.statusCode,
      );
    }

    return decoded;
  }

  static String _normalizeBaseUrl(String value) {
    final trimmed = value.trim();
    if (trimmed.endsWith('/')) {
      return trimmed.substring(0, trimmed.length - 1);
    }
    return trimmed;
  }
}
