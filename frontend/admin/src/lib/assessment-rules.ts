import type {
    PaperTemplateStatus,
    QuestionType,
    UserResponse,
    UserRole,
} from "@/types/api";

export const questionTypeLabels: Record<QuestionType, string> = {
    SINGLE_CHOICE: "单选题",
    MULTIPLE_CHOICE: "多选题",
    FILL_IN_BLANK: "填空题",
};

const uniqueValues = (values: string[], caseInsensitive = false) => {
    const seen = new Set<string>();
    return values.filter((value) => {
        const key = caseInsensitive ? value.toLocaleLowerCase() : value;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
};

export function parseAcceptedAnswers(raw: string, questionType: QuestionType): string[] {
    const separator = questionType === "FILL_IN_BLANK" ? "|" : /[|,]/;
    const values = raw.split(separator).map((value) => value.trim()).filter(Boolean);
    if (questionType === "FILL_IN_BLANK") {
        return uniqueValues(values, true);
    }
    return uniqueValues(values.map((value) => value.toUpperCase()));
}

export interface QuestionDraftValidationInput {
    questionType: QuestionType;
    stem: string;
    options: Record<string, string>;
    acceptedAnswers: string[];
    score: string;
}

export const validateAssessmentScore = (rawScore: string) => {
    const value = rawScore.trim();
    if (!value || !Number.isFinite(Number(value)) || Number(value) <= 0) {
        return "分值必须大于 0。";
    }
    const match = value.match(/^(\d+)(?:\.(\d+))?$/);
    const integerDigits = match?.[1].replace(/^0+(?=\d)/, "").length ?? Number.POSITIVE_INFINITY;
    const fractionDigits = match?.[2]?.length ?? 0;
    if (!match || integerDigits > 17 || fractionDigits > 2) {
        return "分值最多支持 17 位整数和 2 位小数。";
    }
    return null;
};

export function validateQuestionDraft(input: QuestionDraftValidationInput): string[] {
    const errors: string[] = [];
    const options = Object.fromEntries(
        Object.entries(input.options)
            .map(([key, value]) => [key.trim().toUpperCase(), value.trim()])
            .filter(([key, value]) => key && value),
    );
    const answers = uniqueValues(input.acceptedAnswers.map((answer) => answer.trim()).filter(Boolean));

    if (!input.stem.trim()) errors.push("题干不能为空。");
    const invalidScore = validateAssessmentScore(input.score);
    if (invalidScore) errors.push(invalidScore);

    if (input.questionType === "FILL_IN_BLANK") {
        if (Object.keys(options).length) errors.push("填空题不能设置选项。");
        if (!answers.length) errors.push("填空题至少需要 1 个可接受答案。");
        return errors;
    }

    if (Object.keys(options).length < 2 || Object.keys(options).length > 4) {
        errors.push("选择题必须提供 2 至 4 个非空选项。");
    }
    if (input.questionType === "SINGLE_CHOICE" && answers.length !== 1) {
        errors.push("单选题必须且只能设置 1 个正确答案。");
    }
    if (input.questionType === "MULTIPLE_CHOICE" && answers.length < 2) {
        errors.push("多选题至少需要 2 个不同的正确答案。");
    }
    if (answers.some((answer) => !(answer.toUpperCase() in options))) {
        errors.push("正确答案必须对应现有选项。");
    }
    return errors;
}

interface OwnershipInput {
    role: UserRole;
    userId: number;
    ownerUserId: number;
}

export const canManagePaper = ({ userId, ownerUserId }: OwnershipInput) =>
    userId === ownerUserId;

export const canArchivePaper = ({ role, userId, ownerUserId }: OwnershipInput) =>
    role === "ADMIN" || userId === ownerUserId;

export const canPublishPaper = ({ status, ...ownership }: OwnershipInput & { status: PaperTemplateStatus }) =>
    status === "READY" && canArchivePaper(ownership);

export interface ResolvedTargetPreview {
    student: UserResponse;
    classroomIds: number[];
    explicit: boolean;
}

export function resolveTargetPreview(input: {
    classroomIds: number[];
    classroomMembers: Map<number, UserResponse[]>;
    explicitStudents: UserResponse[];
}): ResolvedTargetPreview[] {
    const resolved = new Map<number, ResolvedTargetPreview>();
    input.classroomIds.forEach((classroomId) => {
        (input.classroomMembers.get(classroomId) ?? []).forEach((student) => {
            const target = resolved.get(student.id) ?? { student, classroomIds: [], explicit: false };
            if (!target.classroomIds.includes(classroomId)) target.classroomIds.push(classroomId);
            resolved.set(student.id, target);
        });
    });
    input.explicitStudents.forEach((student) => {
        const target = resolved.get(student.id) ?? { student, classroomIds: [], explicit: false };
        target.explicit = true;
        resolved.set(student.id, target);
    });
    return [...resolved.values()];
}
