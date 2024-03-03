package ru.reosfire.money.manage.authentication

data class JWTConfiguration(
    val secret: String,
    val audience: String,
    val issuer: String,
) {
    companion object {
        fun byEnvironment() = JWTConfiguration(
            secret = System.getenv("JWT_SECRET"),
            audience = "users", // TODO according to stack overflow: This one should be an address to make other servers be able to ensure that this token is meant for them.
            issuer = "http://0.0.0.0:8080", // TODO this is the address of the token producer. Provided for clients for check if they want to process it etc
        )
    }
}
