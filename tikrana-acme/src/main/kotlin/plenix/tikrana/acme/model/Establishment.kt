package plenix.tikrana.acme.model

class Establishment(val id: BusinessId, val name: Name) : Business {
    override val legalId = id
    override val legalName = name
}
