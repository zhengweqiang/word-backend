import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Label(props: JSX.LabelHTMLAttributes<HTMLLabelElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <label class={cn("text-sm font-medium text-foreground", local.class)} {...rest} />;
}
