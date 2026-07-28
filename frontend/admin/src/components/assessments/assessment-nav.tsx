import { A, useLocation } from "@solidjs/router";
import { ClipboardCheck, FileSpreadsheet, LibraryBig, Send, Sheet } from "lucide-solid";
import { For, type Component, type JSX } from "solid-js";
import { cn } from "@/lib/cn";

const items: { href: string; label: string; icon: Component<JSX.SvgSVGAttributes<SVGSVGElement>> }[] = [
    { href: "/questions", label: "题库", icon: LibraryBig },
    { href: "/questions/import", label: "CSV 导入", icon: FileSpreadsheet },
    { href: "/papers", label: "试卷", icon: Sheet },
    { href: "/paper-releases", label: "发布", icon: Send },
    { href: "/paper-results", label: "结果", icon: ClipboardCheck },
];

export function AssessmentNav() {
    const location = useLocation();
    return (
        <nav aria-label="试题与试卷导航" class="flex flex-wrap gap-2 border-b border-border pb-3">
            <For each={items}>
                {(item) => {
                    const active = () => location.pathname === item.href
                        || (item.href === "/papers" && location.pathname.startsWith("/papers/"))
                        || (item.href === "/paper-results" && location.pathname.startsWith("/paper-results/"));
                    return (
                        <A
                            href={item.href}
                            class={cn(
                                "inline-flex h-9 items-center gap-2 rounded-md border px-3 text-sm font-medium",
                                active() ? "border-primary bg-primary text-primary-foreground" : "bg-background hover:bg-muted",
                            )}
                        >
                            <item.icon class="h-4 w-4" />
                            {item.label}
                        </A>
                    );
                }}
            </For>
        </nav>
    );
}
