package at.asitplus.cidre

import at.asitplus.cidre.byteops.CidrNumber
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun IpAddress<*, *>.isSameFamily(other: IpAddress<*, *>): Boolean = family == other.family

fun IpAddressAndPrefix<*, *>.isSameFamily(other: IpAddressAndPrefix<*, *>): Boolean = family == other.family

inline fun <R> IpAddress<*, *>.withSameFamily(
    other: IpAddress<*, *>,
    block: SameFamilyAddressScope.() -> R
): R? = when {
    this is IpAddress.V4 && other is IpAddress.V4 -> SameFamilyAddressScope.V4(this, other).block()
    this is IpAddress.V6 && other is IpAddress.V6 -> SameFamilyAddressScope.V6(this, other).block()
    else -> null
}

inline fun <R> IpNetwork<*, *>.withSameFamily(
    other: IpNetwork<*, *>,
    block: SameFamilyNetworkScope.() -> R
): R? = if (isSameFamily(other)) SameFamilyNetworkScope(this, other).block() else null

inline fun <R> IpNetwork<*, *>.withSameFamily(
    address: IpAddress<*, *>,
    block: SameFamilyNetworkAddressScope.() -> R
): R? = when {
    this is IpNetwork.V4 && address is IpAddress.V4 -> SameFamilyNetworkAddressScope.V4(this, address).block()
    this is IpNetwork.V6 && address is IpAddress.V6 -> SameFamilyNetworkAddressScope.V6(this, address).block()
    else -> null
}

inline fun <R> IpAddress<*, *>.withSameFamily(
    network: IpNetwork<*, *>,
    block: SameFamilyNetworkAddressScope.() -> R
): R? = when {
    this is IpAddress.V4 && network is IpNetwork.V4 -> SameFamilyNetworkAddressScope.V4(network, this).block()
    this is IpAddress.V6 && network is IpNetwork.V6 -> SameFamilyNetworkAddressScope.V6(network, this).block()
    else -> null
}

sealed class SameFamilyAddressScope {
    abstract val left: IpAddress<*, *>
    abstract val right: IpAddress<*, *>
    val family: IpFamily get() = left.family

    class V4(
        override val left: IpAddress.V4,
        override val right: IpAddress.V4
    ) : SameFamilyAddressScope()

    class V6(
        override val left: IpAddress.V6,
        override val right: IpAddress.V6
    ) : SameFamilyAddressScope()

    @OptIn(ExperimentalContracts::class)
    fun isV4(): Boolean {
        contract {
            returns(true) implies (this@SameFamilyAddressScope is V4)
            returns(false) implies (this@SameFamilyAddressScope is V6)
        }
        return this is V4
    }

    @OptIn(ExperimentalContracts::class)
    fun isV6(): Boolean {
        contract {
            returns(true) implies (this@SameFamilyAddressScope is V6)
            returns(false) implies (this@SameFamilyAddressScope is V4)
        }
        return this is V6
    }

    inline fun <R> whenFamily(
        v4: V4.() -> R,
        v6: V6.() -> R
    ): R = when (this) {
        is V4 -> v4()
        is V6 -> v6()
    }

    fun compare(): Int = left.compareTo(right)

    infix fun IpAddress<*, *>.compare(other: IpAddress<*, *>): Int = compareTo(other)

    operator fun IpAddress<*, *>.compareTo(other: IpAddress<*, *>): Int = whenSameFamily(
        other,
        v4 = { a, b -> a.compareTo(b) },
        v6 = { a, b -> a.compareTo(b) }
    )

    operator fun IpAddress<*, *>.minus(other: IpAddress<*, *>): CidrNumber<*>? = whenSameFamily(
        other,
        v4 = { a, b -> a - b },
        v6 = { a, b -> a - b }
    )

    operator fun IpAddress<*, *>.plus(other: IpAddress<*, *>): CidrNumber<*>? = whenSameFamily(
        other,
        v4 = { a, b -> a + b },
        v6 = { a, b -> a + b }
    )

