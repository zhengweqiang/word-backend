export const formatDateTime = (value?: string | null) => {
    if (!value) {
        return "未记录";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    }).format(new Date(value));
};

export const formatDate = (value?: string | null) => {
    if (!value) {
        return "未设置";
    }
    return new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
    }).format(new Date(value));
};

export const formatNumber = (value?: number | null) => {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return "0";
    }
    return new Intl.NumberFormat("zh-CN").format(value);
};

export const formatPercent = (value?: number | null) => {
    if (value === null || value === undefined || Number.isNaN(value)) {
        return "0%";
    }
    return `${Number(value).toFixed(1)}%`;
};

export const enumLabel = (value?: string | null) => {
    if (!value) {
        return "未知";
    }
    return value
        .toLowerCase()
        .split("_")
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(" ");
};

export const compactFileSize = (value?: number | null) => {
    if (!value) {
        return "0 B";
    }
    const units = ["B", "KB", "MB", "GB"];
    let size = value;
    let index = 0;
    while (size >= 1024 && index < units.length - 1) {
        size /= 1024;
        index += 1;
    }
    return `${size.toFixed(size >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
};
