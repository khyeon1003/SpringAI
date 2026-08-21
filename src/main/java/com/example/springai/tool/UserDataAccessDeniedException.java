package com.example.springai.tool;

public class UserDataAccessDeniedException extends RuntimeException {

    public UserDataAccessDeniedException(Long currentUserId, Long requestedUserId) {
        super("User data access denied. currentUserId=" + currentUserId + ", requestedUserId=" + requestedUserId);
    }
}
