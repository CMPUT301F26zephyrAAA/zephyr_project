package com.example.zephyrcampsites
/***
 * Validates personal information entered by an enterant for US 01.07.03 ***/

class ProfileValidator {
    /**
     * Checks whether name, email, phone number are valid
     *
     * @param name Entrants name
     * @param email Entrant's email address
     * @param phoneNumber Entrant's phone number
     * @return Error message if invalid, otherwise null.
     */
    fun validate(name: String, email: String, phoneNumber: String): String? {
        if (name.isBlank()) {
            return "Name cannot be blank"
        }
        //CI-01 https://www.geeksforgeeks.org/kotlin/kotlin-regular-expression/
        if (!email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            return "Invalid email address"
        }

        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.length !in 10..15 ||
            phoneNumber.any {
                !it.isDigit() && it != ' ' && it != '+' && it != '-' && it != '(' && it != ')'
            }
        ) {
            return "Invalid phone number"
        }
        return null
    }
}