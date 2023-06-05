package me.rhunk.snapenhance.data.wrapper.impl.media

import android.os.Parcelable
import me.rhunk.snapenhance.data.wrapper.AbstractWrapper
import me.rhunk.snapenhance.util.getObjectField
import java.lang.reflect.Field


class MediaInfo(obj: Any?) : AbstractWrapper<Any?>(obj) {
    val uri: String
        get() {
            val firstStringUriField = instanceNonNull().javaClass.fields.first { f: Field -> f.type == String::class.java }
            return instanceNonNull().getObjectField(firstStringUriField.name) as String
        }

    init {
        instance?.let {
            if (it is List<*>) {
                if (it.size == 0) {
                    throw RuntimeException("MediaInfo is empty")
                }
                instance = it[0]!!
            }
        }
    }

    val encryption: EncryptionWrapper?
        get() {
            val encryptionAlgorithmField = instanceNonNull().javaClass.fields.first { f: Field ->
                f.type.isInterface && Parcelable::class.java.isAssignableFrom(f.type)
            }
            return encryptionAlgorithmField[instance]?.let { EncryptionWrapper(it) }
        }
}
