package com.smart.exam.common.web.audit;

public record AuditLogCommand(String moduleKey,
                              String operatorId,
                              String operatorRole,
                              String action,
                              String targetType,
                              String targetId,
                              Object detail,
                              String ip,
                              String userAgent) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String moduleKey;
        private String operatorId;
        private String operatorRole;
        private String action;
        private String targetType;
        private String targetId;
        private Object detail;
        private String ip;
        private String userAgent;

        private Builder() {
        }

        public Builder moduleKey(String moduleKey) {
            this.moduleKey = moduleKey;
            return this;
        }

        public Builder operatorId(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        public Builder operatorRole(String operatorRole) {
            this.operatorRole = operatorRole;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder detail(Object detail) {
            this.detail = detail;
            return this;
        }

        public Builder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public AuditLogCommand build() {
            return new AuditLogCommand(
                    moduleKey,
                    operatorId,
                    operatorRole,
                    action,
                    targetType,
                    targetId,
                    detail,
                    ip,
                    userAgent
            );
        }
    }
}
