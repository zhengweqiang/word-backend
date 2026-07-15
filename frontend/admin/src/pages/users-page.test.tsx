import { render, screen, waitFor } from "@solidjs/testing-library";
import { describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { UsersPage } from "@/pages/users-page";
import type { PaginatedResponse, UserResponse } from "@/types/api";

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({ id: 1, username: "admin", displayName: "Admin", role: "ADMIN", status: "ACTIVE" }),
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
});
