import type { UserRole } from "@/types/api";

export interface NavItem {
    href: string;
    label: string;
    icon: "dashboard" | "users" | "points" | "bot" | "school" | "chat" | "book" | "video" | "cloud" | "calendar" | "import";
    roles: UserRole[];
}

const items: NavItem[] = [
    { href: "/", label: "总览", icon: "dashboard", roles: ["ADMIN", "TEACHER"] },
    { href: "/users", label: "用户管理", icon: "users", roles: ["ADMIN", "TEACHER"] },
    { href: "/points", label: "积分管理", icon: "points", roles: ["ADMIN", "TEACHER"] },
    { href: "/ai-configs", label: "AI 配置", icon: "bot", roles: ["ADMIN"] },
    { href: "/classrooms", label: "班级管理", icon: "school", roles: ["ADMIN", "TEACHER"] },
    { href: "/classrooms/chat", label: "班级聊天", icon: "chat", roles: ["ADMIN", "TEACHER"] },
    { href: "/dictionaries", label: "词书资源", icon: "book", roles: ["ADMIN", "TEACHER"] },
    { href: "/videos", label: "视频资源", icon: "video", roles: ["ADMIN", "TEACHER"] },
    { href: "/video-storage", label: "视频存储", icon: "cloud", roles: ["ADMIN"] },
    { href: "/study-plans", label: "学习计划", icon: "calendar", roles: ["ADMIN", "TEACHER"] },
    { href: "/imports", label: "词书导入", icon: "import", roles: ["ADMIN"] },
];

export function getNavigationForRole(role?: UserRole) {
    return role ? items.filter((item) => item.roles.includes(role)) : [];
}
