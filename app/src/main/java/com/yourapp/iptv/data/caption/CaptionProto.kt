package com.yourapp.iptv.data.caption

import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile

/** Small bounded protobuf reader for the pinned ONNX graph; tensor weights are never materialized. */
internal object CaptionProto {
    data class Field(val number: Int, val wire: Int, val bytes: ByteArray, val value: ByteArray)
    fun varint(value: Long): ByteArray {
        val out = ByteArrayOutputStream(); var n = value
        while (n and -128L != 0L) { out.write((n.toInt() and 127) or 128); n = n ushr 7 }
        out.write(n.toInt()); return out.toByteArray()
    }
    fun read(file: RandomAccessFile): Long {
        var value = 0L
        for (shift in 0..63 step 7) {
            val b = file.readUnsignedByte()
            require(shift != 63 || b <= 1) { "Invalid graph integer" }
            value = value or ((b and 127).toLong() shl shift)
            if (b < 128) return value
        }
        error("Invalid graph integer")
    }
    fun fields(bytes: ByteArray): List<Field> {
        var at = 0
        fun integer(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                require(at < bytes.size)
                val b = bytes[at++].toInt() and 255
                require(shift != 63 || b <= 1)
                value = value or ((b and 127).toLong() shl shift)
                if (b < 128) return value
            }
            error("Invalid graph integer")
        }
        return buildList {
            while (at < bytes.size) {
                val start = at; val tag = integer(); val wire = (tag and 7).toInt()
                require(tag ushr 3 > 0)
                val size = when (wire) { 0 -> null; 1 -> 8L; 2 -> integer(); 5 -> 4L; else -> error("Invalid graph field") }
                val data = at
                if (size == null) integer() else { require(size in 0..(bytes.size - at).toLong()); at += size.toInt() }
                add(Field((tag ushr 3).toInt(), wire, bytes.copyOfRange(start, at), bytes.copyOfRange(data, at)))
            }
        }
    }
    fun message(number: Int, value: ByteArray) = varint((number.toLong() shl 3) or 2) + varint(value.size.toLong()) + value
    fun string(number: Int, value: String) = message(number, value.toByteArray(Charsets.UTF_8))
    fun integer(number: Int, value: Long) = varint(number.toLong() shl 3) + varint(value)
    fun join(rows: Iterable<ByteArray>): ByteArray = ByteArrayOutputStream().apply { rows.forEach { write(it) } }.toByteArray()
    fun text(rows: List<Field>, number: Int) = rows.filter { it.number == number && it.wire == 2 }
        .map { it.value.toString(Charsets.UTF_8) }
}