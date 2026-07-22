import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api";

const jsonResponse = (body: unknown = []) => new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
});

describe("study plans API", () => {
    beforeEach(() => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse()));
    });

    it("forwards every selected classroom when loading common dictionaries", async () => {
        const listDictionaries = api.listDictionaries as unknown as (
            classroomIds: number[],
        ) => Promise<unknown>;

        await listDictionaries([12, 18]);

        expect(fetch).toHaveBeenCalledWith(
            "/api/dictionaries?classroomIds=12&classroomIds=18",
            expect.objectContaining({ credentials: "include" }),
        );
    });
});
