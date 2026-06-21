import type { JSX } from "solid-js";
import { Show } from "solid-js";
import { AppShell } from "@/components/layout/app-shell";
import { useAuth } from "@/features/auth/auth-context";

function RedirectToUnifiedLogin() {
    window.location.replace("/");
    return null;
}

function RedirectStudentToWorkspace() {
    window.location.replace("/");
    return null;
}

export function ProtectedLayout(props: { children?: JSX.Element }) {
    const auth = useAuth();

    return (
        <Show
            when={auth.ready()}
            fallback={
                <div class="flex min-h-screen items-center justify-center bg-[linear-gradient(180deg,_#fffdf8_0%,_#e8f1f0_100%)]">
                    <div class="space-y-3 text-center">
                        <div class="mx-auto h-12 w-12 animate-pulse rounded-2xl bg-primary/20" />
                        <p class="text-sm uppercase tracking-[0.2em] text-muted-foreground">正在校验登录态</p>
                    </div>
                </div>
            }
        >
            <Show
                when={auth.user()}
                fallback={<RedirectToUnifiedLogin />}
            >
                <Show
                    when={auth.user()?.role !== "STUDENT"}
                    fallback={<RedirectStudentToWorkspace />}
                >
                    <AppShell>{props.children}</AppShell>
                </Show>
            </Show>
        </Show>
    );
}
