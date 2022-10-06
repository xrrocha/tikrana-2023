package plenix.tikrana.acme

import plenix.tikrana.dynamicproxy.PropertyValueProvider
import java.util.concurrent.atomic.AtomicLong

interface Entity {
    val id: Long

    companion object : PropertyValueProvider<Entity> {
        private val idGenerator = AtomicLong(41L)
        protected fun nextId() = idGenerator.incrementAndGet()
        override fun provideValues() = listOf(Entity::id to nextId())
    }
}
