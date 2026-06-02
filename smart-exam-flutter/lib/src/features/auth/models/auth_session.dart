class AuthSession {
  const AuthSession({
    required this.token,
    required this.expiresIn,
    required this.user,
  });

  final String token;
  final int expiresIn;
  final SessionUser user;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      token: '${json['token'] ?? ''}',
      expiresIn: _parseInt(json['expiresIn']),
      user: SessionUser.fromJson(json['user'] as Map<String, dynamic>),
    );
  }

  Map<String, dynamic> toJson() {
    return {'token': token, 'expiresIn': expiresIn, 'user': user.toJson()};
  }
}

class SessionUser {
  const SessionUser({
    required this.id,
    required this.username,
    required this.role,
    required this.permissions,
  });

  final String id;
  final String username;
  final String role;
  final List<String> permissions;

  factory SessionUser.fromJson(Map<String, dynamic> json) {
    return SessionUser(
      id: '${json['id'] ?? ''}',
      username: '${json['username'] ?? ''}',
      role: '${json['role'] ?? ''}',
      permissions: (json['permissions'] as List<dynamic>? ?? const [])
          .map((item) => '$item')
          .toList(growable: false),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'role': role,
      'permissions': permissions,
    };
  }
}

class UserEnvelope {
  const UserEnvelope({
    required this.id,
    required this.role,
    required this.profile,
  });

  final String id;
  final String role;
  final UserProfile profile;

  factory UserEnvelope.fromJson(Map<String, dynamic> json) {
    return UserEnvelope(
      id: '${json['id'] ?? ''}',
      role: '${json['role'] ?? ''}',
      profile: UserProfile.fromJson(json['profile'] as Map<String, dynamic>),
    );
  }
}

class UserProfile {
  const UserProfile({
    required this.id,
    required this.username,
    required this.realName,
    required this.role,
    required this.status,
  });

  final String id;
  final String username;
  final String realName;
  final String role;
  final String status;

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      id: '${json['id'] ?? ''}',
      username: '${json['username'] ?? ''}',
      realName: '${json['realName'] ?? ''}',
      role: '${json['role'] ?? ''}',
      status: '${json['status'] ?? ''}',
    );
  }
}

int _parseInt(Object? value) {
  if (value is int) {
    return value;
  }
  return int.tryParse('$value') ?? 0;
}
