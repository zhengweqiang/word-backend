import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { TeacherClassChatPage } from "@/pages/teacher-class-chat-page";

const authUser = vi.hoisted(() => ({
    current: {
        id: 7,
        username: "teacher",
        displayName: "老师甲",
        role: "TEACHER",
        status: "ACTIVE",
    },
}));

vi.mock("@/lib/api", () => ({
    api: {
        listClassroomConversations: vi.fn(),
        listClassroomGroupFeedMessages: vi.fn(),
        createClassroomGroupFeedTextMessage: vi.fn(),
        shareClassroomGroupFeedStudyPlan: vi.fn(),
        shareClassroomGroupFeedVideo: vi.fn(),
        getClassroomGroupFeedVideoPlayback: vi.fn(),
        listStudyPlans: vi.fn(),
        listVideosPage: vi.fn(),
    },
}));

vi.mock("@volcengine/veplayer", () => ({
    default: vi.fn().mockImplementation(function () {
        return {
            destroy: vi.fn().mockResolvedValue(undefined),
        };
    }),
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => authUser.current,
    }),
}));

const conversationsPage = {
    content: [
        {
            classroomId: 101,
            classroomName: "二班",
            lastMessageSummary: "[学习计划] 高考 30 天计划",
            lastMessageAt: "2026-06-28T10:00:00",
        },
        {
            classroomId: 100,
            classroomName: "一班",
            lastMessageSummary: "今天背完了",
            lastMessageAt: "2026-06-28T09:00:00",
        },
    ],
    totalElements: 2,
    totalPages: 1,
    number: 0,
    size: 20,
    numberOfElements: 2,
};

