package at.asitplus.cidre

/**
 * IP family, either [IpFamily.V4] or [IpFamily.V6]
 */
sealed interface IpFamily {
    val numberOfOctets: Int
    val numberOfBits: Int get() = numberOfOctets * 8
    val segmentSeparator: Char
    val regex: RegexSpec

    abstract class RegexSpec {
        abstract val segment: Regex
        abstract val address: Regex
    }

    companion object Companion {
        val V4: IpAddress.V4.Companion get() = IpAddress.V4.Companion
        val V6: IpAddress.V6.Companion get() = IpAddress.V6.Companion
    }
}