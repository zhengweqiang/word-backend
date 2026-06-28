import { fireEvent, render, screen, waitFor, within } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { ClassroomsPage } from "@/pages/classrooms-page";

const authUser = vi.hoisted(() => ({
    current: {
        id: 1,
        username: "admin",
        displayName: "Admin",
        role: "ADMIN",
        status: "ACTIVE",
    },
}));

vi.mock("@/lib/api", () => ({
    api: {
        listUsers: vi.fn(),
        listStudents: vi.fn(),
        listDictionaries: vi.fn(),
        listVideosPage: vi.fn(),
        listClassroomsPage: vi.fn(),
        createClassroom: vi.fn(),
        deleteClassroom: vi.fn(),
        getClassroomStudents: vi.fn(),
        getClassroomDictionaries: vi.fn(),
        listClassroomGroupFeedMessages: vi.fn(),
        createClassroomGroupFeedTextMessage: vi.fn(),
        shareClassroomGroupFeedDictionary: vi.fn(),
        shareClassroomGroupFeedVideo: vi.fn(),
        assignDictionariesToClassroom: vi.fn(),
        removeDictionaryFromClassroom: vi.fn(),
        addStudentToClassroom: vi.fn(),
        removeStudentFromClassroom: vi.fn(),
    },
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => authUser.current,
    }),
}));

const emptyClassroomsPage = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
    numberOfElements: 0,
};

describe("ClassroomsPage", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authUser.current = {
            id: 1,
            username: "admin",
            displayName: "Admin",
            role: "ADMIN",
            status: "ACTIVE",
        };
        vi.mocked(api.listUsers).mockResolvedValue([
            {
                id: 1,
                username: "admin",
                displayName: "Admin",
                role: "ADMIN",
                status: "ACTIVE",
            },
        ]);
        vi.mocked(api.listStudents).mockResolvedValue([]);
        vi.mocked(api.listDictionaries).mockResolvedValue([]);
        vi.mocked(api.listVideosPage).mockResolvedValue({
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: 0,
            size: 50,
            numberOfElements: 0,
        });
        vi.mocked(api.listClassroomsPage).mockResolvedValue(emptyClassroomsPage);
        vi.mocked(api.createClassroom).mockResolvedValue({
            id: 12,
            name: "初中英语词汇班",
            description: "用于星火初中英语词汇1600词 25天学习计划",
            teacherId: 2,
            teacherName: "初中英语老师",
            studentCount: 0,
        });
        vi.mocked(api.getClassroomStudents).mockResolvedValue([]);
        vi.mocked(api.getClassroomDictionaries).mockResolvedValue([]);
        vi.mocked(api.listClassroomGroupFeedMessages).mockResolvedValue({
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: 0,
            size: 20,
            numberOfElements: 0,
        });
    });

    it("shows an empty teacher state and disables classroom creation when admin has no teachers", async () => {
        render(() => <ClassroomsPage />);

        fireEvent.click(await screen.findByRole("button", { name: "创建班级" }));

        expect(await screen.findByText("暂无可用老师")).toBeInTheDocument();
        expect(screen.getByText("管理员创建班级前需要先创建老师账号。")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "去用户管理" })).toHaveAttribute("href", "/users");
        expect(within(screen.getByRole("dialog", { name: "创建班级" })).getByRole("button", { name: "创建班级" }))
            .toBeDisabled();
    });

    it("requires admin to choose a teacher before creating a classroom", async () => {
        vi.mocked(api.listUsers).mockResolvedValue([
            {
                id: 1,
                username: "admin",
                displayName: "Admin",
                role: "ADMIN",
                status: "ACTIVE",
            },
            {
                id: 2,
                username: "junior_english_teacher",
                displayName: "初中英语老师",
                role: "TEACHER",
                status: "ACTIVE",
            },
        ]);

        render(() => <ClassroomsPage />);

        fireEvent.click(await screen.findByRole("button", { name: "创建班级" }));
        const dialog = await screen.findByRole("dialog", { name: "创建班级" });
        fireEvent.input(within(dialog).getAllByRole("textbox")[0], {
            target: { value: "初中英语词汇班" },
        });

        await within(dialog).findByRole("combobox");

        const createButton = within(dialog).getByRole("button", { name: "创建班级" });
        expect(createButton).toBeDisabled();

        fireEvent.change(within(dialog).getByRole("combobox"), {
            target: { value: "2" },
        });
        fireEvent.click(createButton);

        await waitFor(() => {
            expect(api.createClassroom).toHaveBeenCalledWith(
                expect.objectContaining({
                    name: "初中英语词汇班",
                    teacherId: 2,
                }),
            );
        });
    });

    it("shows the classroom group feed and lets a teacher post a text message", async () => {
        authUser.current = {
            id: 2,
            username: "teacher",
            displayName: "初中英语老师",
            role: "TEACHER",
            status: "ACTIVE",
        };
        vi.mocked(api.listClassroomsPage).mockResolvedValue({
            content: [
                {
                    id: 31,
                    name: "初中英语词汇班",
                    description: "25 天学习计划",
                    teacherId: 2,
                    teacherName: "初中英语老师",
                    studentCount: 18,
                },
            ],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
            numberOfElements: 1,
        });
        vi.mocked(api.getClassroomDictionaries).mockResolvedValue([
            {
                id: 8,
                name: "星火初中英语词汇 1600",
            },
        ]);
        vi.mocked(api.listVideosPage).mockResolvedValue({
            content: [
                {
                    id: 6,
                    title: "词根记忆导学",
                    originalFileName: "intro.mp4",
                    fileSize: 128,
                    tencentFileId: "video-file-id",
                    status: "READY",
                    cloudPublishStatus: "PUBLISHED",
                    createdBy: 2,
                    createdByDisplayName: "初中英语老师",
                    ownerUserId: 2,
                    scopeType: "PRIVATE",
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
        vi.mocked(api.listClassroomGroupFeedMessages).mockResolvedValue({
            content: [
                {
                    id: 101,
                    classroomId: 31,
                    messageType: "DICTIONARY",
                    resourceId: 8,
                    resourceTitle: "星火初中英语词汇 1600",
                    resourceSummary: "1600 词",
                    authorUserId: 2,
                    authorName: "初中英语老师",
                    createdAt: "2026-06-26T08:20:00",
                },
            ],
            totalElements: 1,
            totalPages: 1,
            number: 0,
            size: 20,
            numberOfElements: 1,
        });
        vi.mocked(api.createClassroomGroupFeedTextMessage).mockResolvedValue({
            id: 102,
            classroomId: 31,
            messageType: "TEXT",
            content: "今天先完成第 1 单元。",
            authorUserId: 2,
            authorName: "初中英语老师",
            createdAt: "2026-06-26T08:30:00",
        });

        render(() => <ClassroomsPage />);

        expect(await screen.findByText("班级群消息流")).toBeInTheDocument();
        expect((await screen.findAllByText("星火初中英语词汇 1600")).length).toBeGreaterThan(0);
        expect(api.listClassroomGroupFeedMessages).toHaveBeenCalledWith(31, {
            page: 1,
            size: 20,
        });

        fireEvent.input(screen.getByPlaceholderText("给这个班级发布一条文字留言"), {
            target: { value: "今天先完成第 1 单元。" },
        });
        fireEvent.click(screen.getByRole("button", { name: "发布留言" }));

        await waitFor(() => {
            expect(api.createClassroomGroupFeedTextMessage).toHaveBeenCalledWith(31, {
                content: "今天先完成第 1 单元。",
            });
        });
    });
});
