package com.uxplima.auth.paper.testing;

import java.awt.Color;
import java.awt.Image;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapView;

import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * A server that makes and keeps maps, for the tests of a picture on a map. MockBukkit's own map cannot be told to
 * stop tracking, so the maps here are plain ones that keep their renderers and switches.
 */
public final class MapServer {

    private final Map<Integer, FakeView> maps = new HashMap<>();
    private final Server server;

    public MapServer(ServerMock mock) {
        server = Mockito.mock(Server.class);
        World world = mock.getWorlds().getFirst();
        Mockito.when(server.getWorlds()).thenReturn(List.of(world));
        Mockito.when(server.createMap(world)).thenAnswer(call -> {
            FakeView made = new FakeView(maps.size() + 1);
            maps.put(made.getId(), made);
            return made;
        });
        Mockito.when(server.getMap(ArgumentMatchers.anyInt()))
                .thenAnswer(call -> maps.get(call.<Integer>getArgument(0)));
    }

    /** The server to open a map on. */
    public Server server() {
        return server;
    }

    /** A map that keeps its renderers and switches, which is all the captcha asks of one. */
    public static final class FakeView implements MapView {
        private final int id;
        private final List<org.bukkit.map.MapRenderer> renderers =
                new ArrayList<>(List.of(new org.bukkit.map.MapRenderer() {
                    @Override
                    public void render(MapView map, MapCanvas canvas, org.bukkit.entity.Player player) {}
                }));
        private boolean tracking = true;
        private boolean locked;

        public FakeView(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public boolean isVirtual() {
            return false;
        }

        @Override
        public MapView.Scale getScale() {
            return MapView.Scale.CLOSEST;
        }

        @Override
        public void setScale(MapView.Scale scale) {}

        @Override
        public int getCenterX() {
            return 0;
        }

        @Override
        public int getCenterZ() {
            return 0;
        }

        @Override
        public void setCenterX(int x) {}

        @Override
        public void setCenterZ(int z) {}

        @Override
        public org.bukkit.@Nullable World getWorld() {
            return null;
        }

        @Override
        public void setWorld(org.bukkit.World world) {}

        @Override
        public List<org.bukkit.map.MapRenderer> getRenderers() {
            return renderers;
        }

        @Override
        public void addRenderer(org.bukkit.map.MapRenderer renderer) {
            renderers.add(renderer);
        }

        @Override
        public boolean removeRenderer(org.bukkit.map.@Nullable MapRenderer renderer) {
            return renderers.remove(renderer);
        }

        @Override
        public boolean isTrackingPosition() {
            return tracking;
        }

        @Override
        public void setTrackingPosition(boolean trackingPosition) {
            tracking = trackingPosition;
        }

        @Override
        public boolean isUnlimitedTracking() {
            return false;
        }

        @Override
        public void setUnlimitedTracking(boolean unlimited) {}

        @Override
        public boolean isLocked() {
            return locked;
        }

        @Override
        public void setLocked(boolean locked) {
            this.locked = locked;
        }
    }

    /** A canvas that keeps every image drawn on it. */
    public static final class Canvas implements MapCanvas {
        public final List<Image> images = new ArrayList<>();

        @Override
        public MapView getMapView() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MapCursorCollection getCursors() {
            return new MapCursorCollection();
        }

        @Override
        public void setCursors(MapCursorCollection cursors) {}

        @Override
        public void setPixelColor(int x, int y, @Nullable Color color) {}

        @Override
        public @Nullable Color getPixelColor(int x, int y) {
            return null;
        }

        @Override
        public Color getBasePixelColor(int x, int y) {
            return Color.WHITE;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setPixel(int x, int y, byte color) {}

        @Override
        @SuppressWarnings("removal")
        public byte getPixel(int x, int y) {
            return 0;
        }

        @Override
        @SuppressWarnings("removal")
        public byte getBasePixel(int x, int y) {
            return 0;
        }

        @Override
        public void drawImage(int x, int y, Image image) {
            images.add(image);
        }

        @Override
        public void drawText(int x, int y, MapFont font, String text) {}
    }
}
