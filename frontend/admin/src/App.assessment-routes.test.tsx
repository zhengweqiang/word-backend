import { render, screen } from "@solidjs/testing-library";
import type { JSX } from "solid-js";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "@/App";

vi.mock("@/features/auth/auth-context", () => ({
    AuthProvider: (props: { children: JSX.Element }) => <>{props.children}</>,
}));
vi.mock("@/components/layout/protected-layout", () => ({
    ProtectedLayout: (props: { children?: JSX.Element }) => <>{props.children}</>,
}));
vi.mock("@/pages/question-bank-page", () => ({ QuestionBankPage: () => <p>route-question-bank</p> }));
vi.mock("@/pages/question-import-page", () => ({ QuestionImportPage: () => <p>route-question-import</p> }));
vi.mock("@/pages/paper-template-page", () => ({ PaperTemplatePage: () => <p>route-paper-list</p> }));
vi.mock("@/pages/paper-editor-page", () => ({ PaperEditorPage: () => <p>route-paper-editor</p> }));
vi.mock("@/pages/paper-release-page", () => ({ PaperReleasePage: () => <p>route-paper-release</p> }));
vi.mock("@/pages/paper-result-page", () => ({ PaperResultPage: () => <p>route-paper-result</p> }));

describe("assessment route mounts", () => {
    afterEach(() => window.history.replaceState({}, "", "/"));

    it.each([
        ["/admin/questions", "route-question-bank"],
        ["/admin/questions/import", "route-question-import"],
        ["/admin/papers", "route-paper-list"],
        ["/admin/papers/42/edit", "route-paper-editor"],
        ["/admin/paper-releases", "route-paper-release"],
        ["/admin/paper-results", "route-paper-result"],
        ["/admin/paper-results/77", "route-paper-result"],
    ])("mounts %s through the real router", async (path, marker) => {
        window.history.replaceState({}, "", path);
        render(() => <App />);
        expect(await screen.findByText(marker)).toBeInTheDocument();
    });
});
