package plenix.tikrana.acme

import org.junit.jupiter.api.Test
import plenix.tikrana.acme.Gender.FEMALE
import plenix.tikrana.dynamicproxy.new
import java.time.LocalDate
import kotlin.test.assertEquals

class CreateUserScenario {

    @Test
    fun doit() {
        val sandra = new<Person> {
            gender = FEMALE
            personName = PersonName(firstName = "Sandra", paternalSurname = "Ortiz")
            birthDate = LocalDate.of(1965, 8, 7)
        }
        val usuarioSandra = sandra.setRole<User> {
            userName = "scom"
        }
        assertEquals("scom", usuarioSandra.userName)
        assertEquals("Ortiz, Sandra", usuarioSandra.name)
    }
}