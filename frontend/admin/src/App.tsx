import { Route, Router } from "@solidjs/router";
import { ProtectedLayout } from "@/components/layout/protected-layout";
import { AuthProvider } from "@/features/auth/auth-context";
import { AiConfigsPage } from "@/pages/ai-configs-page";
import { ClassroomsPage } from "@/pages/classrooms-page";
import { DictionariesPage } from "@/pages/dictionaries-page";
import { ImportCenterPage } from "@/pages/import-center-page";
import { NotFoundPage } from "@/pages/not-found-page";
import { OverviewPage } from "@/pages/overview-page";
import { StudyPlansPage } from "@/pages/study-plans-page";
import { TeacherClassChatPage } from "@/pages/teacher-class-chat-page";
import { UsersPage } from "@/pages/users-page";
import { VideoStoragePage } from "@/pages/video-storage-page";
import { VideosPage } from "@/pages/videos-page";

function App() {
    return (
        <AuthProvider>
            <Router base="/admin">
                <Route path="/" component={ProtectedLayout}>
                    <Route path="/" component={OverviewPage} />
                    <Route path="/users" component={UsersPage} />
                    <Route path="/ai-configs" component={AiConfigsPage} />
                    <Route path="/classrooms/chat" component={TeacherClassChatPage} />
                    <Route path="/classrooms" component={ClassroomsPage} />
                    <Route path="/dictionaries" component={DictionariesPage} />
                    <Route path="/videos" component={VideosPage} />
                    <Route path="/video-storage" component={VideoStoragePage} />
                    <Route path="/study-plans" component={StudyPlansPage} />
                    <Route path="/imports" component={ImportCenterPage} />
                    <Route path="*404" component={NotFoundPage} />
                </Route>
            </Router>
        </AuthProvider>
    );
}

export default App;
