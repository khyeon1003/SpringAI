package com.example.springai.tool;

import java.math.BigDecimal;

import com.example.springai.dto.UserAcademicInfoDto;
import com.example.springai.entity.User;
import com.example.springai.repository.UserRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class UserTool {

    public static final String USER_ID_CONTEXT_KEY = "userId";

    private final UserRepository userRepository;

    public UserTool(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Tool(name = "getUserAcademicInfo", description = "Get the current user's academic information.")
    public UserAcademicInfoDto getUserAcademicInfo(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        User user = findCurrentUser(toolContext, requestedUserId);

        return new UserAcademicInfoDto(
                user.getId(),
                user.getGpa(),
                user.getGrade(),
                user.getGeneralEducationCredits(),
                user.getMajorCredits());
    }

    @Tool(name = "getUserGpa", description = "Get the current user's GPA.")
    public BigDecimal getUserGpa(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findCurrentUser(toolContext, requestedUserId).getGpa();
    }

    @Tool(name = "getUserGrade", description = "Get the current user's grade.")
    public BigDecimal getUserGrade(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findCurrentUser(toolContext, requestedUserId).getGrade();
    }

    @Tool(name = "getUserGeneralEducationCredits", description = "Get the current user's general education credits.")
    public Integer getUserGeneralEducationCredits(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findCurrentUser(toolContext, requestedUserId).getGeneralEducationCredits();
    }

    @Tool(name = "getUserMajorCredits", description = "Get the current user's major credits.")
    public Integer getUserMajorCredits(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findCurrentUser(toolContext, requestedUserId).getMajorCredits();
    }

    private User findCurrentUser(ToolContext toolContext, Long requestedUserId) {
        Long currentUserId = resolveUserId(toolContext);
        if (requestedUserId != null && !requestedUserId.equals(currentUserId)) {
            throw new UserDataAccessDeniedException(currentUserId, requestedUserId);
        }

        return findUserById(currentUserId);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found. userId=" + userId));
    }

    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext == null) {
            throw new IllegalArgumentException("Tool context is required.");
        }

        Object userId = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (userId instanceof Long value) {
            return value;
        }

        throw new IllegalArgumentException("Tool context must contain Long userId.");
    }
}
