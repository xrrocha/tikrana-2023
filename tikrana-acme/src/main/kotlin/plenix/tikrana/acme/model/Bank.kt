package plenix.tikrana.acme.model

class Bank(id: OrganizationId, name: Name) : Organization(id, name) {
}

enum class BankAccountType {
    SAVINGS, CHECKING
}

class BankAccount(val owner: Party, val bank: Bank, val type: BankAccountType) : Entity {
}
