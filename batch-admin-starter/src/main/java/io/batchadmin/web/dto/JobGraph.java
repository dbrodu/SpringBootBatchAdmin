package io.batchadmin.web.dto;

import java.util.List;

/**
 * A laid-out view of the trigger graph (pipelines): jobs as nodes, triggers as directed edges,
 * positioned with a simple longest-path layering so it can be drawn as an SVG without any
 * client-side layout library.
 *
 * @param width  overall drawing width (px)
 * @param height overall drawing height (px)
 */
public record JobGraph(int width, int height, List<Node> nodes, List<Edge> edges) {

    /** A job box. {@code x,y} is the top-left corner. */
    public record Node(String name, String label, int x, int y, int w, int h) {
    }

    /**
     * A directed trigger edge from {@code source}'s right edge to {@code target}'s left edge.
     *
     * @param condition {@code SUCCESS} / {@code FAILURE} / {@code ANY}
     */
    public record Edge(String source, String target, String condition, boolean enabled,
                       int x1, int y1, int x2, int y2, int labelX, int labelY) {
    }
}
