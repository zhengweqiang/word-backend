import { cn } from "@/lib/cn";

interface ProgressProps {
    value?: number | null;
    class?: string;
}

export function Progress(props: ProgressProps) {
    const normalized = Math.max(0, Math.min(100, props.value ?? 0));

    return (
        <div class={cn("h-2.5 w-full overflow-hidden rounded-full bg-muted", props.class)}>
            <div
                class="h-full rounded-full bg-primary transition-all duration-500"
                style={{ width: `${normalized}%` }}
            />
        </div>
    );
}
