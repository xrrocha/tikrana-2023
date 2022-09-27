package plenix.tikrana.memimg

import arrow.core.getOrElse
import org.junit.jupiter.api.Test
import plenix.tikrana.util.Failure
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryImageIT {

    init {
        initLogger()
    }

    @Test
    fun `Memory Image works`() {

        val bank = Bank()
        val eventStorage = MemoryEventStorage()
        val memoryImage = MemoryImage(bank, eventStorage)

        with(Tester<Bank>(memoryImage)) {

            assert(CreateAccount("janet", "Janet Doe")) {
                bank.accounts["janet"]!!.balance == Amount.ZERO
            }

            assert(Deposit("janet", Amount(100))) {
                bank.accounts["janet"]!!.balance == Amount(100)
            }

            assert(Withdrawal("janet", Amount(10))) {
                bank.accounts["janet"]!!.balance == Amount(90)
            }

            assert(CreateAccount("john", "John Doe")) {
                bank.accounts["john"]!!.balance == Amount.ZERO
            }

            assert(Deposit("john", Amount(50))) {
                bank.accounts["john"]!!.balance == Amount(50)
            }

            test(Transfer("janet", "john", Amount(20))) {
                assertEquals(Amount(70), bank.accounts["janet"]!!.balance)
                assertEquals(Amount(70), bank.accounts["john"]!!.balance)
            }
        }

        data class ById(val id: String) : BankQuery<Account> {
            override fun queryBank(bank: Bank): Account? =
                bank.accounts.values.find { it.id == id }
        }

        val query = ById("janet")
        val queryResult = memoryImage.executeQuery(query)
        assertTrue(queryResult.isRight())
        val queryValue = queryResult.getOrElse { null }
        assertTrue(
            queryValue != null &&
                    queryValue.id == "janet" &&
                    queryValue.balance == Amount(70)
        )
    }

    class Tester<S>(private val memoryImage: MemoryImage) {
        fun <R> assert(mutation: Mutation<S, R>, assertion: (R?) -> Boolean) =
            memoryImage.executeMutation(mutation)
                .tapLeft(Failure::doThrow)
                .tap { result ->
                    if (assertion(result).not())
                        throw IllegalArgumentException("Assertion failed for $mutation")
                }

        fun <R> test(mutation: Mutation<S, R>, assertion: (R?) -> Unit) =
            memoryImage.executeMutation(mutation)
                .tapLeft(Failure::doThrow)
                .tap(assertion)
    }
}
