import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/cn";

const buttonVariants = cva(
    "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50",
    {
        variants: {
            variant: {
                default: "bg-primary text-primary-foreground shadow-sm hover:-translate-y-0.5 hover:shadow-lg",
                secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/90",
                outline: "border border-border bg-background/80 text-foreground hover:bg-accent hover:text-accent-foreground",
                ghost: "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
                destructive: "bg-destructive text-destructive-foreground hover:bg-destructive/90",
            },
            size: {
                default: "h-10 px-4 py-2",
                sm: "h-8 rounded-md px-3",
                lg: "h-11 rounded-xl px-5 text-base",
            },
        },
        defaultVariants: {
            variant: "default",
            size: "default",
        },
    },
);

type ButtonProps = JSX.ButtonHTMLAttributes<HTMLButtonElement> &
    VariantProps<typeof buttonVariants>;

export function Button(props: ButtonProps) {
    const [local, rest] = splitProps(props, ["class", "variant", "size", "type"]);

    return (
        <button
            class={cn(buttonVariants({ variant: local.variant, size: local.size }), local.class)}
            type={local.type ?? "button"}
            {...rest}
        />
    );
}
