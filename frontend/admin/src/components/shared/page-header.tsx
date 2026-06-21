import type { JSX } from "solid-js";
import { Badge } from "@/components/ui/badge";

interface PageHeaderProps {
    eyebrow?: string;
    title: string;
    description: string;
    actions?: JSX.Element;
}

export function PageHeader(props: PageHeaderProps) {
    return (
        <div class="flex flex-col gap-5 md:flex-row md:items-end md:justify-between">
            <div class="space-y-3">
                {props.eyebrow ? <Badge variant="outline">{props.eyebrow}</Badge> : null}
                <div class="space-y-2">
                    <h1 class="font-display text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
                        {props.title}
                    </h1>
                    <p class="max-w-2xl text-sm leading-6 text-muted-foreground md:text-base">{props.description}</p>
                </div>
            </div>
            {props.actions ? <div class="flex items-center gap-3">{props.actions}</div> : null}
        </div>
    );
}
