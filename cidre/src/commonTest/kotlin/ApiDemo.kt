package at.asitplus

import at.asitplus.cidre.IpAddress
import at.asitplus.cidre.IpFamily
import at.asitplus.cidre.IpInterface
import at.asitplus.cidre.IpNetwork
import at.asitplus.cidre.byteops.CidrNumber
import at.asitplus.cidre.byteops.toNetmask
import kotlin.test.Test

class ApiDemo {

    @Test
    fun netAndIfaceOps() {
        val addrAndPrefix = "::dead/42"
        val iface = IpInterface(addrAndPrefix)
        val net = IpNetwork(addrAndPrefix, strict = false) //be lenient and auto-mask
        println("iface: $iface") //::dead/42
        println("net:   $net")   //::/42

        //normalises in-place and associates (not copies) the address with the nwtwork
        val associated = IpNetwork.forAddress(iface.address, iface.prefix)

        println("net:   $associated") //::/42
        println("iface: $associated") //::/42 <-- not the change here!
        println(associated.address === iface.address) //true

        //no normalisation, but copying, so we can be strict!
        val deepCopied = IpNetwork(iface.address, iface.prefix, strict = true)
        println(deepCopied.address == iface.address)  //true
        println(deepCopied.address === iface.address) //false
    }

    @Test
    fun addressOps() {
        //Use qualified constructor to enforce family
        val lower = IpAddress.V4("192.168.0.1")
        val higher = IpAddress.V4("192.168.0.99")

        println("Distance = ${lower - higher}") //null due to underflow
        println("Distance = ${higher - lower}") //00000062 (=98)
        println("Summed = ${lower + CidrNumber.V4(98u)}") //192.168.0.99

        println("Numeric:          ${lower.toCidrNumber()}")   //c0a80001
        var shifted = lower shl 8
        println("Numeric shifted = ${shifted.toCidrNumber()}") //a8000100
        println("Shifted = $shifted") //168.0.1.0 due to truncation

        val maskedBits = higher.mask(24u)
        val maskedCopy = higher and (24u.toNetmask(IpFamily.V4))
        // Masked in-place= 192.168.0.0 (modified bits: 4), manually masked = 192.168.0.0
        println("Masked in-place= $higher (modified bits: $maskedBits), manually masked = $maskedCopy")
    }

    @Test
    fun ranges() {
        //point-to-point -> no broadcast
        val pointToPoint = IpNetwork.V4("192.168.0.0/31")
        println(pointToPoint.address)               //192.0.0.0
        println(pointToPoint.lastAddress)           //192.0.0.1
        println(pointToPoint.firstAssignableHost)   //192.168.0.0/31
        println(pointToPoint.lastAssignableHost)    //192.168.0.1/31
        println(pointToPoint.broadcastAddress)      //null
        println(pointToPoint.size)                  // 00000002 (= 2)

        //perhaps the most used private IP range
        val private = IpNetwork.V4("192.168.0.0/24")
        println(private.address)                //192.168.0.0
        println(private.lastAddress)            //192.168.0.255
        println(private.firstAssignableHost)    //192.168.0.1/24
        println(private.lastAssignableHost)     //192.168.0.254/24
        println(private.broadcastAddress)       //192.168.0.255/24
        println(private.size)                   //00000100 (= 256)

        //maxing out
        val unspec = IpNetwork.V4("0.0.0.0/0")
        println(unspec.address)               //0.0.0.0
        println(unspec.lastAddress)           //255.255.255.255
        println(unspec.firstAssignableHost)   //0.0.0.1/0
        println(unspec.lastAssignableHost)    //255.255.255.254/0
        println(unspec.broadcastAddress)      //255.255.255.255/0
        println(unspec.size)                  //0100000000 (= 2^32; observe the fifth octet required to represent it!)
    }
}