import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/cn";

const badgeVariants = cva(
    "inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.18em]",
    {
        variants: {
            variant: {
                default: "border-transparent bg-primary/10 text-primary",
                secondary: "border-transparent bg-secondary text-secondary-foreground",
                success: "border-transparent bg-success/15 text-success",
                warning: "border-transparent bg-warning/15 text-warning",
                destructive: "border-transparent bg-destructive/15 text-destructive",
                outline: "border-border text-muted-foreground",
            },
        },
        defaultVariants: {
            variant: "default",
        },
    },
);

type BadgeProps = JSX.HTMLAttributes<HTMLDivElement> & VariantProps<typeof badgeVariants>;

export function Badge(props: BadgeProps) {
    const [local, rest] = splitProps(props, ["class", "variant"]);
    return <div class={cn(badgeVariants({ variant: local.variant }), local.class)} {...rest} />;
}
