package it.krpng.cassa.data.ods

interface MenuImportCommitter {
    suspend fun commit(plan: MenuImportPlan)
}

sealed class MenuImportCommitException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class MenuImportPlanConflictException(message: String) : MenuImportCommitException(message)

class MenuImportDatabaseException(cause: Throwable) : MenuImportCommitException(
    message = "La scrittura del catalogo non è riuscita.",
    cause = cause,
)

class UnexpectedMenuImportException(cause: Throwable) : MenuImportCommitException(
    message = "Errore imprevisto durante l'importazione.",
    cause = cause,
)
