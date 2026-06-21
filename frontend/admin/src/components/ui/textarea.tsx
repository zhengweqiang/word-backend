import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Textarea(props: JSX.TextareaHTMLAttributes<HTMLTextAreaElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return (
        <textarea
            class={cn(
                "flex min-h-[120px] w-full rounded-lg border border-input bg-background/70 px-3 py-2 text-sm text-foreground shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
                local.class,
            )}
            {...rest}
        />
    );
}
