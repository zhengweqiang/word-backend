import VePlayer from "@volcengine/veplayer";
import "@volcengine/veplayer/index.min.css";
import { createEffect, onCleanup } from "solid-js";

interface VePlayerPreviewProps {
    url: string;
    coverUrl?: string | null;
}

const vePlayerLicense = import.meta.env.VITE_VEPLAYER_LICENSE?.trim() ?? "";

export function VePlayerPreview(props: VePlayerPreviewProps) {
    let rootRef: HTMLDivElement | undefined;
    let player: VePlayer | null = null;
    let setupVersion = 0;

    const destroyPlayer = () => {
        const currentPlayer = player;
        player = null;
        void currentPlayer?.destroy();
    };

    createEffect(() => {
        const url = props.url;
        if (!rootRef || !url) {
            return;
        }

        if (!vePlayerLicense) {
            destroyPlayer();
            return;
        }

        const version = ++setupVersion;
        destroyPlayer();
        rootRef.replaceChildren();
        void VePlayer.setLicenseConfig({ license: vePlayerLicense }).then(() => {
            if (!rootRef || version !== setupVersion) {
                return;
            }

            player = new VePlayer({
                root: rootRef,
                url,
                poster: props.coverUrl ?? undefined,
                autoplay: true,
                fluid: true,
                videoFillMode: "auto",
                disableVodLogOptsCheck: true,
            });
        });
    });

    onCleanup(() => {
        setupVersion += 1;
        destroyPlayer();
    });

    return (
        <div
            ref={rootRef}
            class="veplayer-preview min-h-[360px] w-full overflow-hidden rounded-2xl bg-black"
            data-testid="veplayer-preview"
            data-url={props.url}
            data-player={vePlayerLicense ? "veplayer" : "html5"}
        >
            {!vePlayerLicense && (
                <video
                    class="block min-h-[360px] max-h-[70vh] w-full rounded-2xl bg-black object-contain"
                    controls
                    autoplay
                    playsinline
                    poster={props.coverUrl ?? undefined}
                    src={props.url}
                    data-testid="video-preview-fallback"
                />
            )}
        </div>
    );
}
