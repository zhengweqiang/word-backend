import { A } from "@solidjs/router";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";

export function NotFoundPage() {
    return (
        <div class="flex min-h-[60vh] items-center justify-center">
            <Card class="max-w-lg">
                <CardContent class="space-y-4 p-8 text-center">
                    <p class="text-xs uppercase tracking-[0.22em] text-muted-foreground">404</p>
                    <h1 class="font-display text-3xl font-semibold text-foreground">页面不存在</h1>
                    <p class="text-sm text-muted-foreground">目标路由没有匹配到后台页面，可以回到总览重新进入。</p>
                    <A href="/">
                        <Button>返回总览</Button>
                    </A>
                </CardContent>
            </Card>
        </div>
    );
}
