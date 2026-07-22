import type { UserRole } from "@/types/api";

export function createPointAdjustmentRequestKey(
    actorRole: Extract<UserRole, "ADMIN" | "TEACHER">,
    studentId: number,
    idFactory: () => string = () => crypto.randomUUID(),
) {
    const roleCode = actorRole === "ADMIN" ? "a" : "t";
    return `pa:${roleCode}:${studentId}:${idFactory()}`;
}
