package com.smart.exam.common.web.security;

public final class PermissionCodes {

    private PermissionCodes() {
    }

    public static final String EXAM_CREATE = "EXAM_CREATE";
    public static final String EXAM_SESSION_START = "EXAM_SESSION_START";
    public static final String EXAM_ANSWER_SAVE = "EXAM_ANSWER_SAVE";
    public static final String EXAM_SESSION_SUBMIT = "EXAM_SESSION_SUBMIT";
    public static final String EXAM_ANTI_CHEAT_EVENT_REPORT = "EXAM_ANTI_CHEAT_EVENT_REPORT";
    public static final String EXAM_ANTI_CHEAT_RISK_VIEW = "EXAM_ANTI_CHEAT_RISK_VIEW";
    public static final String STUDENT_RESULT_VIEW = "STUDENT_RESULT_VIEW";

    public static final String GRADING_TASK_VIEW = "GRADING_TASK_VIEW";
    public static final String GRADING_MANUAL_SCORE = "GRADING_MANUAL_SCORE";

    public static final String QUESTION_CREATE = "QUESTION_CREATE";
    public static final String QUESTION_LIST = "QUESTION_LIST";
    public static final String QUESTION_DETAIL = "QUESTION_DETAIL";
    public static final String PAPER_CREATE = "PAPER_CREATE";
    public static final String PAPER_DETAIL = "PAPER_DETAIL";

    public static final String REPORT_SCORE_DISTRIBUTION_VIEW = "REPORT_SCORE_DISTRIBUTION_VIEW";
    public static final String REPORT_QUESTION_ACCURACY_VIEW = "REPORT_QUESTION_ACCURACY_VIEW";

    public static final String USER_SELF_VIEW = "USER_SELF_VIEW";
    public static final String USER_PROFILE_VIEW = "USER_PROFILE_VIEW";
    public static final String USER_LIST_VIEW = "USER_LIST_VIEW";
}
