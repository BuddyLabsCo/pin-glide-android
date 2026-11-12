package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun File.markLogout(snapshot: IdentitySnapshot) {
    require(snapshot.generation > 0)
    val pending = File(parentFile, "$name.new")
    FileOutputStream(pending).use { stream ->
        stream.write(snapshot.generation.toString().toByteArray(Charsets.UTF_8))
        stream.fd.sync()
    }
    Files.move(
        pending.toPath(),
        toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
    )
}

internal fun File.appliesTo(snapshot: IdentitySnapshot): Boolean {
    if (!exists()) return false
    val bytes = readBytes()
    val generation = if (bytes.contentEquals(byteArrayOf(1))) {
        Long.MAX_VALUE
    } else {
        bytes.toString(Charsets.UTF_8).toLong().also { require(it > 0) }
    }
    return snapshot.generation <= generation
}
