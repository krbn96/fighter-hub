package com.fighterhub.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fighterhub.dto.RecruitmentApplicationCreateRequest;
import com.fighterhub.dto.RecruitmentApplicationResponse;
import com.fighterhub.entity.RecruitmentApplication;
import com.fighterhub.entity.RecruitmentApplicationStatus;
import com.fighterhub.entity.Team;
import com.fighterhub.entity.TeamMember;
import com.fighterhub.entity.User;
import com.fighterhub.exception.ApplicationAlreadyProcessedException;
import com.fighterhub.exception.CannotApplyToOwnTeamException;
import com.fighterhub.exception.DuplicatePendingApplicationException;
import com.fighterhub.exception.DuplicateTournamentMembershipException;
import com.fighterhub.exception.NotTeamOwnerException;
import com.fighterhub.exception.RecruitmentApplicationNotFoundException;
import com.fighterhub.exception.RecruitmentClosedException;
import com.fighterhub.exception.TeamFullException;
import com.fighterhub.exception.TeamNotFoundException;
import com.fighterhub.exception.UserNotFoundException;
import com.fighterhub.repository.RecruitmentApplicationRepository;
import com.fighterhub.repository.TeamMemberRepository;
import com.fighterhub.repository.TeamRepository;
import com.fighterhub.repository.UserRepository;

@Service
public class RecruitmentApplicationService {

