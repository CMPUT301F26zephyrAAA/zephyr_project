// Checks the raster tiles that the OpenStreetMap embed can draw in its current viewport.
// AI Citation [AI-47]
// OpenAI, Codex, November 27, 2026. Added viewport-aware tile error detection.
// See Bhav Saluja's entry in the team's AI Documentation Google Doc.

(timeoutMs) => {
    if (window !== window.top || location.pathname !== "/export/embed.html") {
        return;
    }

    // The embed creates its map inside window.onload, so capture it before that handler runs.
    // https://github.com/openstreetmap/openstreetmap-website/blob/master/app/assets/javascripts/embed.js.erb
    window.addEventListener("load", () => {
        const mapLibre = window.OSM?.MapLibre;
        const EmbedMap = mapLibre?.Map;
        if (!EmbedMap) {
            return;
        }

        mapLibre.Map = class extends EmbedMap {
            constructor(options) {
                super(options);
                mapLibre.Map = EmbedMap;
                const map = this;
                let timeout;

                // MapLibre's renderer includes cached fallback tiles in these coordinates.
                // https://github.com/maplibre/maplibre-gl-js/blob/main/src/tile/tile_manager.ts
                window.zephyrHasVisibleTiles = () => {
                    const managers = map.style?.tileManagers ?? map.style?.sourceCaches;
                    const raster = managers?.["raster-tiles-mapnik"];
                    return typeof raster?.getVisibleCoordinates === "function"
                        ? raster.getVisibleCoordinates(false).length > 0
                        : null;
                };

                const stopCheck = () => {
                    clearTimeout(timeout);
                    timeout = undefined;
                };
                const scheduleCheck = () => {
                    if (timeout !== undefined || map.isMoving()) {
                        return;
                    }
                    timeout = setTimeout(() => {
                        timeout = undefined;
                        if (!map.isMoving() && window.zephyrHasVisibleTiles() === false) {
                            window.zephyrViewportStatus.postMessage("empty");
                        }
                    }, timeoutMs);
                };

                map.on("movestart", stopCheck);
                map.on("moveend", scheduleCheck);
                map.on("error", scheduleCheck);
                scheduleCheck();
            }
        };
    }, { capture: true, once: true });
}
