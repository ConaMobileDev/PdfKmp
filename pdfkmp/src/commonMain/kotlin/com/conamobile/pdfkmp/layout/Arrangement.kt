package com.conamobile.pdfkmp.layout

/**
 * How children of a [com.conamobile.pdfkmp.dsl.RowScope] are distributed
 * along the horizontal axis when their combined intrinsic width is smaller
 * than the row's available width.
 *
 * Naming follows Compose's `Arrangement` so cross-domain Compose developers
 * can carry their intuition over.
 */
public enum class HorizontalArrangement {
    /** Pack children at the left edge. Trailing space stays empty. */
    Start,

    /** Group all children at the horizontal centre with no gap between them. */
    Center,

    /** Pack children at the right edge. Leading space stays empty. */
    End,

    /**
     * Place the first child against the left edge and the last against the
     * right; distribute the remaining space evenly between adjacent
     * children.
     */
    SpaceBetween,

    /**
     * Distribute the remaining space so that there is equal padding around
     * each child (half-padding at the row's edges).
     */
    SpaceAround,

    /**
     * Distribute the remaining space so that the gap between every two
     * adjacent children — including the gaps at the row edges — is the
     * same.
     */
    SpaceEvenly,
}

/**
 * How children of a [com.conamobile.pdfkmp.dsl.ColumnScope] are
 * distributed along the vertical axis when their combined intrinsic height
 * is smaller than the column's available height.
 */
public enum class VerticalArrangement {
    /** Pack children at the top. */
    Top,

    /** Group children at the vertical centre. */
    Center,

    /** Pack children at the bottom. */
    Bottom,

    /** First child at the top, last at the bottom, equal gaps between. */
    SpaceBetween,

    /** Equal padding around each child (half at the column's top and bottom). */
    SpaceAround,

    /** Equal gap between every two children, including at the column's edges. */
    SpaceEvenly,
}

/**
 * How a row's children are aligned along the cross-axis (vertical) when
 * they have different intrinsic heights.
 */
public enum class VerticalAlignment {
    /** Align children with the top of the row. */
    Top,

    /** Align children's vertical centres with the row's vertical centre. */
    Center,

    /** Align children with the bottom of the row. */
    Bottom,
}

/**
 * How a column's children are aligned along the cross-axis (horizontal)
 * when they have different intrinsic widths.
 */
public enum class HorizontalAlignment {
    /** Align children with the column's left edge. */
    Start,

    /** Align children's horizontal centres with the column's centre. */
    Center,

    /** Align children with the column's right edge. */
    End,
}
