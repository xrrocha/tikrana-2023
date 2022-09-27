package plenix.tikrana.memimg

import arrow.core.Either
import arrow.core.flatMap
import plenix.tikrana.util.ApplicationFailure
import plenix.tikrana.util.Failure
import plenix.tikrana.util.SystemFailure

interface Mutation<S, R> {
    fun executeOn(system: S): R?
}

interface Query<S, R> {
    fun queryOn(system: S): R?
}

class MemoryImage(private val system: Any, private val eventStorage: EventStorage) {

    init {
        synchronized(system) { eventStorage.replay { mutation: Mutation<Any, Any> -> mutation.executeOn(system) } }
    }

    fun <S, R> executeMutation(mutation: Mutation<S, R>): Either<Failure, R?> =
        TxManager.run {
            @Suppress("UNCHECKED_CAST")
            Either.catch { mutation.executeOn(system as S) }
                .mapLeft { ApplicationFailure("Executing mutation ${mutation::class.qualifiedName}", it) }
                .flatMap { result ->
                    Either.catch { eventStorage.append(mutation) }
                        .mapLeft { SystemFailure("Serializing mutation ${mutation::class.qualifiedName}", it) }
                        .map { result }
                }
        }

    fun <S, R> executeQuery(query: Query<S, R>): Either<Failure, R?> =
        @Suppress("UNCHECKED_CAST")
        Either.catch { query.queryOn(system as S) }
            .mapLeft { ApplicationFailure("Executing query ${query::class.qualifiedName}", it) }
}
