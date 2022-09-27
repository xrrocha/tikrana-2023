package plenix.tikrana.memimg

import plenix.tikrana.util.Failure

class Tester<S>(private val memoryImage: MemoryImage) {
    fun <R> verifyAfter(mutation: Mutation<S, R>, assertion: (R?) -> Boolean) =
        memoryImage.executeMutation(mutation)
            .tapLeft(Failure::doThrow)
            .tap { result ->
                if (assertion(result).not())
                    throw IllegalArgumentException("Assertion failed for $mutation")
            }

    fun <R> assertAfter(mutation: Mutation<S, R>, assertion: (R?) -> Unit) =
        memoryImage.executeMutation(mutation)
            .tapLeft(Failure::doThrow)
            .tap(assertion)
}