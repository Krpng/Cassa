package it.krpng.cassa.platform.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BondedBluetoothDeviceOrderingTest {
    @Test
    fun `named devices sort before null and blank names`() {
        val sorted =
            BondedBluetoothDeviceOrdering.sort(
                listOf(
                    BondedBluetoothDevice("03", null),
                    BondedBluetoothDevice("02", "  "),
                    BondedBluetoothDevice("01", "Zebra"),
                    BondedBluetoothDevice("00", ""),
                ),
            )

        assertEquals(
            listOf("01", "00", "02", "03"),
            sorted.map { it.id },
        )
    }

    @Test
    fun `named devices sort case-insensitive ASC then id ASC`() {
        val sorted =
            BondedBluetoothDeviceOrdering.sort(
                listOf(
                    BondedBluetoothDevice("BB:BB:BB:BB:BB:BB", "beta"),
                    BondedBluetoothDevice("AA:AA:AA:AA:AA:AA", "Alpha"),
                    BondedBluetoothDevice("CC:CC:CC:CC:CC:CC", "alpha"),
                ),
            )

        assertEquals(
            listOf("AA:AA:AA:AA:AA:AA", "CC:CC:CC:CC:CC:CC", "BB:BB:BB:BB:BB:BB"),
            sorted.map { it.id },
        )
    }

    @Test
    fun `equal ignore-case names tie-break by id ASC`() {
        val sorted =
            BondedBluetoothDeviceOrdering.sort(
                listOf(
                    BondedBluetoothDevice("22:22:22:22:22:22", "Netum"),
                    BondedBluetoothDevice("11:11:11:11:11:11", "netum"),
                ),
            )

        assertEquals(
            listOf("11:11:11:11:11:11", "22:22:22:22:22:22"),
            sorted.map { it.id },
        )
    }

    @Test
    fun `null and blank names sort by id ASC among themselves`() {
        val sorted =
            BondedBluetoothDeviceOrdering.sort(
                listOf(
                    BondedBluetoothDevice("DD", null),
                    BondedBluetoothDevice("BB", ""),
                    BondedBluetoothDevice("CC", "   "),
                    BondedBluetoothDevice("AA", null),
                ),
            )

        assertEquals(listOf("AA", "BB", "CC", "DD"), sorted.map { it.id })
    }

    @Test
    fun `duplicate display names are retained as separate devices`() {
        val sorted =
            BondedBluetoothDeviceOrdering.sort(
                listOf(
                    BondedBluetoothDevice("02", "Same"),
                    BondedBluetoothDevice("01", "Same"),
                ),
            )

        assertEquals(2, sorted.size)
        assertEquals(listOf("01", "02"), sorted.map { it.id })
    }

    @Test
    fun `ordering is locale-independent under Turkish default locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val sorted =
                BondedBluetoothDeviceOrdering.sort(
                    listOf(
                        BondedBluetoothDevice("02", "I"),
                        BondedBluetoothDevice("01", "i"),
                        BondedBluetoothDevice("03", "İ"),
                    ),
                )
            // Locale-independent case folding treats ASCII I/i as equal → id ASC first,
            // then dotted capital İ (distinct code point under CASE_INSENSITIVE_ORDER).
            assertEquals(listOf("01", "02", "03"), sorted.map { it.id })
            assertTrue(BondedBluetoothDeviceOrdering.isNonBlankName("İ"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
