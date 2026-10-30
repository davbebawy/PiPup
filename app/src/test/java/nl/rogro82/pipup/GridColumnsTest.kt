package nl.rogro82.pipup

import org.junit.Assert.assertEquals
import org.junit.Test

class GridColumnsTest {

    @Test
    fun automaticColumns() {
        assertEquals(listOf(1, 2, 2, 2, 3, 3, 3, 3, 3), (1..9).map { gridColumns(it, null) })
        assertEquals(1, gridColumns(0, null))
    }

    @Test
    fun requestedColumnsAreClamped() {
        assertEquals(4, gridColumns(4, 4))
        assertEquals(3, gridColumns(3, 6))
        assertEquals(1, gridColumns(4, 0))
    }

    @Test
    fun tilesDropEmptyAndCapAtNine() {
        val items = (1..12).map { PopupProps.Media.GridItem(uri = "http://h/api/webrtc?src=c$it") } +
            PopupProps.Media.GridItem(uri = " ")
        val grid = PopupProps.Media.Grid(items = items)
        assertEquals(PopupProps.MAX_GRID_TILES, grid.tiles.size)
        assertEquals(3, grid.columnCount)
    }
}
