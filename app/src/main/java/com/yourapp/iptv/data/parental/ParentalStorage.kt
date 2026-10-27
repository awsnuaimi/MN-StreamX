package com.yourapp.iptv.data.parental

import com.yourapp.iptv.domain.parental.*
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class ParentalRecord(
    val salt: ByteArray, val hash: ByteArray, val rules: ParentalRules,
    val failures: Int = 0, val deadline: Long = 0, val boot: Int = -1, val cooldown: Long = 0
)

internal interface ParentalStorage {
    fun read(): ParentalRecord?
    fun write(record: ParentalRecord?)
}

internal interface ParentalClock {
    fun elapsed(): Long
    fun boot(): Int
}

internal object PinHash {
    fun valid(pin: CharArray) = pin.size == 6 && pin.all { it in '0'..'9' }
    fun salt() = ByteArray(16).also(SecureRandom()::nextBytes)
    fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        require(valid(pin) && salt.size == 16)
        val spec = PBEKeySpec(pin, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    fun matches(pin: CharArray, record: ParentalRecord): Boolean {
        val candidate = derive(pin, record.salt)
        return try { MessageDigest.isEqual(candidate, record.hash) } finally { candidate.fill(0) }
    }
}

internal object ParentalRecordCodec {
    const val LIMIT = 1024 * 1024
    fun encode(record: ParentalRecord): ByteArray {
        record.rules.validate()
        require(record.salt.size == 16 && record.hash.size == 32 && record.failures in 0..10_000)
        require(record.deadline >= 0 && record.cooldown in 0..1_800_000)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeInt(1); data.write(record.salt); data.write(record.hash)
            data.writeInt(record.failures); data.writeLong(record.deadline)
            data.writeInt(record.boot); data.writeLong(record.cooldown)
            data.writeInt(record.rules.sections.size)
            record.rules.sections.sortedBy { it.name }.forEach { data.writeUTF(it.name) }
            data.writeInt(record.rules.groups.size)
            record.rules.groups.toSortedMap().forEach { (source, names) ->
                data.writeUTF(source); data.writeInt(names.size); names.sorted().forEach(data::writeUTF)
            }
        }
        return out.toByteArray().also { require(it.size <= LIMIT) }
    }
    fun decode(bytes: ByteArray): ParentalRecord {
        require(bytes.size <= LIMIT)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == 1)
        val salt = ByteArray(16).also(input::readFully); val hash = ByteArray(32).also(input::readFully)
        val failures = input.readInt(); val deadline = input.readLong(); val boot = input.readInt(); val cooldown = input.readLong()
        val sectionCount = input.readInt().also { require(it in 0..4) }
        val sections = List(sectionCount) { ParentalSection.valueOf(input.readUTF()) }.toSet()
        require(sections.size == sectionCount)
        val sourceCount = input.readInt().also { require(it in 0..16) }
        var total = 0
        val groups = linkedMapOf<String, Set<String>>()
        repeat(sourceCount) {
            val key = input.readUTF(); require(key !in groups)
            val count = input.readInt().also { require(it in 0..512 && total + it <= 512) }; total += count
            groups[key] = List(count) { input.readUTF() }.toSet().also { require(it.size == count) }
        }
        require(input.available() == 0)
        return ParentalRecord(salt,hash,ParentalRules(sections,groups).validate(),failures,deadline,boot,cooldown)
            .also { encode(it).fill(0) }
    }
}