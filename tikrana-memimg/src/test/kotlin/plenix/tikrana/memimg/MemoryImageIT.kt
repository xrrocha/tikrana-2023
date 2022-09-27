package plenix.tikrana.memimg

import org.junit.jupiter.api.Test

class MemoryImageIT {

    init {
        initLogger()
    }

    @Test
    fun testTikrana() {

        val bank = Bank()
        val eventStorage = MemoryEventStorage()
        val memoryImage = MemoryImage(bank, eventStorage)

        // memoryImage.executeMutation()
        CreateAccount("janet", "Janet Doe")
        Deposit("janet", Amount(100))
        Withdrawal("janet", Amount(10))
        CreateAccount("john", "John Doe")
        Deposit("john", Amount(50))
        Transfer("janet", "john", Amount(20))

        data class ById(val id: String) : BankQuery<Account> {
            override fun queryBank(bank: Bank): Account? =
                bank.accounts.values.find { it.id == id }
        }

        val queryResult = ById("janet")
    }
}