    infix fun IpAddress<*, *>.and(other: IpAddress<*, *>): IpAddress<*, *> = whenSameFamily(
        other,
        v4 = { a, b -> a and b },
        v6 = { a, b -> a and b }
    )

    infix fun IpAddress<*, *>.or(other: IpAddress<*, *>): IpAddress<*, *> = whenSameFamily(
        other,
        v4 = { a, b -> a or b },
        v6 = { a, b -> a or b }
    )

    infix fun IpAddress<*, *>.xor(other: IpAddress<*, *>): IpAddress<*, *> = whenSameFamily(
        other,
        v4 = { a, b -> a xor b },
        v6 = { a, b -> a xor b }
    )

    private inline fun <R> IpAddress<*, *>.whenSameFamily(
        other: IpAddress<*, *>,
        v4: (IpAddress.V4, IpAddress.V4) -> R,
        v6: (IpAddress.V6, IpAddress.V6) -> R
    ): R = when {
        this is IpAddress.V4 && other is IpAddress.V4 -> v4(this, other)
        this is IpAddress.V6 && other is IpAddress.V6 -> v6(this, other)
        else -> wrongFamily(other)
    }

    private fun wrongFamily(other: IpAddress<*, *>): Nothing =
        throw IllegalArgumentException("IP address families differ: $family != ${other.family}")
}

class SameFamilyNetworkScope(
    val left: IpNetwork<*, *>,
    val right: IpNetwork<*, *>
) {
    val family: IpFamily = left.family

    init {
        require(left.isSameFamily(right)) { "IP network families differ: ${left.family} != ${right.family}" }
    }

    fun compare(): Int = left.compareTo(right)

    infix fun IpNetwork<*, *>.compare(other: IpNetwork<*, *>): Int = compareTo(other)

    operator fun IpNetwork<*, *>.compareTo(other: IpNetwork<*, *>): Int = whenSameFamily(
        other,
        v4 = { a, b -> a.compareTo(b) },
        v6 = { a, b -> a.compareTo(b) }
    )

    operator fun IpNetwork<*, *>.contains(address: IpAddress<*, *>): Boolean = whenSameFamily(
        address,
        v4 = { network, addr -> network.contains(addr) },
        v6 = { network, addr -> network.contains(addr) }
    )

    operator fun IpNetwork<*, *>.contains(ipInterface: IpInterface<*, *>): Boolean = whenSameFamily(
        ipInterface,
        v4 = { network, iface -> network.contains(iface) },
        v6 = { network, iface -> network.contains(iface) }
    )

    operator fun IpNetwork<*, *>.contains(network: IpNetwork<*, *>): Boolean = whenSameFamily(
        network,
        v4 = { a, b -> a.contains(b) },
        v6 = { a, b -> a.contains(b) }
    )

    infix fun IpNetwork<*, *>.isSubnetOf(other: IpNetwork<*, *>): Boolean = whenSameFamily(
        other,
        v4 = { a, b -> a.isSubnetOf(b) },
        v6 = { a, b -> a.isSubnetOf(b) }
    )

    infix fun IpNetwork<*, *>.isSupernetOf(other: IpNetwork<*, *>): Boolean = whenSameFamily(
        other,
        v4 = { a, b -> a.isSupernetOf(b) },
        v6 = { a, b -> a.isSupernetOf(b) }
    )

    infix fun IpNetwork<*, *>.overlaps(other: IpNetwork<*, *>): Boolean = whenSameFamily(
        other,
        v4 = { a, b -> a.overlaps(b) },
        v6 = { a, b -> a.overlaps(b) }
    )

    infix fun IpNetwork<*, *>.isAdjacentTo(other: IpNetwork<*, *>): Boolean = whenSameFamily(
        other,
        v4 = { a, b -> a.isAdjacentTo(b) },
        v6 = { a, b -> a.isAdjacentTo(b) }
    )

    infix fun IpNetwork<*, *>.canMergeWith(other: IpNetwork<*, *>): Boolean = whenSameFamily(
        other,
        v4 = { a, b -> a.canMergeWith(b) },
        v6 = { a, b -> a.canMergeWith(b) }
    )

    operator fun IpNetwork<*, *>.plus(other: IpNetwork<*, *>): IpNetwork<*, *>? = whenSameFamily(
        other,
        v4 = { a, b -> a + b },
        v6 = { a, b -> a + b }
    )

    private inline fun <R> IpNetwork<*, *>.whenSameFamily(
        other: IpNetwork<*, *>,
        v4: (IpNetwork.V4, IpNetwork.V4) -> R,
        v6: (IpNetwork.V6, IpNetwork.V6) -> R
    ): R = when {
        this is IpNetwork.V4 && other is IpNetwork.V4 -> v4(this, other)
        this is IpNetwork.V6 && other is IpNetwork.V6 -> v6(this, other)
        else -> wrongFamily(other)
    }

    private inline fun <R> IpNetwork<*, *>.whenSameFamily(
        address: IpAddress<*, *>,
        v4: (IpNetwork.V4, IpAddress.V4) -> R,
        v6: (IpNetwork.V6, IpAddress.V6) -> R
    ): R = when {
        this is IpNetwork.V4 && address is IpAddress.V4 -> v4(this, address)
        this is IpNetwork.V6 && address is IpAddress.V6 -> v6(this, address)
        else -> wrongFamily(address)
    }

    private inline fun <R> IpNetwork<*, *>.whenSameFamily(
        ipInterface: IpInterface<*, *>,
        v4: (IpNetwork.V4, IpInterface.V4) -> R,
        v6: (IpNetwork.V6, IpInterface.V6) -> R
    ): R = when {
        this is IpNetwork.V4 && ipInterface is IpInterface.V4 -> v4(this, ipInterface)
        this is IpNetwork.V6 && ipInterface is IpInterface.V6 -> v6(this, ipInterface)
        else -> wrongFamily(ipInterface)
    }

    private fun wrongFamily(other: IpAddressAndPrefix<*, *>): Nothing =
        throw IllegalArgumentException("IP network families differ: $family != ${other.family}")

    private fun wrongFamily(other: IpAddress<*, *>): Nothing =
        throw IllegalArgumentException("IP network/address families differ: $family != ${other.family}")
}

