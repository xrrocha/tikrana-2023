package plenix.tikrana.acme.model

typealias OrganizationId = String
open class Organization(val id: OrganizationId, val name: Name) : Business {
    override val legalId = id
    override val legalName = name
}
