import {
    createContext,
    createSignal,
    onMount,
    useContext,
    type Accessor,
    type JSX,
} from "solid-js";
import { api } from "@/lib/api";
import { clearStoredToken, setStoredToken } from "@/lib/session";
import type { LoginResponse, UserResponse } from "@/types/api";

interface AuthContextValue {
    user: Accessor<UserResponse | undefined>;
    ready: Accessor<boolean>;
    login: (payload: { username: string; password: string }) => Promise<LoginResponse>;
    logout: () => Promise<void>;
    refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue>();

export function AuthProvider(props: { children: JSX.Element }) {
    const [user, setUser] = createSignal<UserResponse>();
    const [ready, setReady] = createSignal(false);

    const refresh = async () => {
        try {
            const me = await api.authMe();
            setUser(me);
        } catch (error) {
            setUser(undefined);
        } finally {
            setReady(true);
        }
    };

    const login = async (payload: { username: string; password: string }) => {
        const response = await api.login(payload);
        setStoredToken(response.token);
        setUser(response.user);
        setReady(true);
        return response;
    };

    const logout = async () => {
        try {
            await api.logout();
        } catch (error) {
            // Ignore logout transport errors and clear client state anyway.
        } finally {
            clearStoredToken();
            setUser(undefined);
        }
    };

    onMount(() => {
        void refresh();
    });

    return (
        <AuthContext.Provider
            value={{
                user,
                ready,
                login,
                logout,
                refresh,
            }}
        >
            {props.children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth must be used within AuthProvider");
    }
    return context;
}
