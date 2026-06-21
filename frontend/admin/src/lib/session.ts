export const TOKEN_STORAGE_KEY = "word_atelier_token";

export const getStoredToken = () => window.localStorage.getItem(TOKEN_STORAGE_KEY);

export const setStoredToken = (token: string) => {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
};

export const clearStoredToken = () => {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
};
