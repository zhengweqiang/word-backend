package com.example.words.service;

import com.example.words.dto.ClassroomGroupFeedMessageResponse;
import com.example.words.dto.ClassroomConversationResponse;
import com.example.words.dto.CreateClassroomGroupFeedTextMessageRequest;
import com.example.words.dto.ShareClassroomGroupFeedDictionaryRequest;
import com.example.words.dto.ShareClassroomGroupFeedStudyPlanRequest;
import com.example.words.dto.ShareClassroomGroupFeedVideoRequest;
import com.example.words.dto.VideoAccessResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomGroupFeedMessage;
import com.example.words.model.ClassroomGroupFeedMessageType;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.Dictionary;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.StudyPlan;
import com.example.words.model.StudyPlanStatus;
import com.example.words.model.UserRole;
import com.example.words.model.VideoAccessMode;
import com.example.words.model.VideoAsset;
import com.example.words.model.VideoCloudPublishStatus;
import com.example.words.model.VideoStatus;
import com.example.words.repository.AppUserRepository;
import com.example.words.repository.ClassroomDictionaryAssignmentRepository;
import com.example.words.repository.ClassroomGroupFeedMessageRepository;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryRepository;
import com.example.words.repository.StudyPlanClassroomRepository;
import com.example.words.repository.StudyPlanRepository;
import com.example.words.repository.VideoAssetRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomGroupFeedService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClassroomGroupFeedMessageRepository classroomGroupFeedMessageRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;
    private final DictionaryRepository dictionaryRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanClassroomRepository studyPlanClassroomRepository;
    private final AccessControlService accessControlService;
    private final AppUserRepository appUserRepository;
    private final VideoAssetService videoAssetService;

    public ClassroomGroupFeedService(
            ClassroomGroupFeedMessageRepository classroomGroupFeedMessageRepository,
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository,
            DictionaryRepository dictionaryRepository,
            VideoAssetRepository videoAssetRepository,
            StudyPlanRepository studyPlanRepository,
            StudyPlanClassroomRepository studyPlanClassroomRepository,
            AccessControlService accessControlService,
            AppUserRepository appUserRepository,
            VideoAssetService videoAssetService) {
        this.classroomGroupFeedMessageRepository = classroomGroupFeedMessageRepository;
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.classroomDictionaryAssignmentRepository = classroomDictionaryAssignmentRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.videoAssetRepository = videoAssetRepository;
        this.studyPlanRepository = studyPlanRepository;
        this.studyPlanClassroomRepository = studyPlanClassroomRepository;
        this.accessControlService = accessControlService;
        this.appUserRepository = appUserRepository;
        this.videoAssetService = videoAssetService;
    }

    @Transactional(readOnly = true)
    public Page<ClassroomConversationResponse> listConversations(int page, int size, AppUser actor) {
        Pageable pageable = buildPageable(page, size);
        List<Classroom> classrooms = actor.getRole() == UserRole.ADMIN
                ? classroomRepository.findAll()
                : classroomRepository.findByTeacherId(actor.getId());
        List<ClassroomConversationResponse> conversations = classrooms.stream()
                .filter(classroom -> classroom.getStatus() == ClassroomStatus.ACTIVE)
                .map(this::toConversationResponse)
                .sorted(Comparator
                        .comparing(
                                ClassroomConversationResponse::getLastMessageAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(ClassroomConversationResponse::getClassroomName))
                .toList();

        int start = Math.min((int) pageable.getOffset(), conversations.size());
        int end = Math.min(start + pageable.getPageSize(), conversations.size());
        return new PageImpl<>(conversations.subList(start, end), pageable, conversations.size());
    }

    @Transactional(readOnly = true)
    public Page<ClassroomGroupFeedMessageResponse> listMessages(
            Long classroomId,
            int page,
            int size,
            ClassroomGroupFeedMessageType messageType,
            AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanAccessFeed(classroom, actor);

        Pageable pageable = buildPageable(page, size);
        Page<ClassroomGroupFeedMessage> messages = messageType == null
                ? classroomGroupFeedMessageRepository.findByClassroomIdOrderByCreatedAtDesc(classroomId, pageable)
                : classroomGroupFeedMessageRepository.findByClassroomIdAndMessageTypeOrderByCreatedAtDesc(
                        classroomId,
                        messageType,
                        pageable
                );
        List<ClassroomGroupFeedMessage> visibleMessages = visibleMessages(messages.getContent());
        Map<Long, String> authorNames = authorNames(visibleMessages);
        List<ClassroomGroupFeedMessageResponse> content = visibleMessages.stream()
                .map(message -> toResponse(message, authorNames))
                .toList();
        long removedCount = messages.getContent().size() - visibleMessages.size();
        long totalElements = Math.max(content.size(), messages.getTotalElements() - removedCount);
        return new PageImpl<>(content, pageable, totalElements);
    }

    @Transactional
    public ClassroomGroupFeedMessageResponse createTextMessage(
            Long classroomId,
            CreateClassroomGroupFeedTextMessageRequest request,
            AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanAccessFeed(classroom, actor);

        ClassroomGroupFeedMessage message = new ClassroomGroupFeedMessage();
        message.setClassroomId(classroomId);
        message.setAuthorUserId(actor.getId());
        message.setMessageType(ClassroomGroupFeedMessageType.TEXT);
        message.setContent(trimToNull(request.getContent()));

        ClassroomGroupFeedMessage saved = classroomGroupFeedMessageRepository.save(message);
        return toResponse(saved, authorNames(List.of(saved)));
    }

    @Transactional
    public ClassroomGroupFeedMessageResponse shareDictionary(
            Long classroomId,
            ShareClassroomGroupFeedDictionaryRequest request,
            AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureClassroomTeacher(classroom, actor);

        Dictionary dictionary = dictionaryRepository.findById(request.getDictionaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Dictionary not found: " + request.getDictionaryId()));
        if (!classroomDictionaryAssignmentRepository.existsByClassroomIdAndDictionaryId(
                classroomId,
                request.getDictionaryId())) {
            throw new BadRequestException("Dictionary is not assigned to this classroom");
        }

        ClassroomGroupFeedMessage message = new ClassroomGroupFeedMessage();
        message.setClassroomId(classroomId);
        message.setAuthorUserId(actor.getId());
        message.setMessageType(ClassroomGroupFeedMessageType.DICTIONARY);
        message.setResourceId(dictionary.getId());
        message.setResourceTitle(dictionary.getName());
        message.setResourceSummary(dictionary.getCategory());

        ClassroomGroupFeedMessage saved = classroomGroupFeedMessageRepository.save(message);
        return toResponse(saved, authorNames(List.of(saved)));
    }

    @Transactional
    public ClassroomGroupFeedMessageResponse shareVideo(
            Long classroomId,
            ShareClassroomGroupFeedVideoRequest request,
            AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureClassroomTeacher(classroom, actor);

        VideoAsset video = videoAssetRepository.findById(request.getVideoId())
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + request.getVideoId()));
        if (!isPlayable(video)) {
            throw new BadRequestException("Video is not ready for classroom sharing");
        }
        ensureCanShareVideo(video, actor);

        ClassroomGroupFeedMessage message = new ClassroomGroupFeedMessage();
        message.setClassroomId(classroomId);
        message.setAuthorUserId(actor.getId());
        message.setMessageType(ClassroomGroupFeedMessageType.VIDEO);
        message.setResourceId(video.getId());
        message.setResourceTitle(video.getTitle());
        message.setResourceSummary(video.getDescription());

        ClassroomGroupFeedMessage saved = classroomGroupFeedMessageRepository.save(message);
        return toResponse(saved, authorNames(List.of(saved)));
    }

    @Transactional(readOnly = true)
    public VideoAccessResponse getVideoPlayback(Long classroomId, Long videoId, AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureCanAccessFeed(classroom, actor);
        if (!classroomGroupFeedMessageRepository.existsByClassroomIdAndMessageTypeAndResourceId(
                classroomId,
                ClassroomGroupFeedMessageType.VIDEO,
                videoId)) {
            throw new BadRequestException("Video is not shared to this classroom");
        }

        VideoAsset video = videoAssetRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Video not found: " + videoId));
        if (!isPlayable(video)) {
            throw new BadRequestException("Video is not ready for classroom playback");
        }
        return videoAssetService.buildAccessResponse(video, VideoAccessMode.PLAY);
    }

    @Transactional
    public ClassroomGroupFeedMessageResponse shareStudyPlan(
            Long classroomId,
            ShareClassroomGroupFeedStudyPlanRequest request,
            AppUser actor) {
        Classroom classroom = getClassroomOrThrow(classroomId);
        ensureClassroomTeacher(classroom, actor);

        StudyPlan studyPlan = studyPlanRepository.findById(request.getStudyPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Study plan not found: " + request.getStudyPlanId()));
        if (!actor.getId().equals(studyPlan.getTeacherId())) {
            throw new AccessDeniedException("Only the study plan teacher can share it to classroom chat");
        }
        if (studyPlan.getStatus() != StudyPlanStatus.PUBLISHED) {
            throw new BadRequestException("Study plan must be published before sharing to classroom chat");
        }
        if (!studyPlanClassroomRepository.existsByStudyPlanIdAndClassroomId(studyPlan.getId(), classroomId)) {
            throw new BadRequestException("Study plan does not include this classroom");
        }
        if (classroomGroupFeedMessageRepository.existsByClassroomIdAndMessageTypeAndResourceId(
                classroomId,
                ClassroomGroupFeedMessageType.STUDY_PLAN,
                studyPlan.getId())) {
            throw new BadRequestException("Study plan has already been shared to this classroom");
        }

        ClassroomGroupFeedMessage message = new ClassroomGroupFeedMessage();
        message.setClassroomId(classroomId);
        message.setAuthorUserId(actor.getId());
        message.setMessageType(ClassroomGroupFeedMessageType.STUDY_PLAN);
        message.setResourceId(studyPlan.getId());
        message.setResourceTitle(studyPlan.getName());
        message.setResourceSummary(studyPlanSummary(studyPlan, studyPlanDictionaryName(studyPlan)));

        ClassroomGroupFeedMessage saved = classroomGroupFeedMessageRepository.save(message);
        return toResponse(saved, authorNames(List.of(saved)));
    }

    private Classroom getClassroomOrThrow(Long classroomId) {
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId));
    }

    private void ensureCanAccessFeed(Classroom classroom, AppUser actor) {
        ensureClassroomActive(classroom);
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.TEACHER && actor.getId().equals(classroom.getTeacherId())) {
            return;
        }
        if (actor.getRole() == UserRole.STUDENT
                && classroomMemberRepository.existsByClassroomIdAndStudentId(classroom.getId(), actor.getId())) {
            return;
        }
        throw new AccessDeniedException("You do not have permission to access this classroom group feed");
    }

    private void ensureClassroomTeacher(Classroom classroom, AppUser actor) {
        ensureClassroomActive(classroom);
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (actor.getRole() == UserRole.TEACHER && actor.getId().equals(classroom.getTeacherId())) {
            return;
        }
        throw new AccessDeniedException("Only the classroom teacher can share resources to this classroom group feed");
    }

    private void ensureCanShareVideo(VideoAsset video, AppUser actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (video.getScopeType() == ResourceScopeType.SYSTEM) {
            return;
        }
        if (video.getScopeType() == ResourceScopeType.TEACHER && actor.getId().equals(video.getOwnerUserId())) {
            return;
        }
        throw new AccessDeniedException("Only the owner teacher can share this video");
    }

    private boolean isPlayable(VideoAsset video) {
        return video.getStatus() == VideoStatus.READY
                && video.getCloudPublishStatus() == VideoCloudPublishStatus.PUBLISHED
                && trimToNull(video.getMediaUrl()) != null;
    }

    private List<ClassroomGroupFeedMessage> visibleMessages(List<ClassroomGroupFeedMessage> messages) {
        List<Long> videoIds = messages.stream()
                .filter(message -> message.getMessageType() == ClassroomGroupFeedMessageType.VIDEO)
                .map(ClassroomGroupFeedMessage::getResourceId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (videoIds.isEmpty()) {
            return messages;
        }

        Set<Long> existingVideoIds = videoAssetRepository.findAllById(videoIds).stream()
                .map(VideoAsset::getId)
                .collect(Collectors.toSet());
        return messages.stream()
                .filter(message -> message.getMessageType() != ClassroomGroupFeedMessageType.VIDEO
                        || existingVideoIds.contains(message.getResourceId()))
                .toList();
    }

    private void ensureClassroomActive(Classroom classroom) {
        if (classroom.getStatus() == ClassroomStatus.ARCHIVED) {
            throw new AccessDeniedException("Archived classroom cannot access group feed");
        }
    }

    private Pageable buildPageable(int page, int size) {
        int normalizedPage = Math.max(page, 1) - 1;
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private ClassroomConversationResponse toConversationResponse(Classroom classroom) {
        Optional<ClassroomGroupFeedMessage> lastMessage =
                classroomGroupFeedMessageRepository.findFirstByClassroomIdOrderByCreatedAtDesc(classroom.getId());
        return new ClassroomConversationResponse(
                classroom.getId(),
                classroom.getName(),
                lastMessage.map(this::messageSummary).orElse("暂无消息"),
                lastMessage.map(ClassroomGroupFeedMessage::getCreatedAt).orElse(null)
        );
    }

    private String messageSummary(ClassroomGroupFeedMessage message) {
        return switch (message.getMessageType()) {
            case TEXT -> message.getContent() == null ? "" : message.getContent();
            case DICTIONARY -> "[词书] " + nullToUntitled(message.getResourceTitle());
            case STUDY_PLAN -> "[学习计划] " + nullToUntitled(message.getResourceTitle());
            case VIDEO -> "[视频] " + nullToUntitled(message.getResourceTitle());
        };
    }

    private String studyPlanSummary(StudyPlan studyPlan, String dictionaryName) {
        String endDate = studyPlan.getEndDate() == null ? "长期" : studyPlan.getEndDate().toString();
        String deadline = studyPlan.getDailyDeadlineTime() == null ? "未设置" : studyPlan.getDailyDeadlineTime().toString();
        return dictionaryName
                + " · 每日新词 " + studyPlan.getDailyNewCount()
                + " · " + studyPlan.getStartDate() + " 至 " + endDate
                + " · 截止 " + deadline;
    }

    private String studyPlanDictionaryName(StudyPlan studyPlan) {
        return dictionaryRepository.findById(studyPlan.getDictionaryId())
                .map(Dictionary::getName)
                .orElse("词书 " + studyPlan.getDictionaryId());
    }

    private String nullToUntitled(String value) {
        return value == null || value.isBlank() ? "未命名" : value;
    }

    private Map<Long, String> authorNames(List<ClassroomGroupFeedMessage> messages) {
        List<Long> authorIds = messages.stream()
                .map(ClassroomGroupFeedMessage::getAuthorUserId)
                .distinct()
                .toList();
        return appUserRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }

    private ClassroomGroupFeedMessageResponse toResponse(
            ClassroomGroupFeedMessage message,
            Map<Long, String> authorNames) {
        return new ClassroomGroupFeedMessageResponse(
                message.getId(),
                message.getClassroomId(),
                message.getMessageType(),
                message.getContent(),
                message.getResourceId(),
                message.getResourceTitle(),
                message.getResourceSummary(),
                message.getAuthorUserId(),
                authorNames.getOrDefault(message.getAuthorUserId(), "用户#" + message.getAuthorUserId()),
                message.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
