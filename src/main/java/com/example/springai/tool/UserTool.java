package com.example.springai.tool;

import java.math.BigDecimal;
import java.util.Map;

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
    public UserAcademicInfo getUserAcademicInfo(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        User user = findUser(toolContext, requestedUserId);

        return new UserAcademicInfo(
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
        return findUser(toolContext, requestedUserId).getGpa();
    }

    @Tool(name = "getUserGrade", description = "Get the current user's grade.")
    public BigDecimal getUserGrade(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findUser(toolContext, requestedUserId).getGrade();
    }

    @Tool(name = "getUserGeneralEducationCredits", description = "Get the current user's general education credits.")
    public Integer getUserGeneralEducationCredits(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findUser(toolContext, requestedUserId).getGeneralEducationCredits();
    }

    @Tool(name = "getUserMajorCredits", description = "Get the current user's major credits.")
    public Integer getUserMajorCredits(
            @ToolParam(required = false, description = "Requested user id. Omit this when asking for the current user.") Long requestedUserId,
            ToolContext toolContext) {
        return findUser(toolContext, requestedUserId).getMajorCredits();
    }

    private User findUser(ToolContext toolContext, Long requestedUserId) {
        Long currentUserId = resolveUserId(toolContext);
        validateUserAccess(currentUserId, requestedUserId);

        return findUser(currentUserId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found. userId=" + userId));
    }

    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext == null) {
            throw new IllegalArgumentException("Tool context is required.");
        }

        Map<String, Object> context = toolContext.getContext();
        Object userId = context.get(USER_ID_CONTEXT_KEY);

        if (userId instanceof Long value) {
            return value;
        }

        if (userId instanceof Number value) {
            return value.longValue();
        }

        if (userId instanceof String value && !value.isBlank()) {
            return Long.parseLong(value);
        }

        throw new IllegalArgumentException("Tool context must contain userId.");
    }

    private void validateUserAccess(Long currentUserId, Long requestedUserId) {
        if (requestedUserId != null && !requestedUserId.equals(currentUserId)) {
            throw new UserDataAccessDeniedException(currentUserId, requestedUserId);
        }
    }

    public record UserAcademicInfo(
            Long id,
            BigDecimal gpa,
            BigDecimal grade,
            Integer generalEducationCredits,
            Integer majorCredits) {
    }
}
