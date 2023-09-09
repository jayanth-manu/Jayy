package me.rhunk.snapenhance.data.wrapper.impl

import me.rhunk.snapenhance.SnapEnhance
import me.rhunk.snapenhance.core.util.ktx.getObjectField
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import java.nio.ByteBuffer
import java.util.UUID

class SnapUUID(obj: Any?) : AbstractWrapper(obj) {
    private val uuidString by lazy { toUUID().toString() }

    private val bytes: ByteArray get() = instanceNonNull().getObjectField("mId") as ByteArray

    private fun toUUID(): UUID {
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }

    override fun toString(): String {
        return uuidString
    }

    override fun equals(other: Any?): Boolean {
        return other is SnapUUID && other.uuidString == uuidString
    }

    companion object {
        fun fromString(uuid: String): SnapUUID {
            return fromUUID(UUID.fromString(uuid))
        }
        fun fromBytes(bytes: ByteArray): SnapUUID {
            val constructor = SnapEnhance.classCache.snapUUID.getConstructor(ByteArray::class.java)
            return SnapUUID(constructor.newInstance(bytes))
        }
        fun fromUUID(uuid: UUID): SnapUUID {
            val buffer = ByteBuffer.allocate(16)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
            return fromBytes(buffer.array())
        }
    }
}
