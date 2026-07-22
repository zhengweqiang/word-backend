import { describe, expect, it, vi } from "vitest";
import { createPointAdjustmentRequestKey } from "@/lib/point-request-key";

describe("createPointAdjustmentRequestKey", () => {
    it("creates an auditable request key with the student and actor context", () => {
        const idFactory = vi.fn(() => "7f91df46-3d34-4ca0-af05-3e9cf2de86c4");

        const requestKey = createPointAdjustmentRequestKey("TEACHER", 42, idFactory);

        expect(requestKey).toBe("pa:t:42:7f91df46-3d34-4ca0-af05-3e9cf2de86c4");
        expect(requestKey.length).toBeLessThanOrEqual(64);
        expect(idFactory).toHaveBeenCalledOnce();
    });
});
