package com.babycatbe.nevesestoque.feature.alerts

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertCountsTest {
    @Test
    fun totalsFollowWebRule() {
        val counts = AlertCounts(trash = 2, pendingSuppliers = 1, pendingProducts = 3)
        assertEquals(4, counts.pendingTotal)
        assertEquals(6, counts.total)
        assertEquals(0, AlertCounts(0, 0, 0).total)
    }

    @Test
    fun pendingLabelPluralization() {
        assertEquals("0 pendências", alertPendingLabel(0))
        assertEquals("1 pendência", alertPendingLabel(1))
        assertEquals("4 pendências", alertPendingLabel(4))
    }
}
