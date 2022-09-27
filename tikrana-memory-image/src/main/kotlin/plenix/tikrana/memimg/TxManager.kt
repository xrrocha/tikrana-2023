package plenix.tikrana.memimg

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import plenix.tikrana.util.Failure
import plenix.tikrana.util.SystemFailure
import kotlin.reflect.KProperty

object TxManager {

    private val journal = ThreadLocal<MutableMap<Pair<Any, String>, () -> Unit>>().apply {
        set(mutableMapOf())
    }

    fun <R> run(action: () -> Either<Failure, R>): Either<Failure, R> =
        synchronized(this) {
            begin()
            try {
                action()
            } catch (t: Throwable) {
                SystemFailure("Managing transaction: $t", t).left()
            }
        }
            .tapLeft {
                rollback()
            }

    fun <T> remember(who: Any, what: String, value: T, undo: (T) -> Unit) {
        journal.get().computeIfAbsent(Pair(who, what)) { { undo(value) } }
    }

    private fun begin() = journal.get().clear()

    private fun rollback() =
        journal.get().forEach { (whoWhat, undo) ->
            try {
                undo.invoke()
            } catch (t: Throwable) {
                val (who, what) = whoWhat
                throw IllegalStateException("Error retracting ${who::class.simpleName}.$what: $t", t)
            }
        }
}

class TxDelegate<T>(initialValue: T, private val validator: Validator<T>? = null) {
    private var value: T
    private val setter: (T) -> Unit = { value -> this.value = value }

    constructor(initialValue: T, validation: (T) -> Boolean) : this(initialValue) {
        when {
            value == null || validation(value) -> Unit.right()
            else -> "Invalid value: $value".left()
        }
    }

    init {
        validator?.validate(initialValue)?.getOrHandle { throw IllegalArgumentException(it) }
        value = initialValue
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = value

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        validator?.validate(value)?.getOrHandle { throw IllegalArgumentException("${property.name}: $it") }
        // TODO Support undoing operations on collection and map properties for TxManager.remember
        TxManager.remember(thisRef, property.name, this.value, setter)
        setter(value)
    }
}
