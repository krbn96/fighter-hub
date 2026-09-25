package com.fighterhub.exception;

// PENDING以外の(すでに承認/拒否済みの)参加申請を再度approve/rejectしようとした場合の例外
public class ApplicationAlreadyProcessedException extends ResourceConflictException {

    public ApplicationAlreadyProcessedException(Long applicationId) {
        super("RecruitmentApplication is already processed. applicationId=" + applicationId);
    }
}
