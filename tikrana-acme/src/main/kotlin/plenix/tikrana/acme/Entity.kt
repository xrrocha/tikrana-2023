package plenix.tikrana.acme

import plenix.tikrana.dynamicproxy.PropertyValueProvider
import java.util.concurrent.atomic.AtomicLong

class Repository<E: Entity> {

    private val instances = mutableMapOf<Long, E>()

    operator fun get(id: Long): E? = instances[id]
    fun findAll(): Sequence<E> = instances.values.asSequence()
}

interface Entity {
    val id: Long

    companion object : PropertyValueProvider<Entity> {
        private val idGenerator = AtomicLong(41L)
        protected fun nextId() = idGenerator.incrementAndGet()
        override fun provideValues() = listOf(Entity::id to nextId())
    }
}
