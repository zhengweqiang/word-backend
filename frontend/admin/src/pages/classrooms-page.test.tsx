import { fireEvent, render, screen, waitFor, within } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { ClassroomsPage } from "@/pages/classrooms-page";

vi.mock("@/lib/api", () => ({
    api: {
        listUsers: vi.fn(),
        listStudents: vi.fn(),
        listDictionaries: vi.fn(),
        listClassroomsPage: vi.fn(),
        createClassroom: vi.fn(),
        deleteClassroom: vi.fn(),
        getClassroomStudents: vi.fn(),
        getClassroomDictionaries: vi.fn(),
        assignDictionariesToClassroom: vi.fn(),
        removeDictionaryFromClassroom: vi.fn(),
        addStudentToClassroom: vi.fn(),
        removeStudentFromClassroom: vi.fn(),
    },
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({
            id: 1,
            username: "admin",
            displayName: "Admin",
            role: "ADMIN",
            status: "ACTIVE",
        }),
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
});
