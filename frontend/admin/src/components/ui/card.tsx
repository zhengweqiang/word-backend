import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Card(props: JSX.HTMLAttributes<HTMLDivElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return (
        <div
            class={cn(
                "rounded-2xl border border-border/70 bg-card/90 text-card-foreground shadow-haze backdrop-blur",
                local.class,
            )}
            {...rest}
        />
    );
}

export function CardHeader(props: JSX.HTMLAttributes<HTMLDivElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <div class={cn("flex flex-col gap-2 p-6", local.class)} {...rest} />;
}

export function CardTitle(props: JSX.HTMLAttributes<HTMLHeadingElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <h3 class={cn("font-display text-xl font-semibold tracking-tight", local.class)} {...rest} />;
}

export function CardDescription(props: JSX.HTMLAttributes<HTMLParagraphElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <p class={cn("text-sm text-muted-foreground", local.class)} {...rest} />;
}

export function CardContent(props: JSX.HTMLAttributes<HTMLDivElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <div class={cn("p-6 pt-0", local.class)} {...rest} />;
}
