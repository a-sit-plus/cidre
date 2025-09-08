package at.asitplus.cidre

/**
 * An [address] with a [prefix] belonging to a [network]
 */
sealed class IpInterface<N : Number, T : IpAddress<N>>
@Throws(IllegalArgumentException::class)
constructor(override val address: T, override val prefix: Prefix, val network: IpNetwork<N, T>): IpAddressAndPrefix<N,T> by network {

    override fun toString(): String = "$address/$prefix"

    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun <N : Number, T : IpAddress<N>> unsafe(
            network: IpNetwork<N, T>,
            address: T,
            prefix: Prefix
        ): IpInterface<N, T> = when (address) {
            is IpAddress.V4 -> V4(address, prefix, network as IpNetwork.V4) as IpInterface<N, T>
            is IpAddress.V6 -> V6(address, prefix, network as IpNetwork.V6) as IpInterface<N, T>
        }

        @Suppress("UNCHECKED_CAST")
        operator fun <T : Number> invoke(address: IpAddress<T>, prefix: Prefix): IpInterface<T, IpAddress<T>> =
            when (address) {
                is IpAddress.V4 -> V4(address, prefix) as IpInterface<T, IpAddress<T>>
                is IpAddress.V6 -> V6(address, prefix) as IpInterface<T, IpAddress<T>>
            }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(stringRepresentation: String): IpInterface<*, *> {
            val (addr, prefix) = parseIpAndPrefix(stringRepresentation)
            return IpInterface(addr, prefix)
        }
    }

    class V4 internal constructor(address: IpAddress.V4, prefix: Prefix, network: IpNetwork.V4) :
        IpInterface<Byte, IpAddress.V4>(address, prefix, network), IpAddressAndPrefix.V4 by network{

        constructor(address: IpAddress.V4, prefix: Prefix) : this(
            address,
            prefix,
            IpNetwork.V4(address, prefix, strict = false)
        )
    }

    class V6(address: IpAddress.V6, prefix: Prefix, network: IpNetwork.V6) :
        IpInterface<Short, IpAddress.V6>(address, prefix, network), IpAddressAndPrefix.V6 by network{

        constructor(address: IpAddress.V6, prefix: Prefix) : this(
            address,
            prefix,
            IpNetwork.V6(address, prefix, strict = false)
        )

    }

}
