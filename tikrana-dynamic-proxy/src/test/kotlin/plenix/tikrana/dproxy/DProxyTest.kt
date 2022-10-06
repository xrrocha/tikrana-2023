package plenix.tikrana.dproxy

import org.junit.jupiter.api.Test
import plenix.tikrana.dproxy.DProxy.new
import plenix.tikrana.dproxy.Model.Gender.FEMALE
import plenix.tikrana.dproxy.Model.Person
import plenix.tikrana.dproxy.Model.PersonName
import java.time.LocalDate
import java.time.temporal.ChronoUnit.YEARS
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("unused")
object Model {
    interface Entity {
        val id: Long

        companion object : ValueProvider<Entity> {
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
        fun show() = "$paternalSurname, $firstName"
    }

    interface Person : Entity, Nameable {
        var gender: Gender
        var personName: PersonName
        var birthDate: LocalDate
        override val name: String
            get() = personName.show()

        fun age() = YEARS.between(birthDate, LocalDate.now()).toInt()
    }

    interface User : Person { // TODO As Person role
        var userName: String
        var passwordHash: ByteArray
        var primaryEmail: String
    }

    operator fun <T : Entity> T.invoke(block: T.() -> Unit) = block(this)
}

class DProxyTest {
    @Test
    fun `DProxy creates interface instance properly`() {
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
