package com.smart.exam.user.controller;

import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.web.security.PermissionCodes;
import com.smart.exam.user.model.UserProfile;
import com.smart.exam.user.service.UserProfileService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private final UserProfileService userProfileService = mock(UserProfileService.class);
    private final UserController controller = new UserController(userProfileService);

    @Test
    void teacherListOnlyReturnsVisibleUsers() {
        when(userProfileService.listVisibleForRole("TEACHER"))
                .thenReturn(List.of(profile("31001", "STUDENT")));

        var response = controller.list(
                "21001",
                "TEACHER",
                PermissionCodes.USER_LIST_VIEW
        );

        assertEquals(1, response.getData().size());
        verify(userProfileService).listVisibleForRole("TEACHER");
    }

    @Test
    void teacherCannotViewNonStudentProfile() {
        when(userProfileService.findById("10001")).thenReturn(profile("10001", "ADMIN"));

        BizException exception = assertThrows(BizException.class, () -> controller.detail(
                "10001",
                "21001",
                "TEACHER",
                PermissionCodes.USER_PROFILE_VIEW
        ));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), exception.getCode());
    }

    private UserProfile profile(String id, String role) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUsername("user" + id);
        profile.setRealName("User " + id);
        profile.setRole(role);
        profile.setStatus("ENABLED");
        return profile;
    }
}
