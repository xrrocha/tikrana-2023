package plenix.tikrana.acme.model

typealias LegalId = String

interface Party: Entity {
    val legalId: LegalId
    val legalName: Name
}

typealias BusinessId = LegalId
interface Business : Party {}
