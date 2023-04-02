package com.arashjahani.mappolygonpointsdraw

import com.arashjahani.mappolygonpointsdraw.utils.areaFormat
import com.google.android.gms.common.util.NumberUtils
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NumberUtilsUnitTest {
    @Test
    fun addition_isCorrect() {



        assertEquals("532,178", 532,178L.areaFormat())
        assertEquals("123", 123L.areaFormat())
        assertEquals("0", 0L.areaFormat())
        assertEquals("0", null.areaFormat())
    }
}