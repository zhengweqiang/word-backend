import { describe, expect, it } from "vitest";
import { getNavigationForRole } from "@/components/layout/navigation";

describe("points navigation", () => {
    it.each(["ADMIN", "TEACHER"] as const)("shows points management to %s", (role) => {
        expect(getNavigationForRole(role)).toEqual(expect.arrayContaining([
            expect.objectContaining({ href: "/points", label: "积分管理" }),
        ]));
    });

    it("does not expose points management to students", () => {
        expect(getNavigationForRole("STUDENT").some((item) => item.href === "/points")).toBe(false);
    });
});
