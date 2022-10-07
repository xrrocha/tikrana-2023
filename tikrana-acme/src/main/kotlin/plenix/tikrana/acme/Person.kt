package plenix.tikrana.acme

import plenix.tikrana.dynamicproxy.new
import java.time.LocalDate
import java.time.temporal.ChronoUnit.YEARS
import kotlin.reflect.KClass

enum class Gender { MALE, FEMALE }

data class PersonName(
    var firstName: String,
    var middleName: String? = null,
    var paternalSurname: String,
    var maternalSurname: String? = null
) {
    override fun toString() = "$paternalSurname, $firstName"
}

interface Person : Entity, Nameable {
    var gender: Gender
    var personName: PersonName
    var birthDate: LocalDate
    override val name: String
        get() = personName.toString()

    fun age() = YEARS.between(birthDate, LocalDate.now()).toInt()

    companion object {
        val roles = mutableMapOf<Person, MutableMap<KClass<out PersonRole>, PersonRole>>()
    }
}

inline fun <reified R : PersonRole> Person.role(): R =
    Person.roles
        .computeIfAbsent(this) { mutableMapOf() }
        .computeIfAbsent(R::class) { new<R>(this@role) } as R

interface PersonRole: Person {}

interface User : PersonRole {
    var userName: Name
}
