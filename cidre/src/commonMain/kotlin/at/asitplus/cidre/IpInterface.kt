package at.asitplus.cidre

import at.asitplus.cidre.byteops.CidrNumber


/**
 * An [address] with a [prefix] belonging to a [network]
 */
sealed class IpInterface<N : Number, S: CidrNumber<S>>
@Throws(IllegalArgumentException::class)
constructor(override val prefix: Prefix, val network: IpNetwork<N, S>) :
    IpAddressAndPrefix<N, S> by network {

    override fun toString(): String = "$address/$prefix"

    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun <N : Number, S: CidrNumber<S>> unsafe(
            network: IpNetwork<N, S>,
            address: IpAddress<N, S>,
            prefix: Prefix
        ): IpInterface<N, S> = when (address) {
            is IpAddress.V4 -> V4(address, prefix, network as IpNetwork.V4) as IpInterface<N, S>
            is IpAddress.V6 -> V6(address, prefix, network as IpNetwork.V6) as IpInterface<N, S>
        }

        @Suppress("UNCHECKED_CAST")
        operator fun <N : Number, S: CidrNumber<S>> invoke(
            address: IpAddress<N, S>,
            prefix: Prefix
        ): IpInterface<N, S> =
            when (address) {
                is IpAddress.V4 -> V4(address, prefix) as IpInterface<N, S>
                is IpAddress.V6 -> V6(address, prefix) as IpInterface<N, S>
            }

        @Throws(IllegalArgumentException::class)
        operator fun invoke(stringRepresentation: String): IpInterface<*, *> {
            val (addr, prefix) = parseIpAndPrefix(stringRepresentation)
            return IpInterface(addr, prefix)
        }
    }

    class V4 internal constructor(override val address: IpAddress.V4, prefix: Prefix, network: IpNetwork.V4) :
        IpInterface<Byte, CidrNumber.V4>(prefix, network) {

        constructor(address: IpAddress.V4, prefix: Prefix) : this(
            address,
            prefix,
            IpNetwork.V4(address, prefix, strict = false)
        )

        companion object {
            @Throws(IllegalArgumentException::class)
            operator fun invoke(stringRepresentation: String): IpInterface.V4  {
                val parsed = IpInterface(stringRepresentation)
                require(parsed is IpInterface.V4) {"Not an IPv4 address, but IPv6: $stringRepresentation"}
                return parsed
            }
        }
    }

    class V6(override val address: IpAddress.V6, prefix: Prefix, network: IpNetwork.V6) :
        IpInterface<Short, CidrNumber.V6>(prefix, network) {

        constructor(address: IpAddress.V6, prefix: Prefix) : this(
            address,
            prefix,
            IpNetwork.V6(address, prefix, strict = false)
        )

        companion object {
            @Throws(IllegalArgumentException::class)
            operator fun invoke(stringRepresentation: String): IpInterface.V6  {
                val parsed = IpInterface(stringRepresentation)
                require(parsed is IpInterface.V6) {"Not an IPv4 address, but IPv6: $stringRepresentation"}
                return parsed
            }
        }

    }

}
