package plenix.tikrana.memimg

import plenix.tikrana.memimg.EventStorage

class MemoryEventStorage : EventStorage {
    internal val buffer = mutableListOf<Any>()
    override fun <E> replay(eventConsumer: (E) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        buffer.forEach { eventConsumer(it as E) }
    }

    override fun append(event: Any) {
        buffer += event
    }
}