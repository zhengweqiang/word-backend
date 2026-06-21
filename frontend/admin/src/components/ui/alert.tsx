import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Alert(props: JSX.HTMLAttributes<HTMLDivElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return (
        <div
            class={cn(
                "rounded-xl border border-border/80 bg-background/70 p-4 text-sm text-muted-foreground",
                local.class,
            )}
            {...rest}
        />
    );
}
