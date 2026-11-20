package mnstudio.apps.mnstreamx.ui.screen.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChannelColumnsTest {
    @Test fun evenGroupSplitsIntoBalancedColumns() {
        val columns = PlayerChannelColumns(120, twoColumns = true)
        assertEquals(60, columns.firstCount)
        assertEquals(60, columns.secondCount)
        assertFalse(columns.isSecond(59))
        assertTrue(columns.isSecond(60))
        assertEquals(40, columns.localIndex(100))
        assertEquals(75, columns.towardSecond(15))
        assertEquals(15, columns.towardFirst(75))
        assertEquals(15, columns.groupEntry(75))
        assertEquals(76, columns.vertical(75, 1))
        assertNull(columns.vertical(59, 1))
        assertNull(columns.vertical(60, -1))
    }

    @Test fun oddGroupClampsLastFirstColumnRowToExistingSecondRow() {
        val columns = PlayerChannelColumns(5, twoColumns = true)
        assertEquals(3, columns.firstCount)
        assertEquals(2, columns.secondCount)
        assertEquals(4, columns.towardSecond(2))
        assertEquals(1, columns.towardFirst(4))
        assertEquals(2, columns.vertical(1, 1))
        assertNull(columns.vertical(2, 1))
    }

    @Test fun smallOrNarrowGroupsStayInSingleColumn() {
        for (n in 0..2) {
            val one = PlayerChannelColumns(n, twoColumns = false)
            assertEquals(n, one.firstCount)
            assertEquals(0, one.secondCount)
            assertNull(one.towardSecond(0))
        }
        val single = PlayerChannelColumns(1, twoColumns = true)
        assertEquals(1, single.firstCount)
        assertEquals(0, single.secondCount)
        assertEquals(0, single.groupEntry(null))
        assertNull(PlayerChannelColumns(0, true).groupEntry(null))
    }

    @Test fun invalidNavigationCannotEscapeGroupBounds() {
        val columns = PlayerChannelColumns(6, twoColumns = true)
        assertNull(columns.vertical(0, -1))
        assertNull(columns.vertical(5, 1))
        assertNull(columns.vertical(-1, 1))
        assertNull(columns.towardSecond(3))
        assertNull(columns.towardFirst(2))
        assertEquals(0, columns.groupEntry(999))
    }
}