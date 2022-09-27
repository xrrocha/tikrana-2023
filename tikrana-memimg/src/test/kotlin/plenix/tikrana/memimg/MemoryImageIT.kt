package plenix.tikrana.memimg

import arrow.core.getOrElse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryImageIT {

    init {
        initLogger()
    }

    @Test
    fun `Memory Image works`() {

        val bank = Bank()
        val eventStorage = InMemoryEventStorage()
        val memoryImage = MemoryImage(bank, eventStorage)

        fun balanceFor(id: String) = bank.accounts[id]!!.balance.toInt()

        with(Tester<Bank>(memoryImage)) {

            verifyAfter(CreateAccount("janet", "Janet Doe")) {
                balanceFor("janet") == 0
            }

            verifyAfter(Deposit("janet", Amount(100))) {
                balanceFor("janet") == 100
            }

            verifyAfter(Withdrawal("janet", Amount(10))) {
                balanceFor("janet") == 90
            }

            verifyAfter(CreateAccount("john", "John Doe")) {
                balanceFor("john") == 0
            }

            verifyAfter(Deposit("john", Amount(50))) {
                balanceFor("john") == 50
            }

            assertAfter(Transfer("janet", "john", Amount(20))) {
                assertEquals(70, balanceFor("janet"))
                assertEquals(70, balanceFor("john"))
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
}
