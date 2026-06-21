import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Input(props: JSX.InputHTMLAttributes<HTMLInputElement>) {
    const [local, rest] = splitProps(props, ["class", "type"]);
    return (
        <input
            type={local.type ?? "text"}
            class={cn(
                "flex h-11 w-full rounded-lg border border-input bg-background/70 px-3 py-2 text-sm text-foreground shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50",
                local.class,
            )}
            {...rest}
        />
    );
}
