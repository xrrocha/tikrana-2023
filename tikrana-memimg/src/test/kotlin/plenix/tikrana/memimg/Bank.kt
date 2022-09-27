package plenix.tikrana.memimg

import java.math.BigDecimal

typealias Amount = BigDecimal

data class Bank(val accounts: MutableMap<String, Account> = HashMap())

// @JsonTypeInfo(use=JsonTypeInfo.Id.CLASS)
data class Account(val id: String, val name: String) {
    var balance: Amount by TxDelegate(Amount.ZERO) { it >= Amount.ZERO }
}

/* 2) Application mutations: Deposit, Withdrawal, Transfer */
interface BankMutation<R> : Mutation<Bank, R> {
    fun executeOnBank(bank: Bank): R?
    override fun executeOn(system: Bank): R? = executeOnBank(system)
}


interface BankQuery<R> : Query<Bank, R> {
    fun queryBank(bank: Bank): R?
    override fun queryOn(system: Bank): R? = queryBank(system)
}

interface AccountMutation : BankMutation<Unit> {
    val accountId: String
    fun executeOn(account: Account)
    override fun executeOnBank(bank: Bank) {
        executeOn(bank.accounts[accountId]!!)
    }
}

data class CreateAccount(val id: String, val name: String) : BankMutation<Unit> {
    override fun executeOnBank(bank: Bank) {
        bank.accounts[id] = Account(id, name)
    }
}

data class Deposit(override val accountId: String, val amount: Amount) : AccountMutation {
    override fun executeOn(account: Account) {
        account.balance += amount
    }
}

data class Withdrawal(override val accountId: String, val amount: Amount) : AccountMutation {
    override fun executeOn(account: Account) {
        account.balance -= amount
    }
}

data class Transfer(val fromAccountId: String, val toAccountId: String, val amount: Amount) : BankMutation<Unit> {
    override fun executeOnBank(bank: Bank) {
        // Operation order deliberately set so as to exercise rollback...
        Deposit(toAccountId, amount).executeOn(bank)
        Withdrawal(fromAccountId, amount).executeOn(bank)
    }
}
