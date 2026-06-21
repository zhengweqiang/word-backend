import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Separator(props: JSX.HTMLAttributes<HTMLDivElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <div class={cn("h-px w-full bg-border/80", local.class)} {...rest} />;
}