    private final RecruitmentApplicationRepository recruitmentApplicationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public RecruitmentApplicationService(
            RecruitmentApplicationRepository recruitmentApplicationRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserRepository userRepository,
            Clock clock) {
        this.recruitmentApplicationRepository = recruitmentApplicationRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // User lock取得後に同一Tournament所属・同一Tournament PENDINGの両方を確認することで、
    // 同じUserから同一Tournament内の別Teamへの申請が同時に来ても、後続TransactionはUser lockを
    // 待ち、先行Transaction commit後のPENDINGを確認して409になる(直列化)。
    @Transactional
    public RecruitmentApplicationResponse createApplication(
            Long userId, Long teamId, RecruitmentApplicationCreateRequest request) {

        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (team.getOwner().getId().equals(userId)) {
            throw new CannotApplyToOwnTeamException(userId, teamId);
        }

        // 正式business rule: 募集締切(recruitmentDeadline)後の新規RecruitmentApplication作成は
        // 禁止する(締切ちょうども不可)。承認/拒否(approve/reject)にはこのチェックを行わない
        // (締切前に受け付けたPENDING Applicationは締切後でも承認/拒否可能)。
        if (!team.getTournament().isRecruitmentOpen(LocalDateTime.now(clock))) {
            throw new RecruitmentClosedException(team.getTournament().getId());
        }

        User user = userRepository.findByIdAndDeleteFlagFalseForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Long tournamentId = team.getTournament().getId();

        if (teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(userId, tournamentId)) {
            throw new DuplicateTournamentMembershipException(userId, tournamentId);
        }

        if (recruitmentApplicationRepository.existsByUser_IdAndTeam_Tournament_IdAndStatus(
                userId, tournamentId, RecruitmentApplicationStatus.PENDING)) {
            throw new DuplicatePendingApplicationException(tournamentId, userId);
        }

        RecruitmentApplication application = RecruitmentApplication.create(team, user, request.message());
        RecruitmentApplication savedApplication = recruitmentApplicationRepository.save(application);

        return toRecruitmentApplicationResponse(savedApplication);
    }

    @Transactional(readOnly = true)
    public List<RecruitmentApplicationResponse> findTeamApplications(Long userId, Long teamId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(userId)) {
            throw new NotTeamOwnerException(userId, teamId);
        }

        return recruitmentApplicationRepository.findByTeamIdOrderByCreatedAtDesc(teamId).stream()
                .map(this::toRecruitmentApplicationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecruitmentApplicationResponse> findMyApplications(Long userId) {
        return recruitmentApplicationRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toRecruitmentApplicationResponse)
                .toList();
    }

    // TeamMember作成とApplicationのAPPROVED変更は同一Transaction内で行い、
    // 片方だけ成功する状態を作らない。
    //
    // lock取得順序: Team → RecruitmentApplication(対象+他PENDINGを1クエリでID昇順一括) → User。
    //
    // 対象Application + 対象と同じUser/同一TournamentのPENDING Applicationを、
    // findTargetAndPendingForUpdateOrderByIdで1クエリにまとめてID昇順でロックする
    // (対象の所有者UserはこのクエリのSubqueryでapplicationIdから直接解決するため、
    //  Service側で事前にApplicationをEntityとして読み込む必要はない)。
    //
    // あえて事前のfindByIdを行わない: 事前に対象ApplicationをEntityとして読み込むと、
    // そのEntityがこのTransactionのpersistence contextに管理対象として乗ってしまい、
    // 後続のPESSIMISTIC_WRITEクエリが(他Transactionのcommit待ちで一度ブロックしたのち)
    // 実際には最新のstatusの行を返しても、Hibernateが「既に管理中の同一Entityインスタンス」を
    // そのまま返し、読み込み時点の古いstatus値を上書きしない、という罠がある
    // (この罠を実際に統合テストで検出し、事前読み込みをなくす設計へ変更した)。
    //
    // 「対象を先にロック→他PENDINGを別クエリで後からロック」という2段階だと、異なるTeam Ownerが
    // 同一Userの異なるPENDING Applicationをほぼ同時にapproveした場合、T1が対象Aを保持したまま
    // 他方Bを待ち、T2が対象Bを保持したまま他方Aを待つ、という循環待ち(deadlock)が起こり得る。
    // 対象+他PENDINGを1クエリでID昇順ロックに統一することで、どちらのTransactionも必ず
    // 同じ順序でロックを試みるようになり、この循環待ちを構造的に起こり得なくしている。
    @Transactional
    public RecruitmentApplicationResponse approveApplication(Long currentUserId, Long teamId, Long applicationId) {
        Team team = teamRepository.findActiveTeamByIdForUpdate(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(currentUserId)) {
            throw new NotTeamOwnerException(currentUserId, teamId);
        }

        Long tournamentId = team.getTournament().getId();

        List<RecruitmentApplication> lockedApplications =
                recruitmentApplicationRepository.findTargetAndPendingForUpdateOrderById(
                        applicationId, tournamentId, RecruitmentApplicationStatus.PENDING);

        RecruitmentApplication application = lockedApplications.stream()
                .filter(candidate -> candidate.getId().equals(applicationId))
                .findFirst()
                .orElseThrow(() -> new RecruitmentApplicationNotFoundException(teamId, applicationId));

        // 別TeamのApplicationであることを外部へ公開しないため、404として扱う。
        if (!application.getTeam().getId().equals(teamId)) {
            throw new RecruitmentApplicationNotFoundException(teamId, applicationId);
        }

        if (application.getStatus() != RecruitmentApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException(applicationId);
        }

        Long applicantUserId = application.getUser().getId();

        // 正式business rule: 同一TournamentでUserがTeamMemberになった時点で、
        // そのUserが同一Tournament内の他TeamへのPENDING申請はREJECTEDへ遷移する。
        // 承認対象の申請自身(applicationId)は除外し、APPROVEDへは後続処理で遷移させる。
        rejectPendingApplications(lockedApplications, applicationId);

        User lockedUser = userRepository.findByIdAndDeleteFlagFalseForUpdate(applicantUserId)
                .orElseThrow(() -> new UserNotFoundException(applicantUserId));

        // 申請から承認までの間に別Teamへ加入している可能性があるため、承認時にも再確認する。
        if (teamMemberRepository.existsByUser_IdAndTeam_Tournament_Id(lockedUser.getId(), tournamentId)) {
            throw new DuplicateTournamentMembershipException(lockedUser.getId(), tournamentId);
        }

        long currentMemberCount = teamMemberRepository.countByTeam_Id(teamId);
        if (currentMemberCount >= team.getTournament().getTeamSize()) {
            throw new TeamFullException(teamId);
        }

        teamMemberRepository.save(TeamMember.create(team, lockedUser));

        application.approve();

        return toRecruitmentApplicationResponse(application);
    }

    // rejectはTeam定員・TeamMember・User所属を変更しないため、Team/UserのWRITE LOCKは取得しない。
    // Applicationの行ロックのみでapprove vs reject / reject vs rejectを直列化する。
    @Transactional
    public RecruitmentApplicationResponse rejectApplication(Long currentUserId, Long teamId, Long applicationId) {
        Team team = teamRepository.findActiveTeamById(teamId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));

        if (!team.getOwner().getId().equals(currentUserId)) {
            throw new NotTeamOwnerException(currentUserId, teamId);
        }

        RecruitmentApplication application = recruitmentApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new RecruitmentApplicationNotFoundException(teamId, applicationId));

