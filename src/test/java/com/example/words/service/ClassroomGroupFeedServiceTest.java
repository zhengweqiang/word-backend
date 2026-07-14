package com.example.words.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.words.dto.ClassroomGroupFeedMessageResponse;
import com.example.words.dto.ShareClassroomGroupFeedVideoRequest;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomGroupFeedMessage;
import com.example.words.model.ClassroomGroupFeedMessageType;
import com.example.words.model.ClassroomStatus;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.model.UserStatus;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ClassroomGroupFeedServiceTest {

    @Mock
    private ClassroomGroupFeedMessageRepository classroomGroupFeedMessageRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomMemberRepository classroomMemberRepository;

    @Mock
    private ClassroomDictionaryAssignmentRepository classroomDictionaryAssignmentRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private VideoAssetRepository videoAssetRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private StudyPlanClassroomRepository studyPlanClassroomRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private VideoAssetService videoAssetService;

    private ClassroomGroupFeedService service;

    @BeforeEach
    void setUp() {
        service = new ClassroomGroupFeedService(
                classroomGroupFeedMessageRepository,
                classroomRepository,
                classroomMemberRepository,
                classroomDictionaryAssignmentRepository,
                dictionaryRepository,
                videoAssetRepository,
                studyPlanRepository,
                studyPlanClassroomRepository,
                accessControlService,
                appUserRepository,
                videoAssetService
        );
    }

    @Test
    void adminCanSharePublishedReadyVideoToClassroomManagedByTeacher() {
        AppUser admin = user(1L, "System Admin", UserRole.ADMIN);
        Classroom classroom = classroom(100L, 7L);
        VideoAsset video = readyPublishedVideo(30L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(videoAssetRepository.findById(30L)).thenReturn(Optional.of(video));
        when(classroomGroupFeedMessageRepository.save(any(ClassroomGroupFeedMessage.class)))
                .thenAnswer(invocation -> {
                    ClassroomGroupFeedMessage message = invocation.getArgument(0);
                    message.setId(501L);
                    return message;
                });
        when(appUserRepository.findAllById(List.of(1L))).thenReturn(List.of(admin));

        ClassroomGroupFeedMessageResponse response = service.shareVideo(
                100L,
                new ShareClassroomGroupFeedVideoRequest(30L),
                admin
        );

        assertThat(response.getMessageType()).isEqualTo(ClassroomGroupFeedMessageType.VIDEO);
        assertThat(response.getResourceId()).isEqualTo(30L);
        assertThat(response.getResourceTitle()).isEqualTo("课堂讲解视频");
        assertThat(response.getAuthorName()).isEqualTo("System Admin");
    }

    @Test
    void teacherCannotShareVideoToAnotherTeachersClassroom() {
        AppUser teacher = user(8L, "Other Teacher", UserRole.TEACHER);
        Classroom classroom = classroom(100L, 7L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));

        assertThatThrownBy(() -> service.shareVideo(
                100L,
                new ShareClassroomGroupFeedVideoRequest(30L),
                teacher
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherCannotShareAnotherTeachersTeacherScopedVideoToOwnClassroom() {
        AppUser teacher = user(7L, "Teacher", UserRole.TEACHER);
        Classroom classroom = classroom(100L, 7L);
        VideoAsset video = readyPublishedVideo(30L);
        video.setScopeType(ResourceScopeType.TEACHER);
        video.setOwnerUserId(8L);
        video.setCreatedBy(8L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(videoAssetRepository.findById(30L)).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> service.shareVideo(
                100L,
                new ShareClassroomGroupFeedVideoRequest(30L),
                teacher
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void teacherCanSharePlayableSystemVideoToOwnClassroom() {
        AppUser teacher = user(7L, "Teacher", UserRole.TEACHER);
        Classroom classroom = classroom(100L, 7L);
        VideoAsset video = readyPublishedVideo(30L);

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(videoAssetRepository.findById(30L)).thenReturn(Optional.of(video));
        when(classroomGroupFeedMessageRepository.save(any(ClassroomGroupFeedMessage.class)))
                .thenAnswer(invocation -> {
                    ClassroomGroupFeedMessage message = invocation.getArgument(0);
                    message.setId(501L);
                    return message;
                });
        when(appUserRepository.findAllById(List.of(7L))).thenReturn(List.of(teacher));

        ClassroomGroupFeedMessageResponse response = service.shareVideo(
                100L,
                new ShareClassroomGroupFeedVideoRequest(30L),
                teacher
        );

        assertThat(response.getResourceId()).isEqualTo(30L);
    }

    @Test
    void listMessagesShouldHideVideoMessagesWhoseVideoWasDeleted() {
        AppUser teacher = user(7L, "Teacher", UserRole.TEACHER);
        Classroom classroom = classroom(100L, 7L);
        ClassroomGroupFeedMessage existingVideoMessage = message(
                501L,
                ClassroomGroupFeedMessageType.VIDEO,
                30L,
                "Existing video"
        );
        ClassroomGroupFeedMessage deletedVideoMessage = message(
                502L,
                ClassroomGroupFeedMessageType.VIDEO,
                31L,
                "Deleted video"
        );
        ClassroomGroupFeedMessage textMessage = message(
                503L,
                ClassroomGroupFeedMessageType.TEXT,
                null,
                null
        );
        textMessage.setContent("Hello class");

        when(classroomRepository.findById(100L)).thenReturn(Optional.of(classroom));
        when(classroomGroupFeedMessageRepository.findByClassroomIdOrderByCreatedAtDesc(
                100L,
                PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(
                List.of(existingVideoMessage, deletedVideoMessage, textMessage),
                PageRequest.of(0, 20),
                3
        ));
        when(videoAssetRepository.findAllById(List.of(30L, 31L))).thenReturn(List.of(readyPublishedVideo(30L)));
        when(appUserRepository.findAllById(List.of(7L))).thenReturn(List.of(teacher));

        List<ClassroomGroupFeedMessageResponse> responses = service.listMessages(
                100L,
                1,
                20,
                null,
                teacher
        ).getContent();

        assertThat(responses)
                .extracting(ClassroomGroupFeedMessageResponse::getResourceTitle)
                .contains("Existing video")
                .doesNotContain("Deleted video");
        assertThat(responses)
                .extracting(ClassroomGroupFeedMessageResponse::getContent)
                .contains("Hello class");
    }

    private AppUser user(Long id, String displayName, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setPasswordHash("password");
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private Classroom classroom(Long id, Long teacherId) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setName("一班");
        classroom.setTeacherId(teacherId);
        classroom.setStatus(ClassroomStatus.ACTIVE);
        return classroom;
    }

    private VideoAsset readyPublishedVideo(Long id) {
        VideoAsset video = new VideoAsset();
        video.setId(id);
        video.setTitle("课堂讲解视频");
        video.setDescription("第一课");
        video.setOriginalFileName("lesson.mp4");
        video.setContentType("video/mp4");
        video.setFileSize(1024L);
        video.setTencentFileId("media-" + id);
        video.setMediaUrl("https://example.com/lesson.mp4");
        video.setStatus(VideoStatus.READY);
        video.setCloudPublishStatus(VideoCloudPublishStatus.PUBLISHED);
        video.setCreatedBy(1L);
        video.setOwnerUserId(1L);
        video.setScopeType(ResourceScopeType.SYSTEM);
        video.setStorageConfigId(1L);
        return video;
    }

    private ClassroomGroupFeedMessage message(
            Long id,
            ClassroomGroupFeedMessageType messageType,
            Long resourceId,
            String resourceTitle) {
        ClassroomGroupFeedMessage message = new ClassroomGroupFeedMessage();
        message.setId(id);
        message.setClassroomId(100L);
        message.setAuthorUserId(7L);
        message.setMessageType(messageType);
        message.setResourceId(resourceId);
        message.setResourceTitle(resourceTitle);
        return message;
    }
}
