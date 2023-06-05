package me.rhunk.snapenhance.data.wrapper.impl.media.dash

import me.rhunk.snapenhance.data.wrapper.AbstractWrapper

class LongformVideoPlaylistItem(obj: Any?) : AbstractWrapper<Any?>(obj) {
    private val chapterList by lazy {
        instanceNonNull().javaClass.declaredFields.first { it.type == List::class.java }
    }
    val chapters: List<SnapChapter>
        get() = (chapterList.get(instanceNonNull()) as List<*>).map { SnapChapter(it) }
}