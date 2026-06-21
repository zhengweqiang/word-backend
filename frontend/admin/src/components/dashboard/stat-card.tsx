import type { JSX } from "solid-js";
import { ArrowUpRight } from "lucide-solid";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

interface StatCardProps {
    label: string;
    value: string;
    hint: string;
    accent?: JSX.Element;
}

export function StatCard(props: StatCardProps) {
    return (
        <Card class="overflow-hidden">
            <CardHeader class="pb-3">
                <div class="flex items-center justify-between gap-3">
                    <p class="text-xs uppercase tracking-[0.2em] text-muted-foreground">{props.label}</p>
                    <ArrowUpRight class="h-4 w-4 text-muted-foreground" />
                </div>
                <CardTitle class="text-3xl">{props.value}</CardTitle>
            </CardHeader>
            <CardContent class="flex items-end justify-between gap-3">
                <p class="text-sm text-muted-foreground">{props.hint}</p>
                {props.accent}
            </CardContent>
        </Card>
    );
}
