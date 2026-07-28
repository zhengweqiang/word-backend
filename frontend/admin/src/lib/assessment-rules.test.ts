import { describe, expect, it } from "vitest";
import {
    canArchivePaper,
    canManagePaper,
    canPublishPaper,
    parseAcceptedAnswers,
    resolveTargetPreview,
    validateQuestionDraft,
} from "@/lib/assessment-rules";
import type { UserResponse } from "@/types/api";

const student = (id: number, displayName: string): UserResponse => ({
    id,
    username: `student-${id}`,
    displayName,
    role: "STUDENT",
    status: "ACTIVE",
});

describe("assessment rules", () => {
    it("uses the backend FILL_IN_BLANK enum and parses documented pipe-separated accepted answers", () => {
        expect(parseAcceptedAnswers(" abandon | Forsake | abandon ", "FILL_IN_BLANK"))
            .toEqual(["abandon", "Forsake"]);
    });

    it("matches backend question type, option, answer, and score validation", () => {
        expect(validateQuestionDraft({
            questionType: "SINGLE_CHOICE",
            stem: "词义",
            options: { A: "答案一" },
            acceptedAnswers: ["A", "B"],
            score: "1.234",
        })).toEqual(expect.arrayContaining([
            "选择题必须提供 2 至 4 个非空选项。",
            "单选题必须且只能设置 1 个正确答案。",
            "正确答案必须对应现有选项。",
            "分值最多支持 17 位整数和 2 位小数。",
        ]));

        expect(validateQuestionDraft({
            questionType: "MULTIPLE_CHOICE",
            stem: "多选",
            options: { A: "一", B: "二" },
            acceptedAnswers: ["A"],
            score: "2",
        })).toContain("多选题至少需要 2 个不同的正确答案。");

        expect(validateQuestionDraft({
            questionType: "FILL_IN_BLANK",
            stem: " ",
            options: { A: "不应存在" },
            acceptedAnswers: [],
            score: "0",
        })).toEqual(expect.arrayContaining([
            "题干不能为空。",
            "填空题不能设置选项。",
            "填空题至少需要 1 个可接受答案。",
            "分值必须大于 0。",
        ]));
    });

    it("keeps editing owner-only while allowing admins to archive and publish", () => {
        expect(canManagePaper({ role: "TEACHER", userId: 7, ownerUserId: 8 })).toBe(false);
        expect(canPublishPaper({ role: "TEACHER", userId: 7, ownerUserId: 8, status: "READY" })).toBe(false);
        expect(canPublishPaper({ role: "TEACHER", userId: 7, ownerUserId: 7, status: "READY" })).toBe(true);
        expect(canManagePaper({ role: "ADMIN", userId: 1, ownerUserId: 8 })).toBe(false);
        expect(canArchivePaper({ role: "ADMIN", userId: 1, ownerUserId: 8 })).toBe(true);
        expect(canArchivePaper({ role: "TEACHER", userId: 7, ownerUserId: 8 })).toBe(false);
        expect(canPublishPaper({ role: "ADMIN", userId: 1, ownerUserId: 8, status: "READY" })).toBe(true);
        expect(canPublishPaper({ role: "ADMIN", userId: 1, ownerUserId: 8, status: "DRAFT" })).toBe(false);
    });

    it("deduplicates classroom and explicit targets while retaining every source", () => {
        const targets = resolveTargetPreview({
            classroomIds: [31, 32],
            classroomMembers: new Map([
                [31, [student(12, "小明"), student(13, "小红")]],
                [32, [student(12, "小明")]],
            ]),
            explicitStudents: [student(12, "小明"), student(14, "小刚")],
        });

        expect(targets.map((target) => target.student.id)).toEqual([12, 13, 14]);
        expect(targets[0]).toMatchObject({ explicit: true, classroomIds: [31, 32] });
    });
});
