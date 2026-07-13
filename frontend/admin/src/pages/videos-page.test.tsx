import { fireEvent, render, screen, waitFor } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { VideosPage } from "@/pages/videos-page";
import type { VideoResponse } from "@/types/api";

vi.mock("@/lib/api", () => ({
    api: {
        listVideosPage: vi.fn(),
        uploadVideo: vi.fn(),
        getVideoAccess: vi.fn(),
        syncCloudVideos: vi.fn(),
        syncVideo: vi.fn(),
        publishVideo: vi.fn(),
        unpublishVideo: vi.fn(),
        deleteVideo: vi.fn(),
    },
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({
            id: 1,
            username: "admin",
            displayName: "System Admin",
            role: "ADMIN",
            status: "ACTIVE",
        }),
    }),
}));

const processingVideo: VideoResponse = {
    id: 1,
    title: "liu-xiaoyan-live-upload-1782629017846",
    originalFileName: "liu-xiaoyan-live-upload-1782629017846",
    contentType: "video/mp4",
    fileSize: 147849216,
    tencentFileId: "media-1",
    status: "PROCESSING",
    cloudPublishStatus: "UNPUBLISHED",
    createdBy: 1,
    createdByDisplayName: "System Admin",
    ownerUserId: 1,
    scopeType: "SYSTEM",
    storageConfigId: 1,
    storageConfigName: "火山云点播",
    canManage: true,
    canPreview: false,
};

const readyVideo: VideoResponse = {
    ...processingVideo,
    status: "READY",
    mediaUrl: "https://example.com/video.mp4",
    canPreview: true,
};

const videoPage = (video: VideoResponse) => ({
    content: [video],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 12,
    numberOfElements: 1,
});

describe("VideosPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listVideosPage).mockResolvedValue(videoPage(processingVideo));
        vi.mocked(api.syncVideo).mockResolvedValue(readyVideo);
        vi.mocked(api.publishVideo).mockResolvedValue({
            ...readyVideo,
            cloudPublishStatus: "PUBLISHED",
        });
        vi.mocked(api.getVideoAccess).mockResolvedValue({
            videoId: 1,
            mode: "PREVIEW",
            url: "https://example.com/video.mp4",
        });
    });

    it("lets admin publish a processing video through the publish endpoint", async () => {
        render(() => <VideosPage />);

        const publishButton = await screen.findByRole("button", { name: "云端启用" });
        expect(publishButton).toBeEnabled();

        fireEvent.click(publishButton);

        await waitFor(() => {
            expect(api.publishVideo).toHaveBeenCalledWith(1);
        });
        expect(api.syncVideo).not.toHaveBeenCalled();
    });

    it("lets admin preview a processing video by syncing it first", async () => {
        render(() => <VideosPage />);

        const previewButton = await screen.findByRole("button", { name: "预览" });
        expect(previewButton).toBeEnabled();

        fireEvent.click(previewButton);

        await waitFor(() => {
            expect(api.syncVideo).toHaveBeenCalledWith(1);
            expect(api.getVideoAccess).toHaveBeenCalledWith(1);
            expect(document.querySelector("video")?.getAttribute("src")).toBe("http://example.com/video.mp4");
        });
    });
});
