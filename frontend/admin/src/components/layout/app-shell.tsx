import { For, type JSX } from "solid-js";
import { A, useLocation, useNavigate } from "@solidjs/router";
import {
    Bot,
    BookCopy,
    CalendarRange,
    CircleDollarSign,
    Clapperboard,
    Cloud,
    DatabaseZap,
    LayoutDashboard,
    LogOut,
    MessageSquare,
    School,
    ShieldCheck,
    ClipboardList,
    Users,
} from "lucide-solid";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/cn";
import { useAuth } from "@/features/auth/auth-context";
import { getNavigationForRole } from "@/components/layout/navigation";

const navigationIcons = {
    dashboard: LayoutDashboard,
    users: Users,
    points: CircleDollarSign,
    bot: Bot,
    school: School,
    chat: MessageSquare,
    book: BookCopy,
    video: Clapperboard,
    cloud: Cloud,
    calendar: CalendarRange,
    import: DatabaseZap,
    assessment: ClipboardList,
};

interface AppShellProps {
    children: JSX.Element;
}

export function AppShell(props: AppShellProps) {
    const auth = useAuth();
    const location = useLocation();
    const navigate = useNavigate();

    const navigation = () => getNavigationForRole(auth.user()?.role);

    const handleLogout = async () => {
        await auth.logout();
        void navigate("/login", { replace: true });
    };

    return (
        <div class="min-h-screen bg-background text-foreground">
            <div class="grid min-h-screen lg:grid-cols-[252px_minmax(0,1fr)]">
                <aside class="border-r border-white/10 bg-[#17323a] p-4 text-white">
                    <div class="space-y-4">
                        <div class="space-y-3">
                            <Badge class="border-white/15 bg-white/10 text-white" variant="outline">
                                Word Atelier
                            </Badge>
                            <div>
                                <h1 class="font-display text-2xl font-semibold tracking-tight">后台工作台</h1>
                                <p class="mt-2 text-sm leading-6 text-white/65">
                                    面向管理员与老师的控制面板，围绕班级、词书和学习计划编排。
                                </p>
                            </div>
                        </div>

                        <div class="rounded-md border border-white/10 bg-white/5 p-3">
                            <div class="flex items-center gap-3">
                                <div class="rounded-md bg-white/10 p-2">
                                    <ShieldCheck class="h-5 w-5" />
                                </div>
                                <div>
                                    <p class="text-sm font-medium">{auth.user()?.displayName}</p>
                                    <p class="text-xs uppercase tracking-[0.18em] text-white/55">{auth.user()?.role}</p>
                                </div>
                            </div>
                        </div>

                        <nav class="space-y-2">
                            <For each={navigation()}>
                                {(item) => {
                                    const Icon = navigationIcons[item.icon];
                                    const isActive = () =>
                                        item.href === "/"
                                            ? location.pathname === "/"
                                            : location.pathname === item.href
                                                || (item.href !== "/classrooms" && location.pathname.startsWith(`${item.href}/`));

                                    return (
                                        <A
                                            href={item.href}
                                            class={cn(
                                                "flex items-center gap-3 rounded-md px-3 py-2.5 text-sm transition-colors",
                                                isActive()
                                                    ? "bg-white text-[#0f2730] shadow-lg"
                                                    : "text-white/72 hover:bg-white/10 hover:text-white",
                                            )}
                                        >
                                            <Icon class="h-4 w-4" />
                                            <span>{item.label}</span>
                                        </A>
                                    );
                                }}
                            </For>
                        </nav>
                    </div>

                    <div class="mt-8">
                        <Button class="w-full justify-start bg-white/10 text-white hover:bg-white/15" onClick={handleLogout}>
                            <LogOut class="h-4 w-4" />
                            退出登录
                        </Button>
                    </div>
                </aside>

                <main class="min-w-0 space-y-6 bg-background p-4 md:p-6 xl:p-8">
                    {props.children}
                </main>
            </div>
        </div>
    );
}
