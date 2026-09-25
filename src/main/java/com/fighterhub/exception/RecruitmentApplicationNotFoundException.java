package com.fighterhub.exception;

// 指定applicationId(+teamId)に対応する参加申請が存在しない場合の例外。
// 別Teamに属する参加申請の場合もこの例外を使用し、他Teamの申請の存在自体を公開しない。
public class RecruitmentApplicationNotFoundException extends ResourceNotFoundException {

    public RecruitmentApplicationNotFoundException(Long teamId, Long applicationId) {
        super("RecruitmentApplication not found. teamId=" + teamId + ", applicationId=" + applicationId);
    }
}
