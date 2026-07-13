import { For, type JSX } from "solid-js";
import { A, useLocation, useNavigate } from "@solidjs/router";
import {
    Bot,
    BookCopy,
    CalendarRange,
    Clapperboard,
    Cloud,
    DatabaseZap,
    LayoutDashboard,
    LogOut,
    MessageSquare,
    School,
    ShieldCheck,
    Users,
} from "lucide-solid";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/cn";
import { useAuth } from "@/features/auth/auth-context";

interface AppShellProps {
    children: JSX.Element;
}

interface NavItem {
    href: string;
    label: string;
    icon: typeof LayoutDashboard;
    roles: string[];
}

export function AppShell(props: AppShellProps) {
    const auth = useAuth();
    const location = useLocation();
    const navigate = useNavigate();

    const navigation = () => {
        const items: NavItem[] = [
            { href: "/", label: "总览", icon: LayoutDashboard, roles: ["ADMIN", "TEACHER"] },
            { href: "/users", label: "用户管理", icon: Users, roles: ["ADMIN", "TEACHER"] },
            { href: "/ai-configs", label: "AI 配置", icon: Bot, roles: ["ADMIN"] },
            { href: "/classrooms", label: "班级管理", icon: School, roles: ["ADMIN", "TEACHER"] },
            { href: "/classrooms/chat", label: "班级聊天", icon: MessageSquare, roles: ["ADMIN", "TEACHER"] },
            { href: "/dictionaries", label: "词书资源", icon: BookCopy, roles: ["ADMIN", "TEACHER"] },
            { href: "/videos", label: "视频资源", icon: Clapperboard, roles: ["ADMIN", "TEACHER"] },
            { href: "/video-storage", label: "视频存储", icon: Cloud, roles: ["ADMIN"] },
            { href: "/study-plans", label: "学习计划", icon: CalendarRange, roles: ["ADMIN", "TEACHER"] },
            { href: "/imports", label: "词书导入", icon: DatabaseZap, roles: ["ADMIN"] },
        ];

        return items.filter((item) => auth.user() && item.roles.includes(auth.user()!.role));
    };

    const handleLogout = async () => {
        await auth.logout();
        void navigate("/login", { replace: true });
    };

    return (
        <div class="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(240,180,80,0.22),_transparent_28%),linear-gradient(180deg,_#fffdf8_0%,_#f3f7f7_38%,_#e8f1f0_100%)] text-foreground">
            <div class="mx-auto grid min-h-screen max-w-[1600px] gap-6 px-4 py-4 lg:grid-cols-[280px_minmax(0,1fr)] lg:px-6">
                <aside class="rounded-[32px] border border-border/60 bg-[#0f2730] p-5 text-white shadow-haze">
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

                        <div class="rounded-2xl border border-white/10 bg-white/5 p-4">
                            <div class="flex items-center gap-3">
                                <div class="rounded-2xl bg-white/10 p-3">
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
                                    const Icon = item.icon;
                                    const isActive = () =>
                                        item.href === "/"
                                            ? location.pathname === "/"
                                            : location.pathname === item.href
                                                || (item.href !== "/classrooms" && location.pathname.startsWith(`${item.href}/`));

                                    return (
                                        <A
                                            href={item.href}
                                            class={cn(
                                                "flex items-center gap-3 rounded-2xl px-4 py-3 text-sm transition-all",
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

                <main class="space-y-6 rounded-[32px] border border-border/60 bg-white/70 p-4 shadow-haze backdrop-blur md:p-6">
                    {props.children}
                </main>
            </div>
        </div>
    );
}