describe("TeacherClassChatPage", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.clearAllMocks();
        vi.mocked(api.listClassroomConversations).mockResolvedValue(conversationsPage);
        vi.mocked(api.listClassroomGroupFeedMessages).mockResolvedValue({
            content: [
                {
                    id: 501,
                    classroomId: 101,
                    messageType: "STUDY_PLAN",
                    resourceId: 54,
                    resourceTitle: "暑假预习计划",
                    resourceSummary: "词书 10 · 每日新词 20 · 2026-07-01 至 2026-07-30 · 截止 21:00",
                    authorUserId: 7,
                    authorName: "老师甲",
                    createdAt: "2026-06-28T10:00:00",
                },
                {
                    id: 502,
                    classroomId: 101,
                    messageType: "TEXT",
                    content: "老师我完成了",
                    authorUserId: 20,
                    authorName: "学生甲",
                    createdAt: "2026-06-28T10:05:00",
                },
            ],
            totalElements: 2,
            totalPages: 1,
            number: 0,
            size: 20,
            numberOfElements: 2,
        });
        vi.mocked(api.listStudyPlans).mockResolvedValue([
            {
                id: 55,
                name: "高考 30 天计划",
                teacherId: 7,
                dictionaryId: 10,
                dictionaryName: "高考词汇",
                classroomIds: [101],
                startDate: "2026-07-01",
                endDate: "2026-07-30",
                timezone: "Asia/Shanghai",
                dailyNewCount: 20,
                dailyReviewLimit: 60,
                reviewMode: "FIXED_INTERVAL",
                reviewIntervals: [1, 3, 7, 14],
                completionThreshold: 85,
                dailyDeadlineTime: "21:00",
                attentionTrackingEnabled: true,
                minFocusSecondsPerWord: 2,
                maxFocusSecondsPerWord: 18,
                longStayWarningSeconds: 25,
                idleTimeoutSeconds: 12,
                status: "PUBLISHED",
                studentCount: 18,
            },
            {
                id: 56,
                name: "草稿计划",
                teacherId: 7,
                dictionaryId: 10,
                dictionaryName: "高考词汇",
                classroomIds: [101],
                startDate: "2026-07-01",
                endDate: null,
                timezone: "Asia/Shanghai",
                dailyNewCount: 20,
                dailyReviewLimit: 60,
                reviewMode: "FIXED_INTERVAL",
                reviewIntervals: [1, 3, 7, 14],
                completionThreshold: 85,
                dailyDeadlineTime: "21:00",
                attentionTrackingEnabled: true,
                minFocusSecondsPerWord: 2,
                maxFocusSecondsPerWord: 18,
                longStayWarningSeconds: 25,
                idleTimeoutSeconds: 12,
                status: "DRAFT",
                studentCount: 0,
            },
        ]);
        vi.mocked(api.listVideosPage).mockResolvedValue({
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: 0,
            size: 50,
            numberOfElements: 0,
        });
        vi.mocked(api.shareClassroomGroupFeedStudyPlan).mockResolvedValue({
            id: 503,
            classroomId: 101,
            messageType: "STUDY_PLAN",
            resourceId: 55,
            resourceTitle: "高考 30 天计划",
            authorUserId: 7,
            authorName: "老师甲",
            createdAt: "2026-06-28T10:10:00",
        });
        vi.mocked(api.shareClassroomGroupFeedVideo).mockResolvedValue({
            id: 504,
            classroomId: 101,
            messageType: "VIDEO",
            resourceId: 30,
            resourceTitle: "课堂讲解视频",
            authorUserId: 7,
            authorName: "老师甲",
            createdAt: "2026-06-28T10:12:00",
        });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("selects the latest conversation and renders student replies without unread state", async () => {
        render(() => <TeacherClassChatPage />);

        expect(await screen.findByText("班级聊天")).toBeInTheDocument();
        expect(await screen.findByRole("button", { name: /二班/ })).toBeInTheDocument();
        expect(screen.queryByText("未读")).not.toBeInTheDocument();
        expect(await screen.findByText("暑假预习计划")).toBeInTheDocument();
        expect(await screen.findByText("老师我完成了")).toBeInTheDocument();

        expect(api.listClassroomGroupFeedMessages).toHaveBeenCalledWith(101, {
            page: 1,
            size: 20,
        });
    });

    it("shares only published study plans that cover the selected classroom", async () => {
        render(() => <TeacherClassChatPage />);

        fireEvent.click(await screen.findByRole("button", { name: "学习计划" }));

        const select = await screen.findByLabelText("选择已发布学习计划");
        expect(select).toHaveValue("55");
        expect(screen.queryByText("草稿计划")).not.toBeInTheDocument();

        fireEvent.click(screen.getByRole("button", { name: "分享学习计划" }));

        await waitFor(() => {
            expect(api.shareClassroomGroupFeedStudyPlan).toHaveBeenCalledWith(101, { studyPlanId: 55 });
        });
    });

    it("shares a selected published video to the selected classroom", async () => {
        vi.mocked(api.listVideosPage).mockResolvedValue({
            content: [
                {
                    id: 30,
                    title: "课堂讲解视频",
                    originalFileName: "lesson.mp4",
                    contentType: "video/mp4",
                    fileSize: 1024,
                    tencentFileId: "media-30",
                    mediaUrl: "https://example.com/lesson.mp4",
                    status: "READY",
                    cloudPublishStatus: "PUBLISHED",
                    createdBy: 7,
                    createdByDisplayName: "老师甲",
                    ownerUserId: 7,
                    scopeType: "TEACHER",
                    storageConfigId: 1,
                    canManage: true,
                    canPreview: true,
                },
            ],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 50,
            numberOfElements: 1,
        });

        render(() => <TeacherClassChatPage />);

        fireEvent.click(await screen.findByRole("button", { name: "视频" }));

        const select = await screen.findByLabelText("选择云端可播视频");
        expect(select).toHaveValue("30");
        expect(api.listVideosPage).toHaveBeenCalledWith({
            page: 1,
            size: 50,
        });

        fireEvent.click(screen.getByRole("button", { name: "分享视频" }));

        await waitFor(() => {
            expect(api.shareClassroomGroupFeedVideo).toHaveBeenCalledWith(101, { videoId: 30 });
        });
    });

    it("shows unavailable video details when videos exist but are not ready to share", async () => {
        vi.mocked(api.listVideosPage).mockResolvedValue({
            content: [
                {
                    id: 31,
                    title: "待处理课堂视频",
                    originalFileName: "processing.mp4",
                    contentType: "video/mp4",
                    fileSize: 2048,
                    tencentFileId: "media-31",
                    status: "PROCESSING",
                    cloudPublishStatus: "UNPUBLISHED",
                    createdBy: 1,
                    createdByDisplayName: "管理员",
                    ownerUserId: 1,
                    scopeType: "SYSTEM",
                    storageConfigId: 1,
                    canManage: false,
                    canPreview: false,
                },
            ],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 50,
            numberOfElements: 1,
        });

        render(() => <TeacherClassChatPage />);

        fireEvent.click(await screen.findByRole("button", { name: "视频" }));

        const select = await screen.findByLabelText("选择云端可播视频");
        expect(select).toHaveValue("");
        expect(await screen.findByText("待处理课堂视频 · 处理中 · 云端停用")).toBeInTheDocument();
        expect(screen.getByText("视频需要同步到可预览并启用云端播放后，才能分享到班级。")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "分享视频" })).toBeDisabled();
    });

    it("opens a classroom chat video message in the shared preview player", async () => {
        vi.mocked(api.listClassroomGroupFeedMessages).mockResolvedValue({
            content: [
                {
                    id: 505,
                    classroomId: 101,
                    messageType: "VIDEO",
                    resourceId: 30,
                    resourceTitle: "Classroom chat video",
                    resourceSummary: "Video summary",
                    authorUserId: 7,
                    authorName: "Teacher",
                    createdAt: "2026-07-14T11:31:00",
                },
            ],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
            numberOfElements: 1,
        });
        vi.mocked(api.getClassroomGroupFeedVideoPlayback).mockResolvedValue({
            videoId: 30,
            mode: "PLAY",
            url: "https://example.com/classroom-chat-video.mp4",
        });

        render(() => <TeacherClassChatPage />);

        expect(await screen.findByText("Classroom chat video")).toBeInTheDocument();
        const messageHeader = screen.getByTestId("class-chat-message-header-505");
        expect(messageHeader).toHaveClass("justify-between");
        expect(screen.getByTestId("class-chat-message-time-505")).toHaveClass("ml-auto");
        expect(screen.getByTestId("class-chat-message-time-505")).toHaveClass("text-right");

        const previewButtonRow = screen.getByTestId("class-chat-video-preview-row-505");
        expect(previewButtonRow).toHaveClass("justify-end");
        const previewButton = screen.getByTestId("class-chat-video-preview-505");
        expect(previewButton).toHaveClass("h-7");
        expect(previewButton).toHaveClass("px-2");
        expect(previewButton).toHaveClass("text-xs");
        expect(previewButton).toHaveClass("border-border/70");
        fireEvent.click(screen.getByTestId("class-chat-video-preview-505"));

        await waitFor(() => {
            expect(api.getClassroomGroupFeedVideoPlayback).toHaveBeenCalledWith(101, 30);
            expect(screen.getByTestId("veplayer-preview")).toHaveAttribute(
                "data-url",
                "https://example.com/classroom-chat-video.mp4",
            );
        });
    });

    it("polls conversations and current messages every 30 seconds", async () => {
        render(() => <TeacherClassChatPage />);

        await screen.findByText("班级聊天");
        expect(api.listClassroomConversations).toHaveBeenCalledTimes(1);

        await vi.advanceTimersByTimeAsync(30000);

        await waitFor(() => {
            expect(api.listClassroomConversations).toHaveBeenCalledTimes(2);
            expect(api.listClassroomGroupFeedMessages).toHaveBeenCalledTimes(2);
        });
    });
});
