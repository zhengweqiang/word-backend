import type { JSX } from "solid-js";
import { splitProps } from "solid-js";
import { cn } from "@/lib/cn";

export function Table(props: JSX.HTMLAttributes<HTMLDivElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <div class={cn("overflow-hidden rounded-xl border border-border/70", local.class)} {...rest} />;
}

export function TableRoot(props: JSX.HTMLAttributes<HTMLTableElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <table class={cn("w-full caption-bottom text-sm", local.class)} {...rest} />;
}

export function TableHead(props: JSX.HTMLAttributes<HTMLTableSectionElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <thead class={cn("bg-muted/40", local.class)} {...rest} />;
}

export function TableHeaderCell(props: JSX.ThHTMLAttributes<HTMLTableCellElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return (
        <th
            class={cn("px-4 py-3 text-left text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground", local.class)}
            {...rest}
        />
    );
}

export function TableBody(props: JSX.HTMLAttributes<HTMLTableSectionElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <tbody class={cn("[&_tr:last-child]:border-0", local.class)} {...rest} />;
}

export function TableRow(props: JSX.HTMLAttributes<HTMLTableRowElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <tr class={cn("border-b border-border/70 bg-background/70", local.class)} {...rest} />;
}

export function TableCell(props: JSX.TdHTMLAttributes<HTMLTableCellElement>) {
    const [local, rest] = splitProps(props, ["class"]);
    return <td class={cn("px-4 py-3 align-top", local.class)} {...rest} />;
}