sealed class SameFamilyNetworkAddressScope {
    abstract val network: IpNetwork<*, *>
    abstract val address: IpAddress<*, *>
    val family: IpFamily get() = network.family

    class V4(
        override val network: IpNetwork.V4,
        override val address: IpAddress.V4
    ) : SameFamilyNetworkAddressScope()

    class V6(
        override val network: IpNetwork.V6,
        override val address: IpAddress.V6
    ) : SameFamilyNetworkAddressScope()

    inline fun <R> whenFamily(
        v4: V4.() -> R,
        v6: V6.() -> R
    ): R = when (this) {
        is V4 -> v4()
        is V6 -> v6()
    }

    operator fun IpNetwork<*, *>.contains(address: IpAddress<*, *>): Boolean = whenSameFamily(
        address,
        v4 = { network, addr -> network.contains(addr) },
        v6 = { network, addr -> network.contains(addr) }
    )

    fun contains(): Boolean = address in network

    fun isInNetwork(): Boolean = contains()

    private inline fun <R> IpNetwork<*, *>.whenSameFamily(
        address: IpAddress<*, *>,
        v4: (IpNetwork.V4, IpAddress.V4) -> R,
        v6: (IpNetwork.V6, IpAddress.V6) -> R
    ): R = when {
        this is IpNetwork.V4 && address is IpAddress.V4 -> v4(this, address)
        this is IpNetwork.V6 && address is IpAddress.V6 -> v6(this, address)
        else -> throw IllegalArgumentException("IP network/address families differ: ${this.family} != ${address.family}")
    }
}
