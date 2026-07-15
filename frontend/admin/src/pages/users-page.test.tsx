import { render, screen, waitFor } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { UsersPage } from "@/pages/users-page";
import type { PaginatedResponse, UserResponse, UserRole } from "@/types/api";

const authState = vi.hoisted(() => ({
    role: "ADMIN" as UserRole,
}));

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({ id: 1, username: "user", displayName: "Current User", role: authState.role, status: "ACTIVE" }),
    }),
}));

vi.mock("@/lib/api", () => ({
    api: {
        listUsersPage: vi.fn(),
        listMyStudentsPage: vi.fn(),
        createUser: vi.fn(),
        updateUserRole: vi.fn(),
        updateUserStatus: vi.fn(),
    },
}));

const userPage = (content: UserResponse[]): PaginatedResponse<UserResponse> => ({
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 20,
    number: 0,
    numberOfElements: content.length,
    first: true,
    last: true,
    empty: content.length === 0,
});

describe("UsersPage layout", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        authState.role = "ADMIN";
    });

    it("renders admin filters as a full-width row beneath the list description", async () => {
        vi.mocked(api.listUsersPage).mockResolvedValue(
            userPage([
                {
                    id: 2,
                    username: "jane",
                    displayName: "Jane Admin",
                    role: "ADMIN",
                    status: "ACTIVE",
                    lastLoginAt: "2026-07-14T22:00:00",
                },
            ]),
        );

        render(() => <UsersPage />);

        expect(await screen.findByText("Jane Admin")).toBeInTheDocument();

        const filters = screen.getByTestId("user-list-filters");

        expect(filters.parentElement).toHaveClass("flex-col");
        expect(filters.parentElement).not.toHaveClass("justify-between");
        await waitFor(() => {
            expect(filters).toHaveClass("w-full");
            expect(filters).toHaveClass("md:grid-cols-[max-content_minmax(0,420px)]");
        });

        const [roleFilter, nameFilter] = Array.from(filters.children);

        expect(roleFilter).toHaveClass("flex");
        expect(roleFilter).toHaveClass("items-center");
        expect(roleFilter).toHaveClass("gap-3");
        expect(nameFilter).toHaveClass("flex");
        expect(nameFilter).toHaveClass("items-center");
        expect(nameFilter).toHaveClass("gap-3");
    });

    it("renders teacher student search as an inline row beneath the student list description", async () => {
        authState.role = "TEACHER";
        vi.mocked(api.listMyStudentsPage).mockResolvedValue(
            userPage([
                {
                    id: 3,
                    username: "student",
                    displayName: "Student One",
                    role: "STUDENT",
                    status: "ACTIVE",
                    lastLoginAt: "2026-07-14T21:00:00",
                },
            ]),
        );

        render(() => <UsersPage />);

        expect(await screen.findByText("Student One")).toBeInTheDocument();
        expect(screen.getByText("支持按学生姓名筛选当前老师账号下的学生。")).toBeInTheDocument();

        const filters = screen.getByTestId("user-list-filters");
        const [studentFilter] = Array.from(filters.children);

        expect(filters.parentElement).toHaveClass("flex-col");
        expect(filters).toHaveClass("w-full");
        expect(filters).not.toHaveClass("md:w-[280px]");
        expect(studentFilter).toHaveClass("flex");
        expect(studentFilter).toHaveClass("items-center");
        expect(studentFilter).toHaveClass("gap-3");
        expect(screen.getByText("学生姓名")).toBeInTheDocument();
        expect(screen.getByPlaceholderText("按学生姓名筛选")).toBeInTheDocument();
    });
});
