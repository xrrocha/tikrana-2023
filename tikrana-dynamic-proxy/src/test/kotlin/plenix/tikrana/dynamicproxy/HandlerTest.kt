package plenix.tikrana.dynamicproxy

import org.junit.jupiter.api.Test
import plenix.tikrana.dynamicproxy.Model.Gender.FEMALE
import plenix.tikrana.dynamicproxy.Model.Person
import plenix.tikrana.dynamicproxy.Model.PersonName
import java.time.LocalDate
import java.time.temporal.ChronoUnit.YEARS
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("unused")
object Model {
    interface Entity {
        val id: Long

        companion object : PropertyValueProvider<Entity> {
            private val idGenerator = AtomicLong(41L)
            protected fun nextId() = idGenerator.incrementAndGet()
            override fun provideValues() = listOf(Entity::id to nextId())
        }
    }

    interface Nameable {
        val name: String
    }

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
    }
}

class HandlerTest {
    @Test
    fun `Handler populates interface properties completely and correctly`() {

        val janet = new<Person> {
            gender = FEMALE
            personName = PersonName(firstName = "Janet", paternalSurname = "Doe")
            birthDate = LocalDate.of(1970, 1, 1)
        }

        assertEquals(42L, janet.id)
        assertEquals(FEMALE, janet.gender)
        assertEquals("Doe, Janet", janet.name)
        assertTrue(janet.age() >= 52)
    }
}
