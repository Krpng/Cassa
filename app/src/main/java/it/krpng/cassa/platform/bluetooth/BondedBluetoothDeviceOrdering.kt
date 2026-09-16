package it.krpng.cassa.platform.bluetooth

/**
 * Deterministic bonded-device ordering (D-056 Q1).
 *
 * Case-insensitive name compare is locale-independent (does not use the device default
 * locale / default Collator / default-locale lowercase).
 */
object BondedBluetoothDeviceOrdering {
    fun sort(devices: List<BondedBluetoothDevice>): List<BondedBluetoothDevice> =
        devices.sortedWith(COMPARATOR)

    private val COMPARATOR =
        Comparator<BondedBluetoothDevice> { left, right ->
            val leftNamed = isNonBlankName(left.name)
            val rightNamed = isNonBlankName(right.name)
            when {
                leftNamed && !rightNamed -> -1
                !leftNamed && rightNamed -> 1
                leftNamed && rightNamed -> {
                    val byName =
                        String.CASE_INSENSITIVE_ORDER.compare(left.name!!, right.name!!)
                    if (byName != 0) byName else left.id.compareTo(right.id)
                }
                else -> left.id.compareTo(right.id)
            }
        }

    fun isNonBlankName(name: String?): Boolean = !name.isNullOrBlank()
}
