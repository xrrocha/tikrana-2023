package plenix.tikrana.acme.model

import java.util.concurrent.atomic.AtomicLong

interface Entity {
    companion object {
        private val idGenerator = AtomicLong(0L)
    }

    fun nextId() = idGenerator.getAndIncrement()
}
