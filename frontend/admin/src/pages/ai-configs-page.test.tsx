import { fireEvent, render, screen } from "@solidjs/testing-library";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { AiConfigsPage } from "@/pages/ai-configs-page";

vi.mock("@/features/auth/auth-context", () => ({
    useAuth: () => ({
        user: () => ({ id: 1, username: "admin", displayName: "Admin", role: "ADMIN", status: "ACTIVE" }),
    }),
}));

vi.mock("@/lib/api", () => ({
    api: {
        listAiConfigs: vi.fn(),
        backfillSyllables: vi.fn(),
    },
}));

describe("AiConfigsPage syllable backfill", () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.mocked(api.listAiConfigs).mockResolvedValue([]);
        vi.mocked(api.backfillSyllables).mockResolvedValue({
            attempted: 3,
            updated: 2,
            skipped: 0,
            failures: [{ metaWordId: 9, word: "fragile", reason: "音节不能还原原词" }],
        });
    });

    it("runs backfill and renders the result summary", async () => {
        render(() => <AiConfigsPage />);

        fireEvent.click(await screen.findByRole("button", { name: "回填音节" }));

        expect(api.backfillSyllables).toHaveBeenCalledWith(200);
        expect(await screen.findByText("已更新 2 / 3 个单词")).toBeInTheDocument();
        expect(screen.getByText("fragile：音节不能还原原词")).toBeInTheDocument();
    });
});
