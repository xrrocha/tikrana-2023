package plenix.tikrana.acme.model

class PersonName(val firstName: Name, val middleName: Name?, val paternalSurname: Name, val maternalSurname: Name?) {
    val completeName by lazy { "$firstName $paternalSurname"}
}
typealias PersonId = String
enum class Gender {
    MALE, FEMALE
}
class Person(val id: PersonId, val name: PersonName, val gender: Gender?): Party {
    override val legalId = id
    override val legalName by lazy { name.completeName }
}