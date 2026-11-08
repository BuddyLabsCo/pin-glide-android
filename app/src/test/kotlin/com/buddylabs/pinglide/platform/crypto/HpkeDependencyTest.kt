package com.buddylabs.pinglide.platform.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.hpke.HPKE
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class HpkeDependencyTest {
    @Test
    fun selectedPskSuiteExportsMatchingSecretsAndAuthenticatesCiphertext() {
        val hpke =
            HPKE(HPKE.mode_psk, HPKE.kem_P256_SHA256, HPKE.kdf_HKDF_SHA256, HPKE.aead_AES_GCM256)
        val recipient = hpke.generatePrivateKey()
        val info = "dependency-probe-only".toByteArray()
        val psk = ByteArray(32) { it.toByte() }
        val pskId = "probe-psk".toByteArray()
        val sender = hpke.SetupPSKS(recipient.public, info, psk, pskId)
        val receiver = hpke.setupPSKR(sender.encapsulation, recipient, info, psk, pskId)
        assertArrayEquals(sender.export(info, 32), receiver.export(info, 32))
        val aad = "probe-header".toByteArray()
        val plaintext = "synthetic message".toByteArray()
        assertArrayEquals(plaintext, receiver.open(aad, sender.seal(aad, plaintext)))
        val wrongPsk = hpke.setupPSKR(sender.encapsulation, recipient, info, ByteArray(32), pskId)
        assertFalse(sender.export(info, 32).contentEquals(wrongPsk.export(info, 32)))
        assertThrows(InvalidCipherTextException::class.java) {
            wrongPsk.open(aad, sender.seal(aad, plaintext))
        }
    }
}
