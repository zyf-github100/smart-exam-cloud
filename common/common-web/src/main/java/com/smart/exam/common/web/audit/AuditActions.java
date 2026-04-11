package com.smart.exam.common.web.audit;

public final class AuditActions {

    public static final String USER_STATUS_UPDATED = "USER_STATUS_UPDATED";
    public static final String USER_ROLE_UPDATED = "USER_ROLE_UPDATED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";
    public static final String SYSTEM_CONFIG_UPSERTED = "SYSTEM_CONFIG_UPSERTED";

    public static final String QUESTION_CREATED = "QUESTION_CREATED";
    public static final String PAPER_CREATED = "PAPER_CREATED";

    public static final String EXAM_CREATED = "EXAM_CREATED";
    public static final String EXAM_SESSION_STARTED = "EXAM_SESSION_STARTED";
    public static final String EXAM_SESSION_SUBMITTED = "EXAM_SESSION_SUBMITTED";
    public static final String ANTI_CHEAT_EVENT_REPORTED = "ANTI_CHEAT_EVENT_REPORTED";

    public static final String GRADING_MANUAL_SCORED = "GRADING_MANUAL_SCORED";
    public static final String EXAM_RESULT_RELEASE_UPDATED = "EXAM_RESULT_RELEASE_UPDATED";

    private AuditActions() {
    }
}