        if (!application.getTeam().getId().equals(teamId)) {
            throw new RecruitmentApplicationNotFoundException(teamId, applicationId);
        }

        if (application.getStatus() != RecruitmentApplicationStatus.PENDING) {
            throw new ApplicationAlreadyProcessedException(applicationId);
        }

        application.reject();

        return toRecruitmentApplicationResponse(application);
    }

    // 正式business rule: 同一TournamentでUserがTeamMemberになった時点で、
    // そのUserが同一Tournament内の他TeamへのPENDING申請をREJECTEDへ遷移する。
    // TeamService#createTeam専用のentry point(ownerはTeam作成によって即座にTeamMemberになり、
    // 除外すべき「承認対象の申請」自体が存在しないため、除外引数を取らない)。
    // package-privateにしているのはTeamServiceから呼び出すためで、Controllerから直接
    // 呼ばれることは想定しない。
    //
    // あえて@Transactionalを付けない: 呼び出し元(createTeam)が既に開始している
    // @Transactionalの中でそのまま実行され、TeamMember作成と同一トランザクションで
    // commit/rollbackされるようにするため。
    void rejectOtherPendingApplicationsInTournament(Long userId, Long tournamentId) {
        List<RecruitmentApplication> pendingApplications =
                recruitmentApplicationRepository.findByUser_IdAndTeam_Tournament_IdAndStatusForUpdate(
                        userId, tournamentId, RecruitmentApplicationStatus.PENDING);

        rejectPendingApplications(pendingApplications, null);
    }

    // pendingApplicationsはすでにPESSIMISTIC_WRITEでロック済みであることが前提。
    // excludeApplicationIdは、approveApplicationが承認対象の申請自身をここでREJECTEDに
    // してしまわないよう除外するために使う(createTeam経由の呼び出しではnullを渡し、
    // 一覧内の全件をREJECTEDにする)。
    // 比較はDB採番済みの非nullなgetId()を起点に行うため、excludeApplicationId自体がnullでも
    // NPEにはならない(Long#equals(null)はfalseを返すため、createTeamの呼び出しでは
    // どの要素も除外されず全件REJECTEDになる)。
    private void rejectPendingApplications(
            List<RecruitmentApplication> pendingApplications, Long excludeApplicationId) {
        for (RecruitmentApplication pendingApplication : pendingApplications) {
            if (pendingApplication.getId().equals(excludeApplicationId)) {
                continue;
            }
            pendingApplication.reject();
        }
    }

    private RecruitmentApplicationResponse toRecruitmentApplicationResponse(RecruitmentApplication application) {
        return new RecruitmentApplicationResponse(
                application.getId(),
                application.getTeam().getId(),
                application.getUser().getId(),
                application.getUser().getName(),
                application.getMessage(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
