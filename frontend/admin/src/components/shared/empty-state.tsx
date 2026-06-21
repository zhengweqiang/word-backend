import type { JSX } from "solid-js";
import { Card, CardContent } from "@/components/ui/card";

interface EmptyStateProps {
    title: string;
    description: string;
    actions?: JSX.Element;
}

export function EmptyState(props: EmptyStateProps) {
    return (
        <Card class="border-dashed bg-background/60">
            <CardContent class="flex flex-col items-start gap-4 p-6">
                <div class="space-y-2">
                    <h3 class="font-display text-lg font-semibold text-foreground">{props.title}</h3>
                    <p class="text-sm text-muted-foreground">{props.description}</p>
                </div>
                {props.actions}
            </CardContent>
        </Card>
    );
}
